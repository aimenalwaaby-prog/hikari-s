package com.hikari.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hikari.app.data.RatingSource
import com.hikari.app.data.TitleRating
import com.hikari.app.ui.components.PosterImage

/**
 * The CloudStream desktop client's visual language, rebuilt for the
 * television layout. Every value mirrors that client's tokens: the Navy
 * default theme (background 0x0C0C16, card 0x161624, elevated 0x20202E),
 * white primary text at 70%-alpha muted, hairline edges, 8dp poster corners
 * and 16:9 episode stills with rating badges, white Play pills and dark
 * elevated dialog panels.
 *
 * The ACCENT is not copied: it reads the app's own colour scheme, so
 * Settings → Appearance & Theme → Accent colour keeps driving the TV, and
 * Hikari's gold identity stays intact on every screen.
 */
object Cs3 {

    /** Page background: the reference default Navy. */
    val Background: Color = Color(0xFF0C0C16)

    /** Card surface: one step up from the page, like the reference. */
    val SurfaceCard: Color = Color(0xFF161624)

    /** Elevated surface (dialogs, thumbs, sheets). */
    val SurfaceElevated: Color = Color(0xFF20202E)

    val TextPrimary: Color = Color.White
    val TextMuted: Color = Color.White.copy(alpha = 0.7f)

    /** Hairline divider/border on the dark surfaces. */
    val Divider: Color = Color(0xFF2A2A38)

    /** Glass hairline for focused cards and floating bars. */
    val Edge: Color = Color.White.copy(alpha = 0.14f)

    /** The corner language: 8dp cards/posters, 12dp buttons, 20dp panels. */
    val CornerCard = 8.dp
    val CornerButton = 12.dp
    val CornerPanel = 20.dp
    val CornerPill = 24.dp

    /** 2:3 posters and 16:9 stills, same as the reference. */
    const val PosterAspect: Float = 2f / 3f
    const val StillAspect: Float = 16f / 9f

    /** The app's accent on TV (Settings → Appearance & Theme → Accent). */
    val accent: Color
        @Composable get() = MaterialTheme.colorScheme.primary
}

/**
 * Focus treatment for CS3 cards: a gentle scale plus an accent ring, the
 * remote's version of the desktop client's hover glow. The ring is composited
 * by alpha so an unfocused card costs nothing extra to draw.
 */
@Composable
fun Modifier.cs3Focus(corner: androidx.compose.ui.unit.Dp = Cs3.CornerCard): Modifier {
    var focused by remember { mutableStateOf(false) }
    val accent = Cs3.accent
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(
            width = 2.dp,
            color = if (focused) accent else Color.Transparent,
            shape = RoundedCornerShape(corner),
        )
}

/**
 * Aspect-safe backdrop art, the reference DetailsBackdrop contract:
 *
 * - Genuinely wide art (a TMDB 16:9 backdrop) paints SHARP, top-anchored, with
 *   the reference's triple gradient (bottom scrim into the page, left
 *   vignette, vertical melt).
 * - Anything else (a portrait poster, a thumb) NEVER sharp-crops: it fills
 *   blurred behind the same gradients, exactly like the reference's
 *   blurred-fallback path.
 *
 * This is what ends the stretched-poster backdrops: a poster-only title gets
 * atmosphere, never a beheaded face.
 */
@Composable
fun Cs3BackdropArt(
    model: Any?,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Cs3.Background)) {
        if (model != null) {
            if (wide) {
                PosterImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Blurred fill: atmosphere without the crop.
                PosterImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(22.dp, BlurredEdgeTreatment.Unbounded),
                )
                // Readability dim over the blur.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }
        }
        // Bottom scrim: melts the art into the page.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.70f to Cs3.Background.copy(alpha = 0.55f),
                        0.88f to Cs3.Background.copy(alpha = 0.90f),
                        1.00f to Cs3.Background,
                    )
                )
        )
        // Left vignette: the text column's backdrop.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.00f to Cs3.Background.copy(alpha = 0.82f),
                        0.30f to Cs3.Background.copy(alpha = 0.55f),
                        0.55f to Cs3.Background.copy(alpha = 0.18f),
                        0.75f to Color.Transparent,
                        1.00f to Color.Transparent,
                    )
                )
        )
    }
}

