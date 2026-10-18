package com.hikari.app.zangetsu

import com.hikari.app.data.CatalogRef
import com.hikari.app.data.Episode
import com.hikari.app.data.MediaItem
import com.hikari.app.data.MediaType
import com.hikari.app.data.ProviderConfig
import com.hikari.app.data.StreamSource
import com.hikari.app.data.SubtitleSource
import com.hikari.app.providers.ContentProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * A Zangetsu provider — one self-contained `.js` scraper speaking `getHome`
 * / `popular` / `search` / `getDetail` / `getEpisodes` / `getVideoSources`
 * (see [ZangetsuRuntime]).
 *
 * Items carry the source's own URL as their id; episodes carry the source's
 * episode URL (often a custom scheme packing season+episode). Anime sources
 * map to SERIES + rawType "anime" (tracker parity, so anime plays down the
 * anime path); movie sources map by their own type string.
 */
class ZangetsuProvider(override val config: ProviderConfig) : ContentProvider {

    companion object {
        /** Why a catalogue call produced nothing, for the screen's empty state. */
        val catalogErrors = ConcurrentHashMap<String, String>()
        /** Why a stream call produced nothing, for the sources sheet. */
        val streamErrors = ConcurrentHashMap<String, String>()
        private const val MAX_ITEMS = 500
    }

    private val sourceId: String get() = config.id.removePrefix("zangetsu|")
    private val dir: File get() = ZangetsuPluginManager.dirOf(config)

    private data class Info(val type: String, val name: String)
    private var info: Info? = null

    private suspend fun info(): Info {
        info?.let { return it }
        val payload = ZangetsuRuntime.call(dir, sourceId, "getInfo", "[]")
        val data = dataObject(payload)
        // getInfo is advisory: a source without it still works, typed by its
        // own items. Default to movie (most Zangetsu sources are movies).
        val t = data?.optString("type").orEmpty().lowercase().ifBlank { "movie" }
        return Info(t, data?.optString("name").orEmpty()).also { info = it }
    }

    private fun args(vararg pairs: Pair<String, Any?>): String {
        val o = JSONObject()
        for ((k, v) in pairs) {
            when (v) {
                null -> o.put(k, JSONObject.NULL)
                is Number, is Boolean, is String -> o.put(k, v)
                else -> o.put(k, v.toString())
            }
        }
        return JSONArray(listOf(o)).toString()
    }

    // ---- catalogue ----

    private suspend fun homeRows(): JSONArray? {
        val payload = ZangetsuRuntime.call(dir, sourceId, "getHome", "[{}]", catalogBudget = true)
        val data = dataOf(payload)
        if (data is JSONArray && data.length() > 0) {
            catalogErrors.remove(config.id)
            return data
        }
        // Fall back to the flattened popular list as one row.
        val pop = ZangetsuRuntime.call(dir, sourceId, "popular", "[{}]", catalogBudget = true)
        val pdata = dataOf(pop)
        if (pdata is JSONArray && pdata.length() > 0) {
            catalogErrors.remove(config.id)
            val row = JSONObject().put("title", "Popular").put("items", pdata)
            return JSONArray().put(row)
        }
        noteCatalogFailure(payload, "Catalogue")
        return null
    }

