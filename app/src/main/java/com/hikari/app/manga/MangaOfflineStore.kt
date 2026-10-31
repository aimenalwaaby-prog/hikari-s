package com.hikari.app.manga

import android.content.Context
import android.graphics.BitmapFactory
import com.hikari.app.data.StreamSource
import com.hikari.app.reader.source.HikariPageSource
import com.hikari.app.reader.source.MangaSource
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Page metadata persisted with an offline chapter. Transport URLs and headers are intentionally omitted. */
data class MangaOfflinePage(
    val index: Int,
    val sourceKeyHash: String,
    val relativePath: String,
    val byteCount: Long,
    val downloaded: Boolean,
)

data class MangaOfflineChapter(
    val id: String,
    val providerId: String,
    val mangaUrl: String,
    val mangaTitle: String,
    val posterUrl: String,
    val chapterUrl: String,
    val chapterLabel: String,
    val status: String,
    val pages: List<MangaOfflinePage>,
    val updatedAt: Long,
    val error: String? = null,
) {
    val downloadedPages: Int get() = pages.count { it.downloaded }
    val totalBytes: Long get() = pages.sumOf { it.byteCount }
}

/**
 * Durable manga page storage. It is deliberately separate from DownloadEngine (video/HLS)
 * and WebtoonPageCache (a bounded, disposable image cache).
 */
object MangaOfflineStore {
    private const val MANIFEST = "chapter.json"
    private const val STATUS_DOWNLOADING = "downloading"
    private const val STATUS_PAUSED = "paused"
    private const val STATUS_FAILED = "failed"
    private const val STATUS_COMPLETE = "complete"

    private fun chapterId(providerId: String, mangaUrl: String, chapterUrl: String): String =
        sha256("$providerId\n$mangaUrl\n$chapterUrl")

    private fun root(context: Context): File = File(context.applicationContext.filesDir, "manga/offline")
    private fun directory(context: Context, id: String): File = File(root(context), id)
    private fun manifestFile(context: Context, id: String): File = File(directory(context, id), MANIFEST)

    /** Returns a manifest whether complete or partial; validate files before presenting it as offline-ready. */
    fun load(
        context: Context,
        providerId: String,
        mangaUrl: String,
        chapterUrl: String,
    ): MangaOfflineChapter? = readManifest(manifestFile(context, chapterId(providerId, mangaUrl, chapterUrl)))

    fun all(context: Context): List<MangaOfflineChapter> =
        root(context).listFiles()?.asSequence()
            ?.filter { it.isDirectory }
            ?.mapNotNull { readManifest(File(it, MANIFEST)) }
            ?.sortedByDescending { it.updatedAt }
            ?.toList()
            .orEmpty()

    fun isComplete(context: Context, chapter: MangaOfflineChapter): Boolean =
        chapter.status == STATUS_COMPLETE && chapter.pages.isNotEmpty() && chapter.pages.all { page ->
            page.downloaded && safePageFile(context, chapter.id, page).let { it.isFile && it.length() == page.byteCount && it.length() > 0L && isValidImage(it) }
        }

    fun pageFile(context: Context, chapter: MangaOfflineChapter, page: MangaOfflinePage): File =
        safePageFile(context, chapter.id, page)

    /** Synthetic offline URLs prevent signed CDN URLs/cookies from being written to manifests. */
    fun streams(chapter: MangaOfflineChapter): List<StreamSource> = chapter.pages
        .sortedBy { it.index }
        .map { page ->
            StreamSource(
                name = "Offline page ${page.index + 1}",
                url = "hikari-offline://${chapter.id}/${page.index}",
                pageUrl = "",
            )
        }

    /** Maps synthetic reader page URLs to their durable files. */
    fun filesByPageUrl(context: Context, chapter: MangaOfflineChapter): Map<String, File> =
        chapter.pages.associate { page ->
            "hikari-offline://${chapter.id}/${page.index}" to pageFile(context, chapter, page)
        }

    /** Removes a single offline chapter and frees its durable page files. */
    fun delete(context: Context, providerId: String, mangaUrl: String, chapterUrl: String): Boolean {
        val dir = directory(context, chapterId(providerId, mangaUrl, chapterUrl))
        return !dir.exists() || dir.deleteRecursively()
    }