/** Site colour for a rating badge, the reference's branded-rating colours. */
private fun ratingSiteColor(source: RatingSource): Color = when (source) {
    RatingSource.IMDB -> Color(0xFFF5C518)
    RatingSource.TMDB -> Color(0xFF01B4E4)
    RatingSource.TOMATOMETER -> Color(0xFFFA320A)
    RatingSource.POPCORN -> Color(0xFFFFB300)
    RatingSource.METACRITIC -> Color(0xFF66CC33)
    RatingSource.LETTERBOXD -> Color(0xFF00C030)
}

private fun ratingSiteLabel(source: RatingSource): String = when (source) {
    RatingSource.IMDB -> "IMDb"
    RatingSource.TMDB -> "TMDB"
    RatingSource.TOMATOMETER -> "RT"
    RatingSource.POPCORN -> "RT"
    RatingSource.METACRITIC -> "MC"
    RatingSource.LETTERBOXD -> "LB"
}

/**
 * The reference's ratings row: small dark pills, site name in the site
 * colour, value in white — IMDb 7.4, TMDB 7.2, MAL 74% — followed by the
 * content-rating chip. Tapping a badge opens the same source dialog as the
 * phone badges.
 */
@Composable
fun Cs3RatingBadges(
    ratings: List<TitleRating>,
    cert: String?,
    onRatingClick: (TitleRating) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ratings.isEmpty() && cert.isNullOrBlank()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ratings.forEach { r ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                    .clickable { onRatingClick(r) }
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    text = ratingSiteLabel(r.source),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ratingSiteColor(r.source),
                )
                Text(
                    text = r.value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
        if (!cert.isNullOrBlank()) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    text = cert,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * The reference's primary action: a solid white pill with a dark label —
 * "Watch Now", "Play S2 E1", "Resume S2 E2 • 23m left". Reads across the room.
 */
@Composable
fun Cs3PlayButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.PlayArrow,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
        ),
        shape = RoundedCornerShape(Cs3.CornerButton),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
        modifier = modifier.height(48.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
    }
}

/**
 * The reference's secondary actions: dark translucent squares with a
 * hairline — library, download, trailer. 52dp, 12dp corners.
 */
@Composable
fun Cs3SquareButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    highlighted: Boolean = false,
) {
    val accent = Cs3.accent
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(Cs3.CornerButton),
        color = if (highlighted) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (highlighted) accent.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.28f),
        ),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (highlighted) accent else tint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * A section header in the reference language: bold white title on the left
 * ("Season 2 • 3 Episodes", "Cast & Crew", "Screenshots", "Show Details"),
 * actions pinned right.
 */
@Composable
fun Cs3SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Cs3.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        actions?.invoke()
    }
}

/** Small header action: muted text button, like the reference's right-side links. */
@Composable
fun Cs3HeaderLink(
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val accent = Cs3.accent
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (highlighted) accent else Cs3.TextMuted,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/**
 * The reference's hero dots: a dark pill with dashes, the active one long
 * and white. D-pad safe: each dash is clickable.
 */
@Composable
fun Cs3HeroDots(
    count: Int,
    active: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count < 2) return
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(Cs3.CornerButton))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val selected = i == active
            Box(
                Modifier
                    .width(if (selected) 28.dp else 10.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected) Color.White
                        else Color.White.copy(alpha = 0.30f)
                    )
                    .clickable { onSelect(i) }
            )
        }
    }
}

/**
 * The reference's episode card: a 16:9 still with the rating badge top-left
 * and the watched check top-right, then the episode code ("S2 • EPISODE 1"),
 * the bold title, a two-line synopsis and duration — over a dark card with a
 * hairline edge. Anti-spoiler blurs the still and masks the text.
 *
 * [art] is the episode's own thumbnail (provider) or its TMDB still, both
 * landscape; [fallbackBlur] is the show art that fills blurred when there is
 * no still at all — a card never wears a sharp-cropped poster.
 */
