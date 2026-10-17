package com.hikari.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.hikari.app.HikariApp
import com.hikari.app.data.Episode
import com.hikari.app.data.MediaItem
import com.hikari.app.data.MediaType
import com.hikari.app.manga.MangaChapter
import com.hikari.app.manga.MangaProgress
import com.hikari.app.manga.MangaStore
import com.hikari.app.manga.NovelHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The novel reader: text chapters from plain novel sources (see
 * [com.hikari.app.manga.MangaExtensionManager.isNovelId]).
 *
 * Separate from the image reader on purpose: text needs flowing paragraphs,
 * font sizing and light/sepia/dark themes — none of which belongs in the
 * image pipeline. Chapters, the chapter list and progress ride the same
 * [MangaStore] the image reader uses (same key shape), so follows, history
 * and "where was I" work across both.
 *
 * Chapter text arrives as [com.hikari.app.data.StreamSource.text] parts via
 * the provider's novel `getStreams` branch (blank urls, so the image reader
 * can never pick them up); they are joined here in order.
 */
@Composable
fun NovelReaderScreen(
    nav: NavHostController,
    providerId: String,
    mangaUrl: String,
    chapterUrl: String,
    title: String,
    posterUrl: String,
) {
    val context = LocalContext.current
    val app = context.applicationContext as HikariApp
    val key = "$providerId|$mangaUrl"

    var chapters by remember { mutableStateOf<List<MangaChapter>>(MangaStore.chaptersFor(key).orEmpty()) }
    // Resume the saved chapter when opened without one (continue-reading cards).
    var chapter by remember {
        mutableStateOf(
            chapterUrl.ifBlank { MangaStore.progressFor(key)?.chapterUrl.orEmpty() }
                .ifBlank { chapters.firstOrNull()?.url.orEmpty() }
        )
    }
    var paragraphs by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var fontSize by remember { mutableFloatStateOf(18f) }
    // 0 = dark, 1 = sepia, 2 = light.
    var theme by remember { mutableIntStateOf(0) }
    var showChapters by remember { mutableStateOf(false) }
    // Highlighter: tap paragraphs to mark them in the picked color. Saved per
    // novel+chapter (see [MangaStore.highlightsFor]) so the marks are still
    // there when the chapter is reopened.
    var highlightMode by remember { mutableStateOf(false) }
    var highlightColor by remember { mutableIntStateOf(NovelHighlight.COLORS.first()) }
    var highlightMap by remember { mutableStateOf(mapOf<Int, Int>()) }

    val item = remember(providerId, mangaUrl, title, posterUrl) {
        MediaItem(
            providerId = providerId,
            id = mangaUrl,
            title = title.ifBlank { mangaUrl },
            type = MediaType.SERIES,
            posterUrl = posterUrl.takeIf { it.isNotBlank() },
            rawType = "manga",
        )
    }
    val providerName = remember(providerId) { app.providers.byId(providerId)?.config?.name.orEmpty() }
    val chapterIndex = chapters.indexOfFirst { it.url == chapter }
    val chapterLabel = chapters.getOrNull(chapterIndex)?.label ?: title.ifBlank { mangaUrl }

    // The chapter list normally arrives cached (the detail screen fetched it).
    LaunchedEffect(key) {
        if (chapters.isNotEmpty()) return@LaunchedEffect
        val p = app.providers.byId(providerId) ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { p.getEpisodes(item) } }
        chapters = MangaStore.chaptersFor(key).orEmpty()
        if (chapter.isBlank()) chapter = chapters.firstOrNull()?.url.orEmpty()
    }

    // Load the chapter text, restore the saved position, then follow it.
    val listState = rememberLazyListState()
    LaunchedEffect(key, chapter, reload) {
        paragraphs = emptyList()
        error = null
        loading = true
        // The new chapter reuses this list state: drop the old chapter's
        // scroll position NOW, so a chapter opened from the bottom does not
        // paint from the bottom while its text loads.
        runCatching { listState.scrollToItem(0) }
        highlightMap = emptyMap()
        val p = app.providers.byId(providerId)
        if (p == null) {
            error = "This novel engine is not installed"
            loading = false
            return@LaunchedEffect
        }
        val idx = chapters.indexOfFirst { it.url == chapter }
        val ch = chapters.getOrNull(idx)
        val ep = Episode(
            number = if (idx >= 0) idx + 1 else 1,
            id = chapter,
            name = ch?.name,
            image = null,
            season = 1,
        )
        val streams = withContext(Dispatchers.IO) {
            runCatching { p.getStreams(item, ep) }.getOrNull().orEmpty()
        }
        val parts = streams.mapNotNull { it.text.takeIf { t -> t.isNotBlank() } }
        if (parts.isEmpty()) {
            error = "Chapter text unavailable"
            loading = false
            return@LaunchedEffect
        }
        val paras = parts.flatMap { it.split("\n\n") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
        paragraphs = paras
        loading = false
        // Read the saved position BEFORE marking the chapter open: the mark
        // below overwrites this chapter's row with page 0, so reading after
        // it always found 0 and a chapter was never resumed where it was left.
        val prior = MangaStore.progressFor(key)?.takeIf { it.chapterUrl == chapter }
        // Mark the chapter open (progress), then restore + follow the position.
        val chName = ch?.label ?: chapter
        MangaStore.setProgress(
            MangaProgress(
                mangaKey = key,
                providerId = providerId,
                providerName = providerName,
                mangaUrl = mangaUrl,
                title = title.ifBlank { mangaUrl },
                posterUrl = posterUrl.takeIf { it.isNotBlank() },
                chapterUrl = chapter,
                chapterName = chName,
                page = 0,
                pages = paras.size,
                at = System.currentTimeMillis(),
            )
        )
        highlightMap = MangaStore.highlightsFor(key)
            .filter { it.chapterUrl == chapter }
            .associate { it.paraIndex to it.color }
        if (prior != null && prior.page in paras.indices && prior.page > 0) {
            runCatching { listState.scrollToItem(prior.page) }
        } else {
            runCatching { listState.scrollToItem(0) }
        }
        var last = -1
        while (true) {
            delay(1500)
            val at = listState.firstVisibleItemIndex
            if (at != last && at in paras.indices) {
                last = at
                MangaStore.setProgress(
                    MangaProgress(
                        mangaKey = key,
                        providerId = providerId,
                        providerName = providerName,
                        mangaUrl = mangaUrl,
                        title = title.ifBlank { mangaUrl },
                        posterUrl = posterUrl.takeIf { it.isNotBlank() },
                        chapterUrl = chapter,
                        chapterName = chName,
                        page = at,
                        pages = paras.size,
                        at = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    fun stepChapter(next: Boolean) {
        if (chapters.isEmpty() || chapterIndex < 0) return
        val to = if (next) chapterIndex + 1 else chapterIndex - 1
        chapters.getOrNull(to)?.let { chapter = it.url }
    }

    val bg = when (theme) {
        1 -> Color(0xFFF4ECD8)
        2 -> Color(0xFFFFFFFF)
        else -> Color(0xFF121212)
    }
    val fg = when (theme) {
        1 -> Color(0xFF3E3526)
        2 -> Color(0xFF1A1A1A)
        else -> Color(0xFFE8E8E8)
    }
    val dim = when (theme) {
        1 -> Color(0xFF8A7D63)
        2 -> Color(0xFF757575)
        else -> Color(0xFF9E9E9E)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Top bar: back, title, theme, font size.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = fg)
            }
            Text(
                text = chapterLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { theme = (theme + 1) % 3 }) {
                Text(
                    text = when (theme) {
                        1 -> "Sepia"
                        2 -> "Light"
                        else -> "Dark"
                    },
                    color = fg,
                )
            }
            TextButton(
                onClick = { fontSize = (fontSize - 2f).coerceAtLeast(12f) },
                enabled = fontSize > 12f,
            ) { Text("A−", color = fg) }
            TextButton(
                onClick = { fontSize = (fontSize + 2f).coerceAtMost(28f) },
                enabled = fontSize < 28f,
            ) { Text("A+", color = fg) }
            TextButton(onClick = { highlightMode = !highlightMode }) {
                Text(
                    text = if (highlightMode) "Done" else "Highlight",
                    color = if (highlightMode) MaterialTheme.colorScheme.primary else fg,
                )
            }
        }
        // Chapter navigation.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = { stepChapter(false) },
                enabled = chapterIndex > 0,
            ) { Text("‹ Prev", color = if (chapterIndex > 0) fg else dim) }
            Text(
                text = if (chapters.isEmpty()) "" else "${(chapterIndex + 1).coerceAtLeast(1)} / ${chapters.size}",
                style = MaterialTheme.typography.labelMedium,
                color = dim,
                modifier = Modifier.clickable { showChapters = true },
            )
            TextButton(
                onClick = { stepChapter(true) },
                enabled = chapterIndex >= 0 && chapterIndex < chapters.size - 1,
            ) {
                Text(
                    "Next ›",
                    color = if (chapterIndex >= 0 && chapterIndex < chapters.size - 1) fg else dim,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        if (highlightMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    text = "Tap a paragraph to highlight it — tap a marked one to remove it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = dim,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NovelHighlight.COLORS.forEach { c ->
                        Text(
                            text = "●",
                            fontSize = 30.sp,
                            color = Color(c),
                            modifier = Modifier
                                .clickable { highlightColor = c }
                                .background(
                                    if (c == highlightColor) fg.copy(alpha = 0.25f)
                                    else Color.Transparent,
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error.orEmpty(), color = fg)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { reload++ }) { Text("Try again") }
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                ) {
                    item {
                        Text(
                            text = chapterLabel,
                            fontSize = (fontSize + 4f).sp,
                            fontWeight = FontWeight.Bold,
                            color = fg,
                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                        )
                    }
                    items(paragraphs.size, key = { it }) { idx ->
                        val para = paragraphs[idx]
                        val hl = highlightMap[idx]
                        var paraMod: Modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                        if (hl != null) {
                            paraMod = Modifier
                                .fillMaxWidth()
                                .background(Color(hl).copy(alpha = 0.35f))
                                .padding(bottom = 14.dp)
                        }
                        if (highlightMode) {
                            paraMod = paraMod.clickable {
                                if (highlightMap.containsKey(idx)) {
                                    MangaStore.clearHighlight(key, chapter, idx)
                                    highlightMap = highlightMap - idx
                                } else {
                                    MangaStore.setHighlight(
                                        key,
                                        NovelHighlight(key, chapter, idx, highlightColor),
                                    )
                                    highlightMap = highlightMap + (idx to highlightColor)
                                }
                            }
                        }
                        Text(
                            text = para,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize + 8f).sp,
                            color = fg,
                            modifier = paraMod,
                        )
                    }
                    item { Spacer(Modifier.height(48.dp)) }
                }
            }
        }
    }

    if (showChapters) {
        AlertDialog(
            onDismissRequest = { showChapters = false },
            confirmButton = {},
            text = {
                LazyColumn {
                    items(chapters) { ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chapter = ch.url
                                    showChapters = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = ch.label,
                                color = if (ch.url == chapter) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (ch.url == chapter) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            },
        )
    }
}
