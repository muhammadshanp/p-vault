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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
 * The look of the app: Light and Dark for everyone, and the picture looks for people with
 * special access. Each card is a small preview of that look.
 */
@Composable
fun ThemesScreen(
    theme: ThemePreset,
    specialAccess: Boolean,
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
                        }
                    }
                    items(ThemePreset.entries) { preset ->
                        ThemeOption(
                            preset = preset,
                            selected = preset == theme,
                            locked = preset.special && !specialAccess,
                            onClick = {
                                if (!(preset.special && !specialAccess)) {
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
                                .background(preset.surface)
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
                        locked -> "Special access"
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
