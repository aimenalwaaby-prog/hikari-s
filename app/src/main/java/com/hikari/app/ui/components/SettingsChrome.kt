package com.hikari.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.tv.tvPress
import com.hikari.app.i18n.tr
import com.hikari.app.ui.theme.rememberGlassTokens

// Part of this file's own package: the app-wide "hide the explanations" switch
// (see LocalHideHelp), which every caption here answers to.
import com.hikari.app.ui.components.helpShown

/**
 * The header of EVERY page inside Settings — a folder page, the Logs page, the
 * player-control editor — draws its name at this one size, so no settings page
 * looks louder or quieter than the next one. 16.sp is also what the index's
 * folder rows use, so a folder's page never shouts louder than the row that
 * opened it.
 *
 * The size is not a taste call: the header is one line (icon, then name), and
 * 16.sp is the largest that keeps the longest name in Settings — "Personal
 * Catalog creator" — on that line on a phone. The numbers, so nobody has to
 * re-derive them: the back button and badge take 92.dp, leaving ~236.dp of a
 * 360.dp phone's row, and the longest name measures 178.dp at 16.sp in Roboto
 * Bold — room to spare even with the phone's own font size set a notch or two up.
 * Anything larger ellipsises, and a truncated name reads as a bug.
 */
val SETTINGS_TITLE_SIZE: TextUnit = 16.sp

/**
 * The round accent badge a settings row leads with: a circle of accent wash with
 * a hairline ring, so the icon reads as a glass token rather than a flat square.
 * One composable for every one of them, so the index, the folder headers and the
 * shortcut cards can never drift apart.
 */
@Composable
fun SettingsIconBadge(icon: ImageVector, size: Dp = 46.dp) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/**
 * The same round badge, but for one of the app's own round icon artworks
 * (see res/drawable-nodpi/ic_set_*): the artwork is already a finished circle
 * with its own rim, so it is drawn full-bleed with no tint and no wash behind
 * it — tinting it would repaint the whole painting one flat colour.
 */
@Composable
fun SettingsIconBadgeRes(@DrawableRes resId: Int, size: Dp = 46.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(resId),
            contentDescription = null,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * A self-made colourful badge in the folder-badge language (navy ring,
 * saturated disc, white glyph): for every inner settings row that has no
 * supplied artwork. The disc colour is picked deterministically from [key]
 * so a row keeps its colour across restarts, themes and locales — the same
 * row is never two colours, and neighbours rarely share one.
 */
@Composable
fun SettingsColorBadge(icon: ImageVector, key: String, size: Dp = 46.dp) {
    val disc = badgeColorFor(key)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(disc)
            .border(1.5.dp, androidx.compose.ui.graphics.Color(0xFF0F2540), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/** Stable vibrant disc for [SettingsColorBadge], by row key. */
fun badgeColorFor(key: String): androidx.compose.ui.graphics.Color {
    val palette = listOf(
        0xFFE5484D, 0xFFF97316, 0xFFF5A623, 0xFF22C55E, 0xFF14B8A6,
        0xFF0EA5E9, 0xFF6366F1, 0xFFA855F7, 0xFFEC4899, 0xFF84CC16,
        0xFF06B6D4, 0xFFEAB308, 0xFF8B5CF6, 0xFF10B981,
    )
    val idx = (key.hashCode() and Int.MAX_VALUE) % palette.size
    return androidx.compose.ui.graphics.Color(palette[idx].toInt())
}

/**
 * The way out of a settings page: one circular glass button, the same size and
 * fill everywhere, so backing out of a folder, the Logs page and the
 * player-control editor are all the same gesture in the same place.
 */
@Composable
private fun SettingsBackButton(onBack: () -> Unit) {
    val glass = rememberGlassTokens()
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(glass.fillTop)
            .border(1.dp, glass.border, CircleShape)
            .clickable(onClick = onBack)
            .tvPress(previewPass = true, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = tr("Back to settings"),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The header of a page inside Settings — ONE line: the back button, an optional
 * icon badge, then the page's name with its one-line summary under the name.
 * A folder page leads with the folder's own icon, so it starts with the same
 * "icon, then what it is called" shape as the row that opened it; the pages that
 * are not folders (Logs, the player-control editor) pass no icon and simply hand
 * the name the extra width.
 *
 * The name is one line by construction, not by hope: one fixed size
 * ([SETTINGS_TITLE_SIZE]) for every page, ellipsised rather than wrapped, and
 * weighted so it takes whatever the back button and badge leave. The size was
 * chosen to fit the longest name Settings has, so in practice nothing ellipsises.
 *
 * [breadcrumb] is set only for a sub-folder ("Appearance ›") and is printed on
 * the line ABOVE that row — inside it, the breadcrumb would eat the width the
 * name needs. [trailing] is for the rare page with an action up there (the
 * player-control editor's Reset).
 *
 * The strings arrive already translated (see `tr`), like [ChoiceRow]'s.
 */
@Composable
fun SettingsPageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
    breadcrumb: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        if (breadcrumb != null) {
            Text(
                breadcrumb,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsBackButton(onBack)
            if (iconRes != null) {
                Spacer(Modifier.width(8.dp))
                SettingsIconBadgeRes(iconRes, 40.dp)
            } else if (icon != null) {
                Spacer(Modifier.width(8.dp))
                SettingsIconBadge(icon, 40.dp)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = SETTINGS_TITLE_SIZE,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
                if (helpShown()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    }
}
