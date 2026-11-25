package com.dudeapp.vault

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A look is offered only when this account's level, and its shared looks, allow it. */
private fun lockedFor(preset: ThemePreset, access: MyAccess): Boolean =
    preset.special && (!access.allowsLook(preset.catalogId))

/**
 * The look of the app: Light and Dark for everyone, and the picture looks for people with
 * special access. Each card is a small preview of that look.
 */
@Composable
fun ThemesScreen(
    theme: ThemePreset,
    /** What this account may do: its level, its looks and its switches. */
    access: MyAccess = MyAccess(),
    /** Opens the advanced page, where background and card pictures are chosen. */
    onAdvanced: () -> Unit = {},
    /** True while advanced mode is on; the row is a switch, not a door, when it is off. */
    advancedOn: Boolean = true,
    /** Turns advanced mode on or off right here. */
    onAdvancedChange: (Boolean) -> Unit = {},
    /** Something advanced is in use, so the row can say so. */
    advancedInUse: Boolean = false,
    /**
     * Whether this account may see Advanced at all: the admin and people given
     * "Theme + Advanced" or "Everything". Everyone else never learns it exists.
     */
    advancedAllowed: Boolean = true,
    /** The looks from the catalogue, already filtered to the ones this account may use. */
    catalogThemes: List<DownloadedTheme>,
    /** True while the catalogue is being fetched. */
    refreshing: Boolean,
    catalogueProblem: String?,
    onRefresh: () -> Unit,
    onPick: (ThemePreset) -> Unit,
    onBack: () -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val close = { visible.targetState = false }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState && !visible.targetState) onBack()
    }
    BackHandler(onBack = close)

    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(180)) +
            slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) {
                it / 4
            },
        // Leaving is quicker than arriving on purpose: going back should feel like being let
        // go of, not like waiting for the screen to finish withdrawing.
        exit = fadeOut(tween(120)) +
            slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { it / 6 },
    ) {
        Box(Modifier.fillMaxSize()) {
            Backdrop()
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = close) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Themes", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }

                // The same page tabs the admin panel uses, so the two screens read the same way.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Screen)
                        .padding(top = 2.dp, bottom = 6.dp),
                ) {
                    ThemeTab(label = "Looks", active = true, enabled = true) {}
                    // Advanced is not offered to an ordinary account at all: not greyed, not
                    // explained, simply absent, as the owner asked.
                    if (advancedAllowed) {
                        ThemeTab(label = "Advanced", active = false, enabled = advancedOn, onClick = onAdvanced)
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = Spacing.Screen, end = Spacing.Screen, top = 8.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Card),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Card),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    item(span = { GridItemSpan(2) }) {
                        Column(Modifier.padding(bottom = 4.dp)) {
                            Text(
                                if (access.theme) {
                                    "Pick a look. Each card shows what that look does to the app."
                                } else {
                                    "Light and Dark are for everyone. Ask the admin for access to the picture looks."
                                },
                                color = TextSoft,
                                fontSize = 13.sp,
                            )
                            // Backgrounds and card pictures, kept apart, on their own page.
                            // Only an account with Advanced access sees this entry.
                            if (advancedAllowed) run {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (advancedInUse) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                    },
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable(enabled = advancedOn, onClick = onAdvanced),
                            ) {
                                Row(
                                    Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Advanced", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                            if (advancedInUse) {
                                                Spacer(Modifier.width(8.dp))
                                                Box(
                                                    Modifier
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                                ) {
                                                    Text(
                                                        "In use",
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            if (advancedOn) {
                                                "On · backgrounds, your own themes and bundles."
                                            } else {
                                                "Off · the theme's own background and cards, " +
                                                    "your photos and bundles are set aside."
                                            },
                                            color = TextSoft,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    Switch(
                                        checked = advancedOn,
                                        onCheckedChange = onAdvancedChange,
                                    )
                                    if (advancedOn) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Open Advanced",
                                            tint = TextSoft,
                                            modifier = Modifier
                                                .padding(start = 2.dp)
                                                .clickable(onClick = onAdvanced),
                                        )
                                    }
                                }
                            }
                            }

                            Spacer(Modifier.height(10.dp))
                            // New looks arrive here without a new app build.
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("New themes", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            catalogueProblem
                                                ?: if (refreshing) {
                                                    "Checking for new themes…"
                                                } else {
                                                    "Look for new looks. Nothing leaves your phone but the request."
                                                },
                                            color = if (catalogueProblem != null) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                TextSoft
                                            },
                                            fontSize = 12.sp,
                                        )
                                    }
                                    TextButton(onClick = onRefresh, enabled = !refreshing) {
                                        Text(if (refreshing) "Checking…" else "Check")
                                    }
                                }
                            }
                        }
                    }
                    item(span = { GridItemSpan(2) }) { SectionHeading("Light and Dark") }
                    items(ThemePreset.all.filter { !it.special }) { preset ->
                        ThemeOption(
                            preset = preset,
                            selected = preset == theme,
                            locked = false,
                            onClick = {
                                onPick(preset)
                                close()
                            },
                        )
                    }
                    item(span = { GridItemSpan(2) }) { SectionHeading("Picture looks") }
                    items(ThemePreset.all.filter { it.special }) { preset ->
                        ThemeOption(
                            preset = preset,
                            selected = preset == theme,
                            locked = lockedFor(preset, access),
                            onClick = {
                                if (!lockedFor(preset, access)) {
                                    onPick(preset)
                                    close()
                                }
                            },
                        )
                    }
                    if (catalogThemes.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) { SectionHeading("Downloaded") }
                    }
                    items(catalogThemes, key = { "store:" + it.id }) { downloaded ->
                        val preset = downloaded.toPreset()
                        ThemeOption(
                            preset = preset,
                            picture = themePicture(downloaded),
                            selected = preset.label == theme.label && preset.background == theme.background,
                            locked = lockedFor(preset, access),
                            badge = "New",
                            onClick = {
                                if (access.theme) {
                                    onPick(preset)
                                    close()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(
    preset: ThemePreset,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    /** A downloaded picture, when the look came from the catalogue. */
    picture: Bitmap? = null,
    badge: String? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    val ring = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    }
    Column(
        Modifier
            .clip(shape)
            .background(preset.surface)
            .border(if (selected) 2.dp else 1.dp, ring, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        // The preview: the picture or gradient, a mini vault card and a mini bottom bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(preset.background),
        ) {
            val style = preset.backdrop
            if (picture == null && style is BackdropStyle.Picture) {
                // A look that ships with its own artwork: show that, so the preview looks
                // like the theme rather than a flat square.
                Image(
                    painter = painterResource(style.image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(preset.background.copy(alpha = style.scrim * 0.6f)),
                )
            }
            if (picture != null) {
                Image(
                    bitmap = picture.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(preset.background.copy(alpha = (style as? BackdropStyle.Remote)?.scrim ?: 0.4f)),
                )
            } else if (style is BackdropStyle.Remote) {
                Box(Modifier.fillMaxSize().background(preset.background))
            } else if (style is BackdropStyle.Picture) {
                Image(
                    painter = painterResource(style.image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(preset.background.copy(alpha = style.scrim)),
                )
            }
            Column(Modifier.padding(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(preset.gradient ?: listOf(preset.primary, preset.secondary)),
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(2) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (preset.glass) preset.surface.copy(alpha = 0.72f) else preset.surface,
                                )
                                .border(1.dp, preset.outline, RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .fillMaxWidth(0.8f)
                    .height(18.dp)
                    .clip(CircleShape)
                    .background(preset.surface)
                    .border(1.dp, preset.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 26.dp, height = 12.dp)
                        .clip(CircleShape)
                        .background(preset.primary),
                )
            }
            if (badge != null && !locked) {
                Text(
                    text = badge,
                    color = preset.onPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(preset.primary)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (locked) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = "Needs special access",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    preset.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = preset.onBackground,
                    maxLines = 1,
                )
                Text(
                    text = when {
                        locked -> "Ask the admin for access"
                        badge != null -> "New · special"
                        preset.special -> "Special theme"
                        else -> "Everyone"
                    },
                    color = preset.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            if (selected) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(preset.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = preset.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/** A quiet heading between blocks on the Theme page. */
@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSoft,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** One page tab, drawn exactly like the ones in the admin panel. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.ThemeTab(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 1f else 0.5f)
        },
        border = BorderStroke(
            1.dp,
            if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            },
        ),
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f)
            },
            modifier = Modifier.padding(vertical = 9.dp),
        )
    }
}
