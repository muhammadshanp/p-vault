package com.dudeapp.vault

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Advanced themes: the background and the card pictures are two separate choices.
 *
 * A theme says what the screen behind everything looks like and, separately, what picture
 * each card carries (the big Home card and the four Library tiles). The page lists the
 * looks that are built that way.
 */
@Composable
fun AdvancedThemesScreen(
    theme: ThemePreset,
    access: MyAccess,
    onPick: (ThemePreset) -> Unit,
    onBack: () -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val close = { visible.targetState = false }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState && !visible.targetState) onBack()
    }
    BackHandler(onBack = close)

    val advanced = remember { ThemePreset.all.filter { it.cardPictures.isNotEmpty() } }

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
                    Text("Advanced", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }

                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    item {
                        Text(
                            "These looks set the background of the app and the picture inside each card separately. " +
                                "A card picture fills its card, cropped to fit, so any picture size works: the card stays the " +
                                "same size and its label stays readable.",
                            color = TextSoft,
                            fontSize = 13.sp,
                        )
                    }
                    items(advanced, key = { it.label }) { preset ->
                        val locked = preset.special && !access.theme
                        AdvancedOption(
                            preset = preset,
                            selected = preset.label == theme.label,
                            locked = locked,
                            onClick = {
                                if (!locked) {
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
private fun AdvancedOption(
    preset: ThemePreset,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .clip(shape)
            .background(preset.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        // The background picture, with the card pictures laid over it, so the two layers
        // can be judged together.
        val style = preset.backdrop
        Box(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(preset.background),
        ) {
            if (style is BackdropStyle.Picture) {
                Image(
                    painter = painterResource(style.image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to preset.background.copy(alpha = style.scrim),
                                0.6f to preset.background.copy(alpha = style.scrim * 0.8f),
                                1f to preset.background,
                            ),
                        ),
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CardPreview(preset, CardPicture.HERO, height = 74.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) {
                        CardPreview(preset, CardPicture.PHOTOS, height = 46.dp)
                    }
                    Box(Modifier.weight(1f)) {
                        CardPreview(preset, CardPicture.PDFS, height = 46.dp)
                    }
                    Box(Modifier.weight(1f)) {
                        CardPreview(preset, CardPicture.REELS, height = 46.dp)
                    }
                }
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
                        contentDescription = "Needs access",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    preset.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = preset.onBackground,
                )
                Text(
                    text = when {
                        locked -> "Ask the admin for access"
                        else -> "Background + card pictures"
                    },
                    color = preset.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            if (selected) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(preset.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = preset.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** One mini card with its own artwork, the way it appears on Home. */
@Composable
private fun CardPreview(preset: ThemePreset, kind: CardPicture, height: androidx.compose.ui.unit.Dp) {
    val picture = preset.cardPictures[kind]
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(preset.surface.copy(alpha = if (preset.glass) 0.75f else 1f))
            .border(1.dp, preset.outline, RoundedCornerShape(12.dp)),
    ) {
        if (picture != null) {
            // The same as on Home: the picture fills the card, a wash keeps the label clear.
            Image(
                painter = painterResource(picture),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.30f),
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.62f),
                        ),
                    ),
            )
        }
    }
}
