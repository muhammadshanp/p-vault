package com.dudeapp.vault

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

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
    /** The photos this phone has chosen, by card. */
    photos: Map<CardPicture, File> = emptyMap(),
    /** A photo was picked for one card; null when the picker was closed. */
    onPhoto: (CardPicture, Uri?) -> Unit = { _, _ -> },
    onClearPhoto: (CardPicture) -> Unit = {},
    /** True while the bundles are open; the page shows them instead of the looks. */
    bundlesOpen: Boolean = false,
    onBundles: (Boolean) -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // The card the picker is open for, if any.
    var picking by remember { mutableStateOf<CardPicture?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val kind = picking
        picking = null
        if (kind != null && uri != null) onPhoto(kind, uri)
    }
    fun pick(kind: CardPicture) {
        picking = kind
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
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
                            "A look sets the screen behind the app and the picture inside each card. " +
                                "Any picture works at any size: it fills the card, cropped, and the card keeps the " +
                                "same size as on every other theme, with its name and its count still on it.",
                            color = TextSoft,
                            fontSize = 13.sp,
                        )
                    }
                    item {
                        BundlesEntry(open = bundlesOpen, onClick = { onBundles(!bundlesOpen) })
                    }
                    if (bundlesOpen) {
                        // The bundles live here, on the Advanced page, not in the menu.
                        item {
                            BundlesSection(
                                canEdit = access.advanced,
                                onApplied = close,
                            )
                        }
                    }
                    item {
                        // One photo per card, chosen from this phone.
                        CardPhotoCard(
                            photos = photos,
                            onPick = ::pick,
                            onClear = onClearPhoto,
                            enabled = access.theme,
                        )
                    }
                    items(advanced, key = { it.label }) { preset ->
                        val locked = preset.special && (!access.theme || !access.allowsLook(preset.shareId()))
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
                        locked -> "Ask the admin for this look"
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
            // The same as on Home: the picture is behind the card's content, and the card
            // keeps its size and its labels.
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
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.55f to Color.Black.copy(alpha = 0.12f),
                            1f to Color.Black.copy(alpha = 0.5f),
                        ),
                    ),
            )
        }
    }
}

/**
 * One row per card, with the photo this phone chose for it. A chosen photo wins over the
 * picture the look puts in the card; the card keeps its own size and its own labels.
 */
@Composable
private fun CardPhotoCard(
    photos: Map<CardPicture, File>,
    onPick: (CardPicture) -> Unit,
    onClear: (CardPicture) -> Unit,
    enabled: Boolean,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), shape)
            .padding(16.dp),
    ) {
        Text("Photo for each card", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Pick a photo for any card. It sits behind the card's name, not in place of it, " +
                "and the card stays the same size.",
            color = TextSoft,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        CardPicture.entries.forEach { kind ->
            val file = photos[kind]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cardPictureName(kind), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (file == null) "No photo · the look decides" else "Your photo",
                        color = TextSoft,
                        fontSize = 11.sp,
                    )
                }
                if (file != null) {
                    TextButton(onClick = { onClear(kind) }, enabled = enabled) {
                        Text("Remove", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = { onPick(kind) }, enabled = enabled) {
                    Text(if (file == null) "Choose" else "Change", fontSize = 12.sp)
                }
            }
        }
    }
}

/** The name of a card, as it is shown on Home. */
internal fun cardPictureName(kind: CardPicture): String = when (kind) {
    CardPicture.HERO -> "The big card on Home"
    CardPicture.PHOTOS -> "Photos card"
    CardPicture.PDFS -> "PDFs card"
    CardPicture.NOTES -> "Notes and chats card"
    CardPicture.REELS -> "Reels card"
}

/** The way into the bundles, inside the Advanced page itself. */
@Composable
private fun BundlesEntry(open: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (open) "Hide your bundles" else "Your bundles",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Group the themes you make into named sets. A theme can sit in more than one bundle.",
                color = TextSoft,
                fontSize = 12.sp,
            )
        }
        Text(
            if (open) "▲" else "▼",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
        )
    }
}
