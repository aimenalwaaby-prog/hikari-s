package com.hikari.app.zangetsu

import android.content.Context
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import com.hikari.app.data.Logs
import com.hikari.app.net.CloudflareVerifier
import com.hikari.app.net.DohDns
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs Zangetsu providers (single self-contained `.js` scrapers speaking
 * `getHome` / `popular` / `search` / `getDetail` / `getEpisodes` /
 * `getVideoSources` over an injected promise-style `fetch`) inside a fresh
 * embedded QuickJS engine per call — one engine, one call, then the VM is
 * torn down so nothing leaks between providers.
 *
 * The surface mirrors what the Zangetsu app installs: `fetch(url, {method,
 * headers, body})` resolving `{body, status, headers}`, `__SOURCE_ID`,
 * `__settings`, `console` and `setTimeout`. Fetches run on a pool and resolve
 * concurrently (providers `Promise.all` whole rows), timers are pumped
 * between evaluate rounds, and the call answers through `__zDone`.
 */
object ZangetsuRuntime {

    private const val MAX_CONCURRENT = 8
    private const val TV_CONCURRENT = 2
    private const val FETCH_THREADS = 24
    private const val CALL_TIMEOUT_MS = 45_000L
    private const val CATALOG_TIMEOUT_MS = 50_000L
    private const val VALIDATE_TIMEOUT_MS = 20_000L
    private const val CALL_GRACE_MS = 20_000L
    private const val TIMER_MAX_WAIT_MS = 1_500L
    private const val ENGINE_MEMORY_LIMIT = 256L * 1024 * 1024

    private val concurrency = Semaphore(MAX_CONCURRENT)
    private val tvConcurrency = Semaphore(TV_CONCURRENT)
    private val gate: Semaphore
        get() = if (com.hikari.app.data.PerfMode.tvDevice) tvConcurrency else concurrency

    private val fetchExecutor: ExecutorService = Executors.newFixedThreadPool(FETCH_THREADS)

