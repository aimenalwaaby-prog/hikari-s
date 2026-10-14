package com.hikari.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Press-and-hold quick menu for poster cards.
 *
 * Hold a poster for 1 second and a semicircle with three actions fans out over
 * it — info (opens the detail page), play (starts playback) and add-to-library —
 * and sliding left/right WITHOUT lifting the finger moves the selection; the
 * highlighted action fires on release.
 *
 * Why custom instead of `combinedClickable`: the platform long-press fires at
 * ~500ms (too eager for a grid that also scrolls) and offers no drag-to-pick.
 * The gesture here consumes NOTHING until the menu is actually up, so taps
 * (the card's own `clickable`) and scrolls (the row) behave exactly as before:
 * only the moves after the 1-second hold are consumed, which is also what
 * stops the card's own tap from firing after a menu pick.
 *
 * Slots, left to right: 0 = info, 1 = play, 2 = library.
 */
const val QUICK_MENU_HOLD_MS = 1000L

class QuickMenuState {
    var visible by mutableStateOf(false)
    var selected by mutableIntStateOf(1)
}

@Composable
fun rememberQuickMenuState(): QuickMenuState = remember { QuickMenuState() }

/**
 * Hold-to-menu gesture for a poster. [onPick] fires once, on release, with the
 * chosen slot (0 info / 1 play / 2 library). Taps and scrolls pass through
 * untouched — see the note above.
 */
fun Modifier.quickMenuGesture(
    state: QuickMenuState,
    onPick: (Int) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val start = down.position
        val slop = viewConfiguration.touchSlop
        // Race the hold against release (tap) and drag (scroll). Nothing is
        // consumed here, so whatever wins behaves exactly as if this modifier
        // were not present.
        var outcome: String? = null
        withTimeoutOrNull(QUICK_MENU_HOLD_MS) {
            var ev = awaitPointerEvent()
            while (ev.changes.all { it.pressed }) {
                val p = ev.changes.firstOrNull()?.position
                if (p != null && (p - start).getDistance() > slop) {
                    outcome = "drag"
                    return@withTimeoutOrNull
                }
                ev = awaitPointerEvent()
            }
            outcome = "up"
        }
        // Timeout (outcome still null) is the ONLY path that shows the menu: a
        // tap or a scroll leaves everything to the card/row.
        if (outcome != null) return@awaitEachGesture
        state.selected = 1
        state.visible = true
        var sel = 1
        try {
            while (true) {
                val ev = awaitPointerEvent()
                val x = ev.changes.firstOrNull()?.position?.x
                val w = size.width.toFloat()
                if (x != null && w > 0f) {
                    // Horizontal slide picks the slot — left info, centre play,
                    // right library — so the thumb never has to aim at a small
                    // icon, just lean left or right.
                    sel = ((x / w) * 3).toInt().coerceIn(0, 2)
                    state.selected = sel
                }
                ev.changes.forEach { it.consume() }
                if (ev.changes.all { !it.pressed }) break
            }
        } finally {
            state.visible = false
        }
        onPick(sel)
    }
}

/** The semicircle overlay: dim glass fan with the three action icons. */
@Composable
fun BoxScope.QuickMenuOverlay(selected: Int) {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.matchParentSize()) {
        val w = size.width
        val h = size.height
        // Dim the art so the icons read on any poster.
        drawRect(Color.Black.copy(alpha = 0.28f))
        // The fan: a disc whose centre sits below the card's bottom edge, so
        // only its top cap shows — a semicircle rising from the bottom.
        val r = w * 0.78f
        val cx = w / 2f
        val cy = h + r * 0.28f
        drawCircle(
            color = Color.Black.copy(alpha = 0.62f),
            radius = r,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = scheme.primary.copy(alpha = 0.85f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickMenuIcon(icon = 0, selected = selected) {
            Icon(Icons.Filled.Info, contentDescription = "More info", tint = iconTint(0, selected))
        }
        QuickMenuIcon(icon = 1, selected = selected) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = iconTint(1, selected))
        }
        QuickMenuIcon(icon = 2, selected = selected) {
            Icon(Icons.Filled.BookmarkAdd, contentDescription = "Add to library", tint = iconTint(2, selected))
        }
    }
}

@Composable
private fun iconTint(icon: Int, selected: Int): Color {
    val scheme = MaterialTheme.colorScheme
    return if (icon == selected) scheme.onPrimary else Color.White
}

/** One fan button: a filled accent disc when selected, a dim disc otherwise. */
@Composable
private fun QuickMenuIcon(
    icon: Int,
    selected: Int,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val on = icon == selected
    androidx.compose.foundation.layout.Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(if (on) 52.dp else 44.dp)
            .background(
                if (on) scheme.primary else Color.Black.copy(alpha = 0.55f),
                CircleShape,
            ),
    ) {
        content()
    }
}
