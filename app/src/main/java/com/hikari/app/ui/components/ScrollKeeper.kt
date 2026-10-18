package com.hikari.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import kotlinx.coroutines.delay

/**
 * Universal scroll-position hold: every page-level list, grid and scroll
 * column in the app keeps its place across visits — enter anything, go back,
 * land exactly where you left it, never yanked by itself.
 *
 * WHY a keeper instead of plain remember: leaving a page disposes its
 * composition, so every list state dies with it. And WHY restore-by-wait
 * instead of restore-on-first-rows: content arrives incrementally (rails,
 * pages), so restoring against the first partial batch clamps to the wrong
 * spot and never retries — the reported "came back lower down" jump.
 *
 * CONTRACT per call site ([keptListState]/[keptGridState]/[keptScrollState]):
 * pass a [key] that names the exact content on screen (include ids, queries,
 * tabs — one screen shows many contents). The state saves on dispose and, on
 * return, waits (up to [RESTORE_WAIT_MS]) until the saved index actually
 * exists before jumping — paging and partial loads included. If the user
 * scrolls first, the restore stands down: a late jump must never steal a
 * list the user already grabbed.
 */
object ScrollKeeper {
    private data class Pos(val index: Int, val offset: Int)

    private val positions = java.util.concurrent.ConcurrentHashMap<String, Pos>()

    /** Remember where [key]'s list was. Zero positions are dropped, so a page
     *  left at its top never restores anywhere. */
    fun save(key: String, index: Int, offset: Int) {
        if (index <= 0 && offset <= 0) positions.remove(key)
        else positions[key] = Pos(index, offset)
    }

    /** Remember where [key]'s scroll column was (a column has only an offset). */
    fun save(key: String, offset: Int) {
        if (offset <= 0) positions.remove(key)
        else positions[key] = Pos(0, offset)
    }

    fun load(key: String): Pair<Int, Int>? =
        positions[key]?.let { it.index to it.offset }

    /** Forget [key] — for contents whose identity changed (a new query reuses
     *  the same screen, so its old position must not leak into the new one). */
    fun drop(key: String) {
        positions.remove(key)
    }
}

/** How long a returning page waits for its saved index to exist (slow rails,
 *  paged grids) before landing as close as it can. */
private const val RESTORE_WAIT_MS = 10_000L

/**
 * A [LazyListState] that holds its position across visits (see [ScrollKeeper]).
 *
 * @param key names the exact content on screen — include every id, query and
 * tab that changes what the list shows.
 */
@Composable
fun keptListState(key: String): LazyListState {
    val state = rememberLazyListState()
    LaunchedEffect(key) {
        val (index, offset) = ScrollKeeper.load(key) ?: return@LaunchedEffect
        if (index <= 0 && offset <= 0) return@LaunchedEffect
        val deadline = System.currentTimeMillis() + RESTORE_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            // The user grabbed the list before its content arrived: stand down.
            if (state.firstVisibleItemIndex != 0 || state.firstVisibleItemScrollOffset != 0) return@LaunchedEffect
            if (state.layoutInfo.totalItemsCount > index) {
                runCatching { state.scrollToItem(index, offset) }
                return@LaunchedEffect
            }
            delay(100L)
        }
        // The saved row never came back (shorter content): land at the end
        // rather than the top, closest to where the user was.
        if (state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) {
            runCatching {
                state.scrollToItem(maxOf(0, state.layoutInfo.totalItemsCount - 1))
            }
        }
    }
    DisposableEffect(key) {
        onDispose {
            runCatching {
                ScrollKeeper.save(key, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
            }
        }
    }
    return state
}

/**
 * A [LazyGridState] that holds its position across visits (see [ScrollKeeper]).
 *
 * @param key names the exact content on screen — include every id, query and
 * tab that changes what the grid shows.
 */
@Composable
fun keptGridState(key: String): LazyGridState {
    val state = rememberLazyGridState()
    LaunchedEffect(key) {
        val (index, offset) = ScrollKeeper.load(key) ?: return@LaunchedEffect
        if (index <= 0 && offset <= 0) return@LaunchedEffect
        val deadline = System.currentTimeMillis() + RESTORE_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            // The user grabbed the grid before its content arrived: stand down.
            if (state.firstVisibleItemIndex != 0 || state.firstVisibleItemScrollOffset != 0) return@LaunchedEffect
            if (state.layoutInfo.totalItemsCount > index) {
                runCatching { state.scrollToItem(index, offset) }
                return@LaunchedEffect
            }
            delay(100L)
        }
        if (state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) {
            runCatching {
                state.scrollToItem(maxOf(0, state.layoutInfo.totalItemsCount - 1))
            }
        }
    }
    DisposableEffect(key) {
        onDispose {
            runCatching {
                ScrollKeeper.save(key, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
            }
        }
    }
    return state
}

/**
 * A [ScrollState] that holds its position across visits (see [ScrollKeeper]).
 *
 * @param key names the exact content on screen — include every id and folder
 * path that changes what the column shows.
 */
@Composable
fun keptScrollState(key: String): ScrollState {
    val state = rememberScrollState()
    LaunchedEffect(key) {
        val (_, offset) = ScrollKeeper.load(key) ?: return@LaunchedEffect
        if (offset <= 0) return@LaunchedEffect
        val deadline = System.currentTimeMillis() + RESTORE_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            // The user grabbed the column before it laid out: stand down.
            if (state.value != 0) return@LaunchedEffect
            if (state.maxValue >= offset) {
                runCatching { state.scrollTo(offset) }
                return@LaunchedEffect
            }
            // Laid out but shorter than the saved offset (or not laid out
            // yet): keep waiting a little, then settle at the end.
            if (state.maxValue > 0) break
            delay(100L)
        }
        if (state.value == 0 && state.maxValue > 0) {
            runCatching { state.scrollTo(state.maxValue) }
        }
    }
    DisposableEffect(key) {
        onDispose {
            runCatching { ScrollKeeper.save(key, state.value) }
        }
    }
    return state
}