    private val client by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain -> CloudflareVerifier.intercept(chain) }
            .dns(DohDns)
            .build()
    }

    private class AsyncFetches {
        val results = LinkedBlockingQueue<Pair<String, String>>()
        val pending = AtomicInteger(0)
    }

    private fun quote(s: String): String = JSONObject.quote(s)

    private fun bootJs(sourceId: String, settingsJson: String): String = """
        var __SOURCE_ID = ${quote(sourceId)};
        var __settings = $settingsJson;
        var console = { log: function(m){ __zLog(String(m)); }, warn: function(m){ __zLog('warn: '+String(m)); }, error: function(m){ __zLog('error: '+String(m)); } };
        var __zFetchSeq = 0, __zFetchResolvers = {};
        function fetch(url, opts) {
          opts = opts || {};
          return new Promise(function(resolve, reject) {
            var id = 'f' + (++__zFetchSeq);
            __zFetchResolvers[id] = { resolve: resolve, reject: reject };
            var headers = {};
            try { headers = opts.headers || {}; } catch (e) {}
            var body = '';
            try { if (typeof opts.body === 'string') body = opts.body; } catch (e) {}
            __zFetchAsync(String(url), String(opts.method || 'GET'), JSON.stringify(headers), body, id);
          });
        }
        globalThis.__zResolveFetch = function(id, json) {
          var r = __zFetchResolvers[id];
          if (!r) return 1;
          delete __zFetchResolvers[id];
          var o = null;
          try { o = JSON.parse(json); } catch (e) { r.reject(new Error('bad fetch payload')); return 1; }
          if (o && o.ok) r.resolve({ body: o.body, status: o.status, headers: o.headers || {}, url: o.url || '' });
          else r.reject(new Error((o && o.error) || 'fetch failed'));
          return 1;
        };
        var __zTimers = [], __zTimerId = 0;
        function setTimeout(fn, ms) {
          var id = ++__zTimerId;
          __zTimers.push({ id: id, at: Date.now() + Math.max(0, ms || 0), fn: fn });
          return id;
        }
        function clearTimeout(id) {
          __zTimers = __zTimers.filter(function(t) { return t.id !== id; });
        }
        globalThis.__zFireTimer = function() {
          if (!__zTimers.length) return -1;
          var now = Date.now(), best = null, wait = -1, i, t;
          for (i = 0; i < __zTimers.length; i++) {
            t = __zTimers[i];
            if (t.at <= now && (!best || t.at < best.at)) best = t;
            var w = t.at - now;
            if (wait < 0 || w < wait) wait = w;
          }
          if (best) {
            __zTimers = __zTimers.filter(function(x) { return x !== best; });
            try { best.fn(); } catch (e) {}
            return 0;
          }
          return wait;
        };
        globalThis.__zCall = function(fn, argsJson) {
          var args = [];
          try { args = JSON.parse(argsJson); } catch (e) {
            __zDone(JSON.stringify({ ok: false, error: 'bad args' }));
            return;
          }
          var f = globalThis[fn];
          if (typeof f !== 'function') {
            __zDone(JSON.stringify({ ok: false, error: 'no such function: ' + fn }));
            return;
          }
          var done = function(v) {
            var s;
            try { s = JSON.stringify({ ok: true, data: (typeof v === 'undefined' ? null : v) }); }
            catch (e) { s = JSON.stringify({ ok: false, error: 'unserializable result' }); }
            __zDone(s);
          };
          var fail = function(e) {
            var m = 'failed';
            try { m = String((e && e.message) || e); } catch (x) {}
            __zDone(JSON.stringify({ ok: false, error: m }));
          };
          try {
            var r = f.apply(null, args);
            if (r && typeof r.then === 'function') r.then(done, fail);
            else done(r);
          } catch (e) { fail(e); }
        };
    """.trimIndent()

    private suspend fun QuickJs.evaluateVoid(js: String, name: String) {
        evaluate<Any?>("$js\n;void 0;\n", name, false)
    }

    private fun fetchOnce(url: String, method: String, headersJson: String, body: String): String {
        val out = JSONObject()
        try {
            val builder = Request.Builder().url(url)
            val headers = runCatching { JSONObject(headersJson) }.getOrNull() ?: JSONObject()
            val names = mutableListOf<String>()
            headers.keys().forEach { k -> names.add(k) }
            var contentType: String? = null
            for (k in names) {
                val v = headers.optString(k)
                if (k.equals("content-type", ignoreCase = true)) contentType = v
                if (k.isNotBlank()) builder.header(k, v)
            }
            if (builder.build().header("User-Agent") == null) {
                builder.header("User-Agent", com.hikari.app.net.Http.UA)
            }
            val m = method.uppercase()
            if (m == "GET") builder.get()
            else {
                val mt = (contentType ?: "application/x-www-form-urlencoded").toMediaTypeOrNull()
                builder.method(m, body.toByteArray(Charsets.UTF_8).toRequestBody(mt))
            }
            client.newCall(builder.build()).execute().use { res ->
                val bytes = res.body?.bytes() ?: ByteArray(0)
                val hs = JSONObject()
                val multi = LinkedHashMap<String, MutableList<String>>()
                for (i in 0 until res.headers.size) {
                    val n = res.headers.name(i)
                    multi.getOrPut(n.lowercase()) { ArrayList() }.add(res.headers.value(i))
                    if (!hs.has(n)) hs.put(n, res.headers.value(i))
                }
                for ((n, vs) in multi) {
                    if (!hs.has(n)) hs.put(n, vs.joinToString("; "))
                    hs.put(n.lowercase(), vs.joinToString("; "))
                }
                // Best-effort text: providers only ever read text/JSON.
                val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault("")
                out.put("ok", res.isSuccessful)
                out.put("status", res.code)
                out.put("headers", hs)
                out.put("body", text)
                out.put("url", res.request.url.toString())
                if (!res.isSuccessful) out.put("error", "HTTP " + res.code)
            }
        } catch (e: Throwable) {
            out.put("ok", false)
            out.put("status", 0)
            out.put("headers", JSONObject())
            out.put("body", "")
            out.put("url", url)
            out.put("error", e.message ?: "network error")
        }
        return out.toString()
    }

    private suspend fun createEngine(
        deferred: CompletableDeferred<String>,
        sourceId: String,
        settingsJson: String,
        source: String,
        fetches: AsyncFetches,
        budgetMs: Long,
    ): QuickJs {
        val qjs = QuickJs.create(jobDispatcher = Dispatchers.Default)
        qjs.evaluationTimeoutMillis = budgetMs
        runCatching { qjs.memoryLimit = ENGINE_MEMORY_LIMIT }
        com.hikari.app.data.PerfMode.tvEngineMemoryLimit?.let { runCatching { qjs.memoryLimit = it } }
        qjs.function("__zFetchAsync") { args ->
            val url = args.getOrNull(0)?.toString() ?: ""
            val method = (args.getOrNull(1)?.toString() ?: "GET").uppercase()
            val headersJson = args.getOrNull(2)?.toString() ?: "{}"
            val body = args.getOrNull(3)?.toString() ?: ""
            val promiseId = args.getOrNull(4)?.toString().orEmpty()
            if (promiseId.isNotEmpty()) {
                fetches.pending.incrementAndGet()
                fetchExecutor.submit {
                    val json = fetchOnce(url, method, headersJson, body)
                    fetches.results.add(promiseId to json)
                    fetches.pending.decrementAndGet()
                }
            }
            ""
        }
        qjs.function("__zDone") { args ->
            deferred.complete(args.getOrNull(0)?.toString() ?: "")
            ""
        }
        qjs.function("__zLog") { args ->
            android.util.Log.d("Zangetsu[$sourceId]", args.getOrNull(0)?.toString() ?: "")
            ""
        }
        qjs.evaluateVoid(bootJs(sourceId, settingsJson), "zangetsu-boot.js")
        qjs.evaluateVoid("var __SOURCE_ID = ${quote(sourceId)};", "source-id.js")
        qjs.evaluateVoid(source, "$sourceId.js")
        return qjs
    }

    private suspend fun deliverFetches(qjs: QuickJs, fetches: AsyncFetches) {
        while (true) {
            val item = fetches.results.poll() ?: break
            runCatching {
                qjs.evaluate<Any?>(
                    "__zResolveFetch(${quote(item.first)}, ${quote(item.second)})",
                    "resolve.js",
                    false,
                )
            }
        }
    }

    private fun failure(msg: String): String =
        JSONObject().put("ok", false).put("error", msg).toString()

    private suspend fun run(
        providerDir: File,
        sourceId: String,
        fnName: String,
        argsJson: String,
        budgetMs: Long,
    ): String {
        val scriptFile = File(providerDir, "provider.js")
        val source = runCatching { scriptFile.readText() }.getOrNull()
        if (source.isNullOrBlank()) return failure("provider file missing — reinstall this extension")
        val settingsJson = runCatching {
            File(providerDir, "settings.json").takeIf { it.isFile }?.readText() ?: "{}"
        }.getOrDefault("{}")
        return gate.withPermit {
            withTimeoutOrNull(budgetMs + CALL_GRACE_MS) {
                withContext(Dispatchers.Default) {
                    val deferred = CompletableDeferred<String>()
                    val fetches = AsyncFetches()
                    var qjs: QuickJs? = null
                    try {
                        qjs = createEngine(deferred, sourceId, settingsJson, source, fetches, budgetMs)
                        qjs.evaluate<Any?>("__zCall(${quote(fnName)}, ${quote(argsJson)});", "call.js", false)
                        val deadline = System.currentTimeMillis() + budgetMs
                        var idleRounds = 0
                        var idleBail = false
                        while (!deferred.isCompleted && System.currentTimeMillis() < deadline) {
                            val next = qjs.evaluate<Any?>("__zFireTimer()", "timer.js", false)
                            deliverFetches(qjs, fetches)
                            if (deferred.isCompleted) break
                            val wait = (next as? Number)?.toLong() ?: -1L
                            val busy = fetches.pending.get() > 0 || fetches.results.isNotEmpty()
                            when {
                                busy -> {
                                    idleRounds = 0
                                    delay(if (wait >= 1 && wait <= 50) wait else 8)
                                }
                                wait > 0 -> {
                                    idleRounds = 0
                                    delay(wait.coerceAtMost(TIMER_MAX_WAIT_MS))
                                }
                                wait == 0L -> {
                                    idleRounds = 0
                                    delay(1)
                                }
                                else -> {
                                    idleRounds++
                                    if (idleRounds >= 4) {
                                        idleBail = true
                                        break
                                    }
                                    delay(12)
                                }
                            }
                        }
                        if (deferred.isCompleted) deferred.await()
                        else if (idleBail) failure("the extension stopped responding before it could finish")
                        else failure("timed out after ${budgetMs / 1000}s")
                    } catch (e: Throwable) {
                        if (deferred.isCompleted) runCatching { deferred.await() }.getOrDefault(failure("failed"))
                        else {
                            Logs.log("Zangetsu", "call $fnName failed: ${e.message}")
                            failure(e.message ?: e.javaClass.simpleName)
                        }
                    } finally {
                        runCatching { qjs?.close() }
                    }
                }
            } ?: failure("timed out after ${budgetMs / 1000}s")
        }
    }

    /** Calls one provider function with a JSON args array. Answers the `{"ok","data"/"error"}` envelope. */
    suspend fun call(
        providerDir: File,
        sourceId: String,
        fnName: String,
        argsJson: String,
        catalogBudget: Boolean = false,
    ): String = run(
        providerDir = providerDir,
        sourceId = sourceId,
        fnName = fnName,
        argsJson = argsJson,
        budgetMs = if (catalogBudget) CATALOG_TIMEOUT_MS else CALL_TIMEOUT_MS,
    )

    /** True when the file loads and exports the provider surface. */
    suspend fun validate(context: Context, providerDir: File, sourceId: String): String =
        withContext(Dispatchers.Default) {
            val source = runCatching { File(providerDir, "provider.js").readText() }.getOrNull()
                ?: return@withContext "NO"
            var qjs: QuickJs? = null
            try {
                qjs = QuickJs.create(jobDispatcher = Dispatchers.Default)
                qjs.evaluationTimeoutMillis = VALIDATE_TIMEOUT_MS
                runCatching { qjs.memoryLimit = ENGINE_MEMORY_LIMIT }
                qjs.function("__zFetchAsync") { ""
                }
                qjs.function("__zDone") { ""
                }
                qjs.function("__zLog") { ""
                }
                qjs.evaluateVoid(bootJs(sourceId, "{}"), "zangetsu-boot.js")
                qjs.evaluateVoid(source, "$sourceId.js")
                val probe = qjs.evaluate<Any?>(
                    "JSON.stringify({ video: typeof getVideoSources, detail: typeof getDetail, episodes: typeof getEpisodes, search: typeof search, home: typeof getHome });",
                    "probe.js",
                    false,
                ).toString()
                val o = runCatching { JSONObject(probe) }.getOrNull() ?: return@withContext "NO"
                if (o.optString("video") != "function") return@withContext "NO"
                if (o.optString("detail") != "function" && o.optString("episodes") != "function" &&
                    o.optString("search") != "function" && o.optString("home") != "function"
                ) return@withContext "NO"
                "OK"
            } catch (e: Throwable) {
                "ERR:" + (e.message ?: e.javaClass.simpleName)
            } finally {
                runCatching { qjs?.close() }
            }
        }
}
