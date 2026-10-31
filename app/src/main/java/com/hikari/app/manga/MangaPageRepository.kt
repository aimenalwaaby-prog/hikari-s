package com.hikari.app.manga

import com.hikari.app.data.StreamSource

/** Shared chapter-page filtering for online reading and durable offline downloads. */
object MangaPageRepository {
    fun filter(
        streams: List<StreamSource>,
        chapterUrl: String,
        posterUrl: String,
    ): List<StreamSource> {
        val poster = posterUrl.trim()
        fun isPreview(url: String): Boolean {
            val low = url.lowercase()
            return "thumb" in low || "preview" in low || "?type=" in low || "thumbnail" in low
        }
        return streams
            .filter {
                it.url.isNotBlank() && it.url != chapterUrl && !isPreview(it.url) &&
                    (poster.isBlank() || (it.url != poster && normalize(it.url) != normalize(poster)))
            }
            .distinctBy { normalize(it.url) }
    }

    /** Drops ephemeral query tokens for stable resume matching; never persisted as a request URL. */
    fun stableKey(stream: StreamSource): String =
        "${normalize(stream.pageUrl)}|${normalize(stream.url)}"

    private fun normalize(url: String): String =
        url.substringBefore("?").substringBefore("#").trim().lowercase()
}
