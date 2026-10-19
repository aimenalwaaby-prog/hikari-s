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
        return ProviderConfig(
            id = PREFIX + src.name,
            name = src.title + " (Kotatsu)",
            type = ProviderType.MANGA,
            url = "kotatsu://" + src.name,
            enabled = true,
            extra = "kotatsu",
            nsfw = (src.contentType == ContentType.HENTAI).takeIf { it },
        )
    }

    /**
     * The auto-seed from 0.11.69 installed 1200+ provider rows at once, which
     * lagged the manga tab into crashes (log-proven: 1502 installed providers,
     * megabyte scroll keys). It is gone: Kotatsu sources are now installed
     * one-by-one from the Extensions → Kotatsu repos folder, like every other
     * engine. This one-time sweep removes the seeded rows; sources the user
     * installed themselves from the folder afterwards are untouched (marked by
     * [AppStore.kotatsuPruned]).
     */
    suspend fun pruneSeeded(store: AppStore) {
        if (runCatching { store.kotatsuPruned() }.getOrDefault(false)) return
        val have = runCatching { store.providers() }.getOrDefault(emptyList())
        val seeded = have.filter { it.id.startsWith(PREFIX) }
        if (seeded.isNotEmpty()) {
            runCatching {
                store.updateProviders { before -> before.filterNot { it.id.startsWith(PREFIX) } }
            }
            Logs.log("Kotatsu", "pruned ${seeded.size} auto-seeded sources (install what you want from Kotatsu repos)")
        }
        runCatching { store.markKotatsuPruned() }
    }

    @Deprecated("Auto-seed removed in 0.11.70 — sources install from the Kotatsu repos folder.")
    suspend fun seedDefaults(store: AppStore) {
        pruneSeeded(store)
    }
}
