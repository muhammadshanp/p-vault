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
    /** An image was picked for the whole background. */
    onBackground: (Uri?) -> Unit = {},
    onClearBackground: () -> Unit = {},
    /** True while the bundles are open; the page shows them instead of the looks. */
    bundlesOpen: Boolean = false,
    onBundles: (Boolean) -> Unit = {},
    /** Opens the bundles and jumps to them. */
    onOpenBundles: () -> Unit = {},
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

    // The whole background: one image, picked from the same gallery.
    var backgroundPicking by remember { mutableStateOf(false) }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (backgroundPicking) {
            backgroundPicking = false
            onBackground(uri)
        }
    }
    fun pickBackground() {
        backgroundPicking = true
        backgroundPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val close = { visible.targetState = false }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState && !visible.targetState) onBack()
    }
    BackHandler(onBack = close)

    // Only the user's own themes for now: the built-in advanced looks are not listed.
    val own = Bundles.current.value.second
    val applied = Bundles.applied.value

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
                        BackgroundChoice(
                            image = CardPhotoFiles.background.value,
                            themeName = theme.label,
                            onPick = ::pickBackground,
                            onTheme = onClearBackground,
                            enabled = access.theme,
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
                    if (own.isEmpty()) {
                        item {
                            Text(
                                "No themes of your own yet. Open your bundles to make one: a background " +
                                    "image and a photo for each card.",
                                color = TextSoft,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.Button(onClick = onOpenBundles) {
                                Text("Open your bundles")
                            }
                        }
                    }
                    items(own, key = { it.id }) { mine ->
                        YourThemeRow(
                            theme = mine,
                            applied = applied?.id == mine.id,
                            onApply = {
                                Bundles.applied.value = mine
                                close()
                            },
                        )
                    }
                }
            }
        }
    }
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

/**
 * The whole background, as one choice: the theme's own backdrop, or one image of the user's.
 * While an image is chosen the theme supplies only the colours, so the two never fight.
 */
@Composable
private fun BackgroundChoice(
    image: File?,
    themeName: String,
    onPick: () -> Unit,
    onTheme: () -> Unit,
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
        Text("Background", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Choose one: the theme's own background, or an image of your own for the whole app.",
            color = TextSoft,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = if (image == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(enabled = enabled, onClick = onTheme)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("The theme's background", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (image == null) "$themeName · in use" else "$themeName · tap to use it",
                    color = TextSoft,
                    fontSize = 11.sp,
                )
            }
            if (image == null) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = if (image != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(enabled = enabled, onClick = onPick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("My image", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (image == null) "Pick a photo for the whole app" else "Your image · in use · tap to change",
                    color = TextSoft,
                    fontSize = 11.sp,
                )
            }
            if (image != null) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** One of the user's own themes, with the marks of what it carries. */
@Composable
private fun YourThemeRow(
    theme: OwnTheme,
    applied: Boolean,
    onApply: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (applied) 2.dp else 1.dp,
                color = if (applied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                shape = shape,
            )
            .clickable(onClick = onApply)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                theme.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "${theme.cards.size}/5 card photos · " +
                    if (theme.background == null) "theme background" else "own background",
                color = TextSoft,
                fontSize = 11.sp,
            )
        }
        if (applied) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "In use",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Text("Use", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
