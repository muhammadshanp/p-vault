package com.dudeapp.vault

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A look is offered only when this account has both special access and, if needed, admin. */
private fun lockedFor(preset: ThemePreset, specialAccess: Boolean, admin: Boolean): Boolean =
    (preset.special && !specialAccess) || (preset.adminOnly && !admin)

/**
 * The look of the app: Light and Dark for everyone, and the picture looks for people with
 * special access. Each card is a small preview of that look.
 */
@Composable
fun ThemesScreen(
    theme: ThemePreset,
    specialAccess: Boolean,
    /** The owner's own sign-in; some looks and the Advanced page are admin-only. */
    admin: Boolean = false,
    /** Opens the advanced page, where background and card pictures are chosen. */
    onAdvanced: () -> Unit = {},
    /** The advanced look in use, if any, so the entry can say so. */
    advancedInUse: Boolean = false,
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
        enter = fadeIn(tween(200)) + slideInHorizontally(tween(240)) { it / 5 },
        exit = fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { it / 5 },
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

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    item(span = { GridItemSpan(2) }) {
                        Column(Modifier.padding(bottom = 4.dp)) {
                            Text(
                                "Light and Dark are for everyone. The picture themes are for people with special access.",
                                color = TextSoft,
                                fontSize = 13.sp,
                            )
                            if (!specialAccess) {
                                Text(
                                    "Ask the admin for special access to unlock the other looks.",
                                    color = TextSoft,
                                    fontSize = 13.sp,
                                )
                            }
                            // Backgrounds and card pictures, kept apart, on their own page.
                            if (admin) {
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
                                        .clickable(onClick = onAdvanced),
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
                                            "Background and card pictures, chosen separately.",
                                            color = TextSoft,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = TextSoft,
                                    )
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
                    items(ThemePreset.all) { preset ->
                        ThemeOption(
                            preset = preset,
                            selected = preset == theme,
                            locked = lockedFor(preset, specialAccess, admin),
                            onClick = {
                                if (!lockedFor(preset, specialAccess, admin)) {
                                    onPick(preset)
                                    close()
                                }
                            },
                        )
                    }
                    items(catalogThemes, key = { "store:" + it.id }) { downloaded ->
                        val preset = downloaded.toPreset()
                        ThemeOption(
                            preset = preset,
                            picture = themePicture(downloaded),
                            selected = preset.label == theme.label && preset.background == theme.background,
                            locked = lockedFor(preset, specialAccess, admin),
                            badge = "New",
                            onClick = {
                                if (specialAccess) {
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
                        locked && preset.adminOnly -> "Admin only"
                        locked -> "Special access"
                        badge != null -> "New · special"
                        preset.adminOnly -> "Admin theme"
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
