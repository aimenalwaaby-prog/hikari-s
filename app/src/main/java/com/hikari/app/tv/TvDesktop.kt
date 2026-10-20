package com.hikari.app.tv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The desktop client's visual language, translated onto the television layout.
 *
 * Every value here mirrors that client's tokens: pure-black AMOLED surfaces
 * (Background/SurfaceCard Black, SurfaceElevated #0A0A0A — their exact AMOLED
 * triple), white-on-black text, the hairline border, 8dp corners and the
 * 8/12/16/20/24 spacing scale. The ACCENT is not hardcoded: it reads the
 * app's own colour scheme, so Settings → Appearance & Theme → Accent colour
 * drives the TV exactly like the phone, and the AMOLED Black theme (the fresh-
 * install default) lands on true black with no grey layers.
 *
 * This is deliberately a read-only overlay, not a second theme engine: there
 * is nothing to save, sync or migrate, so it cannot change behaviour — TV
 * components just paint from here instead of the phone-tuned surfaces.
 */
object TvDesktop {

    /** Page background: true black, like the reference's AMOLED mode. */
    val Background: Color = Color.Black

    /** Card surface: true black — cards separate from the page by their
     *  hairline border and content, never by a grey fill. */
    val SurfaceCard: Color = Color.Black

    /** Elevated surface (thumb placeholders, rails, sheets): one step above
     *  black, the reference's exact AMOLED elevated value. */
    val SurfaceElevated: Color = Color(0xFF0A0A0A)

    val TextPrimary: Color = Color.White
    val TextMuted: Color = Color.White.copy(alpha = 0.7f)

    /** Hairline divider/border on black. */
    val Divider: Color = Color(0xFF1C1C22)

    /** Glass hairline for floating bars and lit cards. */
    val GlassEdge: Color = Color.White.copy(alpha = 0.14f)

    /** The 8dp corner language (cards, thumbs, chips). */
    val CornerCard: Dp = 8.dp
    val CornerLarge: Dp = 12.dp
    val CornerPill: Dp = 24.dp

    /** The spacing scale. */
    val SpaceXS: Dp = 8.dp
    val SpaceS: Dp = 12.dp
    val SpaceM: Dp = 16.dp
    val SpaceL: Dp = 20.dp
    val SpaceXL: Dp = 24.dp

    /** 2:3 poster geometry, same as the reference. */
    const val PosterAspect: Float = 2f / 3f

    /** The app's accent on TV (Settings → Appearance & Theme → Accent). */
    val accent: Color
        @Composable get() = MaterialTheme.colorScheme.primary
}

/**
 * The reference's hover treatment, translated to the remote: focus IS hover
 * on a television.
 *
 * A focused card grows slightly and gains an accent ring — the same two cues
 * the desktop client's hovered poster gives (its blurred glow + scale). The
 * glow is a crisp ring rather than a blur on purpose: a full-screen blur
 * repaints a bitmap these boxes cannot afford (see TvCinematicHero), while a
 * ring costs one stroked round-rect.
 */
@Composable
fun Modifier.tvFocusGlow(corner: Dp = TvDesktop.CornerCard): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.045f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tvFocusScale",
    )
    val ring by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        label = "tvFocusRing",
    )
    val accent = TvDesktop.accent
    // No clip on the card itself: the corner curve would reach into the
    // title/year lines and bite their edges (the cards' own artwork boxes
    // already round themselves). The ring below carries its own shape.
    return this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .border(
            width = 2.dp,
            color = accent.copy(alpha = 0.85f * ring),
            shape = RoundedCornerShape(corner),
        )
}
