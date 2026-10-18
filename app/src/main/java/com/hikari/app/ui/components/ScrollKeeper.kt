package com.hikari.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.hikari.app.data.Logs
import kotlinx.coroutines.delay

/**
 * Universal scroll-position hold: every page-level list, grid and scroll
 * column keeps its place across visits.
 *
 * Anchor-based: the saved slot records the key of the item at the top
 * ([Pos.anchor]) next to its index/offset, and the return lands on that
 * ITEM wherever it sits now — row order shifts (async sources finishing in
 * a different order, NSFW filtering, column-count changes moving cells) can
 * no longer park the page on the wrong content. The index is the fallback
 * when the anchor is gone. A restore never jumps to the end of the list on
 * a miss: a page whose content never comes back stays where it opened.
 */
object ScrollKeeper {
    data class Pos(val index: Int, val offset: Int, val anchor: String? = null)

    private val positions = java.util.concurrent.ConcurrentHashMap<String, Pos>()

    fun save(key: String, index: Int, offset: Int, anchor: String? = null) {
        if (index <= 0 && offset <= 0) positions.remove(key)
        else positions[key] = Pos(index, offset, anchor)
    }

    fun save(key: String, offset: Int) {
        if (offset <= 0) positions.remove(key)
        else positions[key] = Pos(0, offset, null)
    }

    fun load(key: String): Pos? = positions[key]

    fun drop(key: String) {
        positions.remove(key)
    }
}

private const val RESTORE_WAIT_MS = 10_000L

private fun scrollLog(msg: String) {
    runCatching { Logs.log("Scroll", msg) }
}

@Composable
fun keptListState(
    key: String,
    /** Item keys in Lazy order; used to re-find the saved anchor. */
    itemKeys: List<String> = emptyList(),
    /** Called while waiting when the target row is not loaded yet (paged lists). */
    onNeedMore: (() -> Unit)? = null,
): LazyListState {
    val saved = remember(key) { ScrollKeeper.load(key) }
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = (saved?.index ?: 0).coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = (saved?.offset ?: 0).coerceAtLeast(0),
    )
    val keysNow = rememberUpdatedState(itemKeys)
    val needMoreNow = rememberUpdatedState(onNeedMore)
    LaunchedEffect(key) {
        val s = ScrollKeeper.load(key)
        if (s == null) {
            scrollLog("miss key=$key")
            return@LaunchedEffect
        }
        if (s.index <= 0 && s.offset <= 0) return@LaunchedEffect
        scrollLog("load key=$key idx=${s.index} off=${s.offset} anchor=${s.anchor}")
        var target = s.index.coerceAtLeast(0)
        s.anchor?.let { a ->
            val ai = keysNow.value.indexOf(a)
            if (ai >= 0) target = ai
        }
        val deadline = System.currentTimeMillis() + RESTORE_WAIT_MS
        var ticks = 0
        while (System.currentTimeMillis() < deadline) {
            if (state.isScrollInProgress) {
                scrollLog("stand-down key=$key (user grabbed list)")
                return@LaunchedEffect
            }
            s.anchor?.let { a ->
                val ai = keysNow.value.indexOf(a)
                if (ai >= 0) target = ai
            }
            if (state.layoutInfo.totalItemsCount > target) {
                if (state.firstVisibleItemIndex != target ||
                    state.firstVisibleItemScrollOffset != s.offset
                ) {
                    runCatching { state.scrollToItem(target, s.offset) }
                }
                scrollLog("restored key=$key target=$target total=${state.layoutInfo.totalItemsCount}")
                return@LaunchedEffect
            }
            if (ticks % 5 == 0) runCatching { needMoreNow.value?.invoke() }
            ticks++
            delay(100L)
        }
        scrollLog("give-up key=$key target=$target total=${state.layoutInfo.totalItemsCount} (staying, no end jump)")
    }
    DisposableEffect(key) {
        onDispose {
            runCatching {
                val idx = state.firstVisibleItemIndex
                val off = state.firstVisibleItemScrollOffset
                val anchor = keysNow.value.getOrNull(idx)
                if (idx <= 0 && off <= 0) scrollLog("drop key=$key (left at top)")
                else {
                    ScrollKeeper.save(key, idx, off, anchor)
                    scrollLog("save key=$key idx=$idx off=$off anchor=$anchor")
                }
            }
        }
    }
    return state
}