    override suspend fun catalogs(): List<CatalogRef> = withContext(Dispatchers.IO) {
        val rows = homeRows() ?: return@withContext emptyList()
        buildList {
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val title = r.optString("title").trim().ifBlank { continue }
                add(CatalogRef(config.id, MediaType.UNKNOWN, "row:$title", title, "zangetsu"))
            }
        }.distinctBy { it.id }
    }

    override suspend fun homeCatalogs(): List<CatalogRef> = catalogs()

    override suspend fun getCatalog(ref: CatalogRef, page: Int): List<MediaItem> =
        withContext(Dispatchers.IO) {
            // Rows arrive whole; page 1 serves the row, later pages are empty.
            if (page > 1) return@withContext emptyList()
            val title = ref.id.substringAfter("row:", "")
            val rows = homeRows() ?: return@withContext emptyList()
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                if (r.optString("title").trim() == title) return@withContext toItems(r.optJSONArray("items"))
            }
            emptyList()
        }

    override suspend fun search(query: String, page: Int): List<MediaItem> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val payload = ZangetsuRuntime.call(
                dir, sourceId, "search",
                "[${JSONObject.quote(query)}, ${page.coerceAtLeast(1)}, {}]",
                catalogBudget = true,
            )
            val data = dataOf(payload) ?: run {
                noteCatalogFailure(payload, "Search")
                return@withContext emptyList()
            }
            catalogErrors.remove(config.id)
            toItems(data)
        }

    private fun noteCatalogFailure(payload: String, what: String) {
        val err = runCatching { JSONObject(payload).optString("error") }.getOrNull()
        val msg = if (err.isNullOrBlank()) "$what returned nothing" else err
        catalogErrors[config.id] = "$what failed — $msg".take(200)
    }

    private suspend fun toItems(data: Any?): List<MediaItem> {
        val arr = data as? JSONArray ?: return emptyList()
        val fallbackAnime = info().type == "anime"
        val out = ArrayList<MediaItem>(minOf(arr.length(), MAX_ITEMS))
        for (i in 0 until minOf(arr.length(), MAX_ITEMS)) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url").trim()
            val title = o.optString("title").trim()
            if (url.isBlank() || title.isBlank()) continue
            val id = o.optString("id").trim().ifBlank { url }
            val t = o.optString("type").trim().lowercase()
            val anime = t == "anime" || (t.isBlank() && fallbackAnime)
            out += MediaItem(
                providerId = config.id,
                id = id,
                title = title,
                type = if (anime) MediaType.SERIES else mediaTypeOf(t),
                posterUrl = o.optString("cover").ifBlank { o.optString("poster") }
                    .ifBlank { o.optString("image") }.ifBlank { null },
                year = o.optInt("year", 0).takeIf { it > 0 },
                overview = o.optString("description").ifBlank { null },
                genres = stringList(o.optJSONArray("genres")),
                rating = o.optDouble("rating", 0.0).takeIf { it > 0 },
                rawType = if (anime) "anime" else t.ifBlank { "movie" },
            )
        }
        return out
    }

    // ---- details ----

    override suspend fun getMeta(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        if (item.id.isBlank()) return@withContext item
        val payload = ZangetsuRuntime.call(dir, sourceId, "getDetail", "[${JSONObject.quote(item.id)}, {}]")
        val d = dataObject(payload) ?: return@withContext item
        val t = d.optString("type").trim().lowercase()
        val anime = t == "anime" || (t.isBlank() && item.rawType == "anime")
        MediaItem(
            providerId = item.providerId,
            id = item.id,
            title = d.optString("title").trim().ifBlank { item.title },
            type = if (anime) MediaType.SERIES else mediaTypeOf(t).takeIf { it != MediaType.UNKNOWN } ?: item.type,
            posterUrl = d.optString("cover").ifBlank { d.optString("poster") }
                .ifBlank { item.posterUrl.orEmpty() }.ifBlank { null },
            year = d.optInt("year", 0).takeIf { it > 0 } ?: item.year,
            overview = d.optString("description").ifBlank { item.overview.orEmpty() }.ifBlank { null },
            genres = stringList(d.optJSONArray("genres")).ifEmpty { item.genres },
            rating = d.optDouble("rating", 0.0).takeIf { it > 0 } ?: item.rating,
            rawType = if (anime) "anime" else t.ifBlank { item.rawType },
            originalTitle = item.originalTitle,
        )
    }

    override suspend fun getEpisodes(item: MediaItem): List<Episode>? =
        withContext(Dispatchers.IO) {
            if (item.id.isBlank()) return@withContext null
            val payload = ZangetsuRuntime.call(dir, sourceId, "getEpisodes", "[${JSONObject.quote(item.id)}, {}]")
            val data = dataOf(payload) as? JSONArray ?: return@withContext null
            buildList {
                for (i in 0 until data.length()) {
                    val e = data.optJSONObject(i) ?: continue
                    val url = e.optString("url").trim()
                    if (url.isBlank()) continue
                    add(
                        Episode(
                            number = e.optInt("number", i + 1).takeIf { it > 0 } ?: (i + 1),
                            id = url,
                            name = e.optString("title").ifBlank { "Episode ${i + 1}" },
                            image = e.optString("thumbnail").ifBlank { null },
                            overview = e.optString("description").ifBlank { null },
                            released = e.optString("date").ifBlank { null },
                        )
                    )
                }
            }.ifEmpty { null }
        }

    // ---- streams ----

    override suspend fun getStreams(item: MediaItem, episode: Episode?): List<StreamSource> =
        withContext(Dispatchers.IO) {
            val epUrl = episode?.id?.trim().orEmpty().ifBlank { item.id.trim() }
            if (epUrl.isBlank()) {
                streamErrors[config.id] = "Nothing to play (empty link)."
                return@withContext emptyList()
            }
            val payload = ZangetsuRuntime.call(dir, sourceId, "getVideoSources", "[${JSONObject.quote(epUrl)}]")
            val data = dataOf(payload) as? JSONArray
            if (data == null || data.length() == 0) {
                val err = runCatching { JSONObject(payload).optString("error") }.getOrNull()
                streamErrors[config.id] = if (err.isNullOrBlank()) "No playable sources." else err.take(200)
                return@withContext emptyList()
            }
            streamErrors.remove(config.id)
            buildList {
                for (i in 0 until data.length()) {
                    val s = data.optJSONObject(i) ?: continue
                    val url = s.optString("url").trim()
                    if (url.isBlank()) continue
                    val quality = s.optString("quality").trim().ifBlank { "auto" }
                    val container = s.optString("container").trim().lowercase()
                    val label = s.optString("label").trim()
                    val headers = linkedMapOf<String, String>()
                    s.optJSONObject("headers")?.keys()?.forEach { k ->
                        headers[k] = s.optJSONObject("headers").optString(k)
                    }
                    val subs = buildList {
                        val sa = s.optJSONArray("subtitles") ?: return@buildList
                        for (j in 0 until sa.length()) {
                            val t = sa.optJSONObject(j) ?: continue
                            val su = t.optString("url").ifBlank { t.optString("file") }.trim()
                            if (su.isBlank()) continue
                            add(
                                SubtitleSource(
                                    lang = t.optString("lang").ifBlank { t.optString("label") }.ifBlank { "sub" },
                                    url = su,
                                    name = t.optString("label"),
                                )
                            )
                        }
                    }
                    add(
                        StreamSource(
                            name = (label.ifBlank { quality }),
                            url = url,
                            headers = headers,
                            subtitles = subs,
                            isM3u8 = container == "hls" || container == "m3u8" || ".m3u8" in url,
                            isMpd = container == "mpd" || ".mpd" in url,
                            provider = "Zangetsu",
                            providerId = config.id,
                            providerName = config.name,
                            details = listOf(quality, container.ifBlank { null }, label.ifBlank { null })
                                .filterNotNull().distinct().joinToString(" • "),
                        )
                    )
                }
            }
        }

    // ---- helpers ----

    private fun dataObject(payload: String): JSONObject? {
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        if (!root.optBoolean("ok", false)) return null
        return root.optJSONObject("data")
    }

    private fun dataOf(payload: String): Any? {
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        if (!root.optBoolean("ok", false)) return null
        if (root.isNull("data")) return null
        return root.get("data")
    }

    private fun stringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun mediaTypeOf(raw: String): MediaType = when (raw.lowercase()) {
        "movie" -> MediaType.MOVIE
        "tv", "series", "show" -> MediaType.SERIES
        else -> MediaType.UNKNOWN
    }
}
