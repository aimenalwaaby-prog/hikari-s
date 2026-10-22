package com.hikari.app.tv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hikari.app.data.Collection
import com.hikari.app.providers.ContentProvider
import com.hikari.app.i18n.tr
import com.hikari.app.ui.components.PosterImage

/**
 * The television's reference-style "Select Provider" dialog: a title, a
 * subtitle saying what the pick does, the "All Plugins (Global)" button, a
 * filter field, then one row per source. A plain tap picks exactly one source
 * and leaves; holding a row switches to multi-select (checkboxes + Done), so
 * the remote can build a set without the sheet Home uses.
 *
 * Television only — Home and the phone search keep the shared bottom sheet.
 */
@Composable
fun TvProviderDialog(
    providers: List<ContentProvider>,
    collections: List<Collection>,
    /** Every key currently picked (empty = All). */
    selection: List<String>,
    /** A plain tap in single-select mode: null = All, else the picked key. */
    onPick: (String?) -> Unit,
    /** Multi-select's Done button: the keys to save. */
    onDone: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var multi by remember { mutableStateOf(false) }
    var working by remember(selection) { mutableStateOf(selection) }
    val ts = TvUi.scale()

    val ordered = remember(providers, query) {
        val narrowed = if (query.isBlank()) providers
        else providers.filter { it.config.name.contains(query, ignoreCase = true) }
        narrowed.sortedBy { it.config.name.lowercase() }
    }
    val shownCollections = remember(collections, query) {
        if (query.isBlank()) collections
        else collections.filter { it.name.contains(query, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(20.dp),
            color = TvDesktop.SurfaceCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, TvDesktop.GlassEdge),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                // Title + subtitle, the way the reference labels the box.
                Text(
                    tr("Select Provider"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = MaterialTheme.typography.headlineSmall.fontSize * ts,
                    fontWeight = FontWeight.Bold,
                    color = Cs3.TextPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("Choose which sources this search runs against."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cs3.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                // "All Plugins (Global)": the search-everything answer, first.
                Button(
                    onClick = { onPick(null) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!multi && selection.isEmpty()) Cs3.accent else Color.White.copy(alpha = 0.10f),
                        contentColor = if (!multi && selection.isEmpty()) Color.White else Cs3.TextPrimary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (!multi && selection.isEmpty()) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        tr("All Plugins (Global)"),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(12.dp))
                // The filter field: dozens of installs are not scrollable.
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(tr("Filter providers…")) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = Cs3.TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cs3.accent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                        focusedTextColor = Cs3.TextPrimary,
                        unfocusedTextColor = Cs3.TextPrimary,
                        cursorColor = Cs3.accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(shownCollections, key = { "col-" + it.id }) { col ->
                        val key = com.hikari.app.ui.navigation.Routes.COLLECTION_PROVIDER_PREFIX + col.id
                        val checked = key in working
                        TvProviderRow(
                            name = col.name,
                            kind = tr("Collection"),
                            iconUrl = null,
                            checked = if (multi) checked else null,
                            selected = !multi && key in selection,
                            onTap = {
                                if (multi) {
                                    working = if (checked) working - key else working + key
                                } else {
                                    onPick(key)
                                }
                            },
                            onHold = {
                                if (!multi) {
                                    multi = true
                                    working = (selection + key).distinct()
                                }
                            },
                        )
                    }
                    items(ordered, key = { it.config.id }) { p ->
                        val key = p.config.id
                        val checked = key in working
                        TvProviderRow(
                            name = p.config.name,
                            kind = p.config.type.groupLabel,
                            iconUrl = p.config.iconUrl,
                            checked = if (multi) checked else null,
                            selected = !multi && key in selection,
                            onTap = {
                                if (multi) {
                                    working = if (checked) working - key else working + key
                                } else {
                                    onPick(key)
                                }
                            },
                            onHold = {
                                if (!multi) {
                                    multi = true
                                    working = (selection + key).distinct()
                                }
                            },
                        )
                    }
                }
                // The multi-select footer: Done saves the set, tapping out
                // (or Back) saves nothing — an accidental mode change can
                // never change the search.
                if (multi) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = {
                            multi = false
                            working = selection
                        }) { Text(tr("Cancel")) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onDone(working) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Cs3.accent,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                tr("Done") + if (working.isNotEmpty()) " (${working.size})" else "",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvProviderRow(
    name: String,
    kind: String,
    iconUrl: String?,
    /** Null = single-select mode (no box drawn); else the ticked state. */
    checked: Boolean?,
    selected: Boolean,
    onTap: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) Cs3.accent.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (selected) Cs3.accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f),
                shape,
            )
            .combinedClickable(onClick = onTap, onLongClick = onHold)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Provider icon, or the name's first letter when it has none.
        if (!iconUrl.isNullOrBlank()) {
            PosterImage(
                model = iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.35f)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Cs3.accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.firstOrNull()?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold,
                    color = Cs3.TextPrimary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontWeight = FontWeight.SemiBold,
                color = Cs3.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                kind,
                style = MaterialTheme.typography.labelSmall,
                color = Cs3.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (checked != null) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = Cs3.accent,
                    uncheckedColor = Color.White.copy(alpha = 0.45f),
                ),
            )
        } else if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Cs3.accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
