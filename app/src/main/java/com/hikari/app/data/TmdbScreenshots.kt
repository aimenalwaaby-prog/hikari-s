package com.hikari.app.data

import com.hikari.app.nuvio.TmdbResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * TMDB artwork backdrops ("screenshots") for the television detail page's
 * Screenshots section, like the desktop client's own screenshots strip.
 *
 * One `/movie/{id}/images` (or `/tv/...`) call per title, backdrops only
 * (`w1280`), capped at 8. Cached process-wide, fills blanks and never throws:
 * an unresolvable title or a dead network is an empty list, and the section
 * simply doesn't render.
 */
object TmdbScreenshots {

    private const val IMG_BACKDROP = "https://image.tmdb.org/t/p/w1280"

    private val mem = ConcurrentHashMap<String, List<String>>()

    suspend fun backdropsFor(item: MediaItem): List<String> =
        runCatching {
            if (item.type != MediaType.SERIES && item.type != MediaType.MOVIE) return emptyList()
            val resolved = TmdbResolver.resolve(item) ?: return emptyList()
            mem[resolved.tmdbId]?.let { return it }
            val out = withContext(Dispatchers.IO) {
                val obj = TmdbResolver.apiGet(
                    "/${resolved.mediaType}/${resolved.tmdbId}/images",
                    mapOf("include_image_language" to "en,null"),
                ) ?: return@withContext emptyList()
                val arr = obj.optJSONArray("backdrops") ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        if (size >= 8) break
                        val path = arr.optJSONObject(i)?.optString("file_path")
                            ?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: continue
                        add(IMG_BACKDROP + path)
                    }
                }
            }
            if (mem.size >= 200) mem.clear()
            if (out.isNotEmpty()) mem[resolved.tmdbId] = out
            out
        }.getOrDefault(emptyList())
}