@Composable
fun Cs3EpisodeCard(
    art: Any?,
    fallbackBlur: Any?,
    code: String,
    title: String,
    desc: String,
    duration: String?,
    rating10: Double?,
    progress: Float,
    watched: Boolean,
    hideSpoilers: Boolean,
    onPlay: () -> Unit,
    onDownload: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val accent = Cs3.accent
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Cs3.CornerButton))
            .background(Cs3.SurfaceCard)
            .border(0.5.dp, Cs3.Edge, RoundedCornerShape(Cs3.CornerButton))
            .clickable(onClick = onPlay)
            .cs3Focus(Cs3.CornerButton),
    ) {
        Column {
            // Still frame.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(Cs3.StillAspect)
                    .background(Cs3.SurfaceElevated),
            ) {
                val still = art
                if (still != null) {
                    PosterImage(
                        model = still,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (hideSpoilers) Modifier.blur(14.dp) else Modifier),
                    )
                } else if (fallbackBlur != null) {
                    PosterImage(
                        model = fallbackBlur,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(18.dp, BlurredEdgeTreatment.Unbounded),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                    )
                }
                // Bottom melt into the card body.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1.00f to Color.Black.copy(alpha = 0.55f),
                            )
                        )
                )
                // Rating badge, top-left.
                if (rating10 != null && rating10 > 0.0 && !hideSpoilers) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "★ " + String.format(java.util.Locale.US, "%.1f", rating10),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                        )
                    }
                }
                // Watched check, top-right.
                if (watched && !hideSpoilers) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            // Text body.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = code,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = Cs3.TextMuted,
                    maxLines = 1,
                )
                Text(
                    text = if (hideSpoilers) "••••••••" else title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Cs3.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.then(if (hideSpoilers) Modifier.blur(3.dp) else Modifier),
                )
                if (desc.isNotBlank()) {
                    Text(
                        text = if (hideSpoilers) "Hidden by anti-spoiler." else desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = Cs3.TextMuted,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp,
                        modifier = Modifier.then(if (hideSpoilers) Modifier.blur(4.dp) else Modifier),
                    )
                }
                if (duration != null) {
                    Text(
                        text = duration,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            }
        }
        // Continue-watching progress along the card's bottom edge.
        if (progress > 0f && !hideSpoilers) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent),
                )
            }
        }
    }
}

/**
 * The reference's poster card: a 2:3 poster with 8dp corners, the rating
 * badge top-left, the title below in white. Posters stay posters — never
 * stretched into landscape frames.
 */
@Composable
fun Cs3PosterCard(
    model: Any?,
    title: String,
    rating10: Double?,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Cs3.accent
    Column(
        modifier
            .clickable(onClick = onClick)
            .cs3Focus(Cs3.CornerCard),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(Cs3.PosterAspect)
                .clip(RoundedCornerShape(Cs3.CornerCard))
                .background(Cs3.SurfaceElevated)
                .border(0.5.dp, Cs3.Edge, RoundedCornerShape(Cs3.CornerCard)),
        ) {
            if (model != null) {
                PosterImage(
                    model = model,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (rating10 != null && rating10 > 0.0) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "★ " + String.format(java.util.Locale.US, "%.1f", rating10),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
            }
            if (progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(accent),
                    )
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = Cs3.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 2.dp, top = 6.dp, end = 2.dp),
        )
    }
}

/**
 * A cast/character tile: profile art with 8dp corners, name in white and the
 * character (or voice) in muted below — the reference's Cast & Crew row.
 */
@Composable
fun Cs3PersonCard(
    model: Any?,
    name: String,
    sub: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(104.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clip(RoundedCornerShape(Cs3.CornerCard))
                .background(Cs3.SurfaceElevated)
                .border(0.5.dp, Cs3.Edge, RoundedCornerShape(Cs3.CornerCard)),
        ) {
            if (model != null) {
                PosterImage(
                    model = model,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Cs3.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, top = 6.dp, end = 2.dp),
        )
        if (!sub.isNullOrBlank()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = Cs3.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp),
            )
        }
    }
}

/**
 * The reference's dialog panel: an elevated dark box with 20dp corners and a
 * hairline edge — Select Provider, Manage Feed, confirmations. Content goes
 * inside; the panel only owns the container.
 */
@Composable
fun Cs3DialogPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Cs3.CornerPanel),
        color = Cs3.SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 24.dp,
    ) {
        content()
    }
}