@Composable
fun keptGridState(
    key: String,
    /** Item keys in grid order; used to re-find the saved anchor. */
    itemKeys: List<String> = emptyList(),
    /** Called while waiting when the target cell is not loaded yet (paged grids). */
    onNeedMore: (() -> Unit)? = null,
): LazyGridState {
    val saved = remember(key) { ScrollKeeper.load(key) }
    val state = rememberLazyGridState(
        initialFirstVisibleItemIndex = (saved?.index ?: 0).coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = (saved?.offset ?: 0).coerceAtLeast(0),
    )
    val keysNow = rememberUpdatedState(itemKeys)
    val needMoreNow = rememberUpdatedState(onNeedMore)
    LaunchedEffect(key) {
        val s = ScrollKeeper.load(key)
        if (s == null) {
            scrollLog("miss key=$key")
            return@LaunchedEffect
        }
        if (s.index <= 0 && s.offset <= 0) return@LaunchedEffect
        scrollLog("load key=$key idx=${s.index} off=${s.offset} anchor=${s.anchor}")
        var target = s.index.coerceAtLeast(0)
        s.anchor?.let { a ->
            val ai = keysNow.value.indexOf(a)
            if (ai >= 0) target = ai
        }
        val deadline = System.currentTimeMillis() + RESTORE_WAIT_MS
        var ticks = 0
        while (System.currentTimeMillis() < deadline) {
            if (state.isScrollInProgress) {
                scrollLog("stand-down key=$key (user grabbed grid)")
                return@LaunchedEffect
            }
            s.anchor?.let { a ->
                val ai = keysNow.value.indexOf(a)
                if (ai >= 0) target = ai
            }
            if (state.layoutInfo.totalItemsCount > target) {
                if (state.firstVisibleItemIndex != target ||
                    state.firstVisibleItemScrollOffset != s.offset
                ) {
                    runCatching { state.scrollToItem(target, s.offset) }
                }
                scrollLog("restored key=$key target=$target total=${state.layoutInfo.totalItemsCount}")
                return@LaunchedEffect
            }
            if (ticks % 5 == 0) runCatching { needMoreNow.value?.invoke() }
            ticks++
            delay(100L)
        }
        scrollLog("give-up key=$key target=$target total=${state.layoutInfo.totalItemsCount} (staying, no end jump)")
    }
    DisposableEffect(key) {
        onDispose {
            runCatching {
                val idx = state.firstVisibleItemIndex
                val off = state.firstVisibleItemScrollOffset
                val anchor = keysNow.value.getOrNull(idx)
                if (idx <= 0 && off <= 0) scrollLog("drop key=$key (left at top)")
                else {
                    ScrollKeeper.save(key, idx, off, anchor)
                    scrollLog("save key=$key idx=$idx off=$off anchor=$anchor")
                }
            }
        }
    }
    return state
}

@Composable
fun keptScrollState(key: String): ScrollState {
    val saved = remember(key) { ScrollKeeper.load(key) }
    val state = rememberScrollState(initial = (saved?.offset ?: 0).coerceAtLeast(0))
    LaunchedEffect(key) {
        val s = ScrollKeeper.load(key)
        if (s == null) {
            scrollLog("miss key=$key")
            return@LaunchedEffect
        }
        if (s.offset <= 0) return@LaunchedEffect
        scrollLog("load key=$key off=${s.offset}")
        val deadline = System.currentTimeMillis() + RESTORE_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (state.isScrollInProgress) {
                scrollLog("stand-down key=$key (user grabbed column)")
                return@LaunchedEffect
            }
            if (state.maxValue >= s.offset) {
                if (state.value != s.offset) runCatching { state.scrollTo(s.offset) }
                scrollLog("restored key=$key off=${s.offset}")
                return@LaunchedEffect
            }
            delay(100L)
        }
        scrollLog("give-up key=$key off=${s.offset} max=${state.maxValue} (staying, no end jump)")
    }
    DisposableEffect(key) {
        onDispose {
            runCatching {
                val off = state.value
                if (off <= 0) scrollLog("drop key=$key (left at top)")
                else {
                    ScrollKeeper.save(key, off)
                    scrollLog("save key=$key off=$off")
                }
            }
        }
    }
    return state
}
