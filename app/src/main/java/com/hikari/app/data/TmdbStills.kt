package com.hikari.app.data

import com.hikari.app.nuvio.TmdbResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * TMDB episode stills for the television detail page.
 *
 * Provider episode lists frequently ship no thumbnails at all — and the TV
 * episode shelf is a strip of 16:9 stills, so those rows used to wear the
 * show's backdrop (or poster) on every card. Like the desktop client's
 * episode enricher, the TMDB still is authoritative here: season by season,
 * `(season, episode) → still URL`.
 *
 * Fills blanks only and never throws: an unresolvable title, a missing
 * season, a dead network — all of those are an empty map, and the shelf keeps
 * whatever art it already had. Results are cached process-wide per TMDB id.
 */
object TmdbStills {

    private const val IMG_STILL = "https://image.tmdb.org/t/p/w500"

    private val mem = ConcurrentHashMap<String, Map<Pair<Int, Int>, String>>()

    /**
     * Still URLs for [seasons] of [item], keyed by (season, episode).
     * Series only; anything else (and anything unresolvable) is empty.
     */
    suspend fun stillsFor(item: MediaItem, seasons: Set<Int>): Map<Pair<Int, Int>, String> =
        runCatching {
            if (item.type != MediaType.SERIES || seasons.isEmpty()) return emptyMap()
            val resolved = TmdbResolver.resolve(item) ?: return emptyMap()
            // "anime" is TMDB's tv namespace with an anime item behind it (see
            // TmdbResolver): the season endpoint is the same one.
            if (resolved.mediaType == "movie") return emptyMap()
            mem[resolved.tmdbId]?.let { return it }
            val out = withContext(Dispatchers.IO) {
                coroutineScope {
                    seasons
                        .filter { it in 0..60 }
                        .take(40)
                        .map { sn ->
                            async {
                                seasonStills(resolved.tmdbId, sn)
                            }
                        }
                        .flatMap { runCatching { it.await() }.getOrDefault(emptyList()) }
                        .associate { it }
                }
            }
            if (mem.size >= 200) mem.clear()
            if (out.isNotEmpty()) mem[resolved.tmdbId] = out
            out
        }.getOrDefault(emptyMap())

    private suspend fun seasonStills(tmdbId: String, season: Int): List<Pair<Pair<Int, Int>, String>> =
        runCatching {
            val sd = TmdbResolver.apiGet("/tv/$tmdbId/season/$season", emptyMap())
                ?: return emptyList()
            val eps = sd.optJSONArray("episodes") ?: return emptyList()
            buildList {
                for (i in 0 until eps.length()) {
                    val e = eps.optJSONObject(i) ?: continue
                    val en = e.optInt("episode_number")
                    if (en <= 0) continue
                    val still = e.optString("still_path").trim()
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?.let { IMG_STILL + it } ?: continue
                    add((season to en) to still)
                }
            }
        }.getOrDefault(emptyList())
}
