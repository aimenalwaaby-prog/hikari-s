package com.hikari.app.kotatsu

import com.hikari.app.data.AppStore
import com.hikari.app.data.Logs
import com.hikari.app.data.ProviderConfig
import com.hikari.app.data.ProviderType
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of the bundled Kotatsu parsers (the official `kotatsu-parsers`
 * library — the exact extraction code the Kotatsu app runs, 1200+ manga /
 * manhwa / manhua / comics / novel sources).
 *
 * Each parser becomes one `manga|kotatsu|<NAME>` provider row, so the whole
 * existing manga pipeline (Extensions toggles, manga-tab catalogues, search,
 * detail, chapters, reader) works on them unchanged — the bridge to the
 * Tachiyomi source API is [KotatsuBridge], served through [MangaProvider].
 *
 * Kotatsu parsers are manga-family only (there are no anime or movie parsers
 * in that ecosystem): anime and movies keep riding the anime/video engines
 * (Aniyomi, CloudStream, Zangetsu, …), which already cover them.
 */
object KotatsuRegistry {

    const val PREFIX = "manga|kotatsu|"

    fun isKotatsuId(id: String): Boolean = id.startsWith(PREFIX)

    fun enumNameOf(id: String): String = id.removePrefix(PREFIX)

    private val parserCache = ConcurrentHashMap<String, MangaParser>()

    fun allSources(): List<MangaParserSource> =
        runCatching { MangaParserSource.entries.toList() }.getOrDefault(emptyList())

    fun parserOf(enumName: String): MangaParser? {
        val src = runCatching { MangaParserSource.valueOf(enumName) }.getOrNull() ?: return null
        return parserCache.getOrPut(enumName) {
            KotatsuContext.newParserInstance(src)
        }
    }

    fun configFor(src: MangaParserSource): ProviderConfig {
        val enabledDefault = src.name in ENABLED_BY_DEFAULT
        return ProviderConfig(
            id = PREFIX + src.name,
            name = src.title + " (Kotatsu)",
            type = ProviderType.MANGA,
            url = "kotatsu://" + src.name,
            enabled = enabledDefault,
            extra = "kotatsu",
            nsfw = (src.contentType == ContentType.HENTAI).takeIf { it },
        )
    }

    /**
     * Sources enabled on a fresh seed: the big reliable English libraries.
     * Everything else seeds installed-but-off, so the manga folder lists the
     * full catalogue and any source is one toggle away.
     */
    private val ENABLED_BY_DEFAULT = setOf(
        "MANGADEX", "MANGAPLUS", "COMICKFUN", "BATOTO", "MANGAFIRE", "MANGAPARK",
        "ASURASCANS", "FLAMESCANS", "REAPERSCANS", "LEVIATANSCANS", "MANHWATOP",
        "NINEMANGA", "MANGAREADERTO", "WEBTOONS", "MANGAFORESTIA", "TOONCLASH",
    )

    /**
     * One-time seed: every working parser as its own provider row (broken ones
     * skipped). A single store write — a thousand individual writes would
     * churn DataStore and the provider observers a thousand times.
     */
    suspend fun seedDefaults(store: AppStore) {
        val have = runCatching { store.providers() }.getOrDefault(emptyList())
        if (have.any { it.id.startsWith(PREFIX) }) return
        val sources = allSources().filter { !it.isBroken }
        if (sources.isEmpty()) {
            Logs.log("Kotatsu", "seed skipped: parser list empty")
            return
        }
        val rows = sources.map(::configFor)
        runCatching {
            store.updateProviders { before -> before + rows.filter { r -> before.none { it.id == r.id } } }
        }
        val on = rows.count { it.enabled }
        Logs.log("Kotatsu", "seeded ${rows.size} sources ($on on)")
    }
}