    /**
     * Resolves page streams with the same source used by the reader, then downloads every page
     * into filesDir. Existing valid pages are reused on retry; a chapter is marked complete only
     * after every ordered page has been written and validated.
     */
    suspend fun downloadChapter(
        context: Context,
        providerId: String,
        mangaUrl: String,
        mangaTitle: String,
        chapterUrl: String,
        chapterLabel: String,
        posterUrl: String,
        httpSource: HttpSource?,
        resolvePages: suspend () -> List<StreamSource>,
        progress: suspend (downloaded: Int, total: Int, bytes: Long) -> Unit = { _, _, _ -> },
    ): MangaOfflineChapter = withContext(Dispatchers.IO) {
        val id = chapterId(providerId, mangaUrl, chapterUrl)
        val storedPosterUrl = posterUrl.takeIf {
            (it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)) &&
                it.length <= 2048 && '?' !in it && '#' !in it
        }.orEmpty()
        val dir = directory(context, id)
        val old = readManifest(File(dir, MANIFEST))
        fun saveFailure(t: Throwable) {
            val base = old ?: MangaOfflineChapter(
                id = id,
                providerId = providerId,
                mangaUrl = mangaUrl,
                mangaTitle = mangaTitle,
                posterUrl = storedPosterUrl,
                chapterUrl = chapterUrl,
                chapterLabel = chapterLabel,
                status = STATUS_FAILED,
                pages = emptyList(),
                updatedAt = System.currentTimeMillis(),
            )
            val status = if (t is kotlinx.coroutines.CancellationException) STATUS_PAUSED else STATUS_FAILED
            writeManifest(context, base.copy(status = status, updatedAt = System.currentTimeMillis(), error = safeError(t)))
        }
        val streams = try {
            MangaPageRepository.filter(resolvePages(), chapterUrl, posterUrl)
        } catch (t: Throwable) {
            runCatching { saveFailure(t) }
            throw t
        }
        if (streams.isEmpty()) {
            val failure = IOException("The source returned no chapter pages")
            saveFailure(failure)
            throw failure
        }

        val oldByKey = old?.pages.orEmpty().associateBy { it.sourceKeyHash }
        val pageDir = File(dir, "pages").apply { mkdirs() }
        val targetPages = streams.mapIndexed { index, stream ->
            val stable = sha256(MangaPageRepository.stableKey(stream))
            val prior = oldByKey[stable]
            val rel = prior?.relativePath?.takeIf { it.matches(Regex("pages/[0-9]{6}\\.img")) }
                ?: "pages/${(index + 1).toString().padStart(6, '0')}.img"
            val file = File(dir, rel)
            val valid = prior?.downloaded == true && file.isFile && file.length() == prior.byteCount && file.length() > 0L
            MangaOfflinePage(index = index, sourceKeyHash = stable, relativePath = rel,
                byteCount = if (valid) file.length() else 0L, downloaded = valid)
        }
        val pageSource = HikariPageSource(providerId, "offline downloader").apply {
            this.httpSource = httpSource
            setHeaders(streams.associate { it.url to it.headers })
        }
        var chapter = MangaOfflineChapter(id, providerId, mangaUrl, mangaTitle, storedPosterUrl, chapterUrl, chapterLabel,
            STATUS_DOWNLOADING, targetPages, System.currentTimeMillis())
        writeManifest(context, chapter)
        progress(chapter.downloadedPages, streams.size, chapter.totalBytes)

        try {
            for (index in streams.indices) {
                currentCoroutineContext().ensureActive()
                val existing = chapter.pages[index]
                if (existing.downloaded) continue
                val stream = streams[index]
                val page = File(dir, existing.relativePath)
                val part = File(page.parentFile, page.name + ".part")
                part.delete()
                try {
                    pageSource.downloadPageImage(
                        MangaSource.PageDescriptor(pageUrl = stream.pageUrl, imageUrl = stream.url),
                        part,
                    )
                    if (!isValidImage(part)) throw IOException("Page ${index + 1} was empty or not a readable image")
                    if (page.exists() && !page.delete()) throw IOException("Could not replace a previous page file")
                    if (!part.renameTo(page)) throw IOException("Could not finalize page ${index + 1}")
                } catch (t: Throwable) {
                    part.delete()
                    throw t
                }
                val updated = chapter.pages.toMutableList()
                updated[index] = existing.copy(byteCount = page.length(), downloaded = true)
                chapter = chapter.copy(pages = updated, updatedAt = System.currentTimeMillis(), error = null)
                writeManifest(context, chapter)
                progress(chapter.downloadedPages, streams.size, chapter.totalBytes)
            }
            if (chapter.pages.any { !it.downloaded }) throw IOException("Some chapter pages are still missing")
            chapter = chapter.copy(status = STATUS_COMPLETE, updatedAt = System.currentTimeMillis(), error = null)
            writeManifest(context, chapter)
            chapter
        } catch (t: Throwable) {
            val status = if (t is kotlinx.coroutines.CancellationException) STATUS_PAUSED else STATUS_FAILED
            chapter = chapter.copy(status = status, updatedAt = System.currentTimeMillis(), error = safeError(t))
            runCatching { writeManifest(context, chapter) }
            throw t
        }
    }

    private fun safePageFile(context: Context, id: String, page: MangaOfflinePage): File {
        require(page.relativePath.matches(Regex("pages/[0-9]{6}\\.img"))) { "Unsafe offline page path" }
        val dir = directory(context, id).canonicalFile
        val file = File(dir, page.relativePath).canonicalFile
        require(file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
            "Offline page escaped its chapter directory"
        }
        return file
    }

    private fun isValidImage(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private fun writeManifest(context: Context, chapter: MangaOfflineChapter) {
        val dir = directory(context, chapter.id).apply { mkdirs() }
        val json = JSONObject()
            .put("version", 1)
            .put("id", chapter.id)
            .put("providerId", chapter.providerId)
            .put("mangaUrl", chapter.mangaUrl)
            .put("mangaTitle", chapter.mangaTitle)
            .put("posterUrl", chapter.posterUrl)
            .put("chapterUrl", chapter.chapterUrl)
            .put("chapterLabel", chapter.chapterLabel)
            .put("status", chapter.status)
            .put("updatedAt", chapter.updatedAt)
            .put("error", chapter.error ?: JSONObject.NULL)
            .put("pages", JSONArray().apply {
                chapter.pages.forEach { page ->
                    put(JSONObject()
                        .put("index", page.index)
                        .put("sourceKeyHash", page.sourceKeyHash)
                        .put("relativePath", page.relativePath)
                        .put("byteCount", page.byteCount)
                        .put("downloaded", page.downloaded))
                }
            })
        val target = File(dir, MANIFEST)
        val temp = File(dir, "$MANIFEST.tmp")
        val backup = File(dir, "$MANIFEST.bak")
        FileOutputStream(temp).use { stream ->
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            stream.flush()
            stream.fd.sync()
        }
        backup.delete()
        if (target.exists() && !target.renameTo(backup)) target.delete()
        if (!temp.renameTo(target)) {
            backup.renameTo(target)
            throw IOException("Could not atomically save the offline chapter manifest")
        }
        backup.delete()
    }

    private fun readManifest(file: File): MangaOfflineChapter? = runCatching {
        if (!file.isFile) return null
        val json = JSONObject(file.readText(Charsets.UTF_8))
        if (json.optInt("version") != 1) return null
        val id = json.getString("id")
        if (!id.matches(Regex("[a-f0-9]{64}"))) return null
        val pagesJson = json.optJSONArray("pages") ?: JSONArray()
        val pages = (0 until pagesJson.length()).mapNotNull { i ->
            val obj = pagesJson.optJSONObject(i) ?: return@mapNotNull null
            val rel = obj.optString("relativePath")
            if (!rel.matches(Regex("pages/[0-9]{6}\\.img"))) return@mapNotNull null
            MangaOfflinePage(
                index = obj.optInt("index", i).coerceAtLeast(0),
                sourceKeyHash = obj.optString("sourceKeyHash"),
                relativePath = rel,
                byteCount = obj.optLong("byteCount", 0L).coerceAtLeast(0L),
                downloaded = obj.optBoolean("downloaded", false),
            )
        }
        MangaOfflineChapter(
            id = id,
            providerId = json.optString("providerId"),
            mangaUrl = json.optString("mangaUrl"),
            mangaTitle = json.optString("mangaTitle"),
            posterUrl = json.optString("posterUrl"),
            chapterUrl = json.optString("chapterUrl"),
            chapterLabel = json.optString("chapterLabel"),
            status = json.optString("status", STATUS_FAILED),
            pages = pages.sortedBy { it.index },
            updatedAt = json.optLong("updatedAt", 0L),
            error = json.optString("error").takeIf { it.isNotBlank() && it != "null" },
        )
    }.getOrNull()

    private fun safeError(t: Throwable): String =
        (t.message ?: t.javaClass.simpleName).replace(Regex("https?://\\S+"), "[source URL]").take(220)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
