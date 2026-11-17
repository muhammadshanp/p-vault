package com.dudeapp.vault

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.launch

/**
 * Bundles: named sets of themes this phone made. A bundle only points at its themes, so one
 * theme can sit in several bundles, and deleting a bundle never deletes a theme.
 */
@Composable
fun BundlesSection(
    /** True when this account may build and change bundles. */
    canEdit: Boolean,
    /** Called when a theme from a bundle is picked, so the page can close. */
    onApplied: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { BundleStore(context) }
    val scope = rememberCoroutineScope()
    val (bundles, themes) = Bundles.current.value
    var naming by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ThemeBundle?>(null) }
    var deleting by remember { mutableStateOf<ThemeBundle?>(null) }
    var openBundle by remember { mutableStateOf<ThemeBundle?>(null) }
    var building by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<String?>(null) }

    if (building != null) {
        ThemeBuilderScreen(
            bundleId = building!!,
            theme = themes.firstOrNull { it.id == editing },
            onDone = {
                Bundles.publish(store)
                building = null
                editing = null
            },
            onBack = {
                building = null
                editing = null
            },
        )
        return
    }

    val current = openBundle?.let { open -> bundles.firstOrNull { it.id == open.id } }
    if (current != null) {
        BundleDetail(
            bundle = current,
            themes = themes,
            canEdit = canEdit,
            onBack = { openBundle = null },
            onApply = { theme ->
                Bundles.applied.value = theme
                Bundles.publish(store)
                onApplied()
            },
            onNewTheme = {
                editing = null
                building = current.id
            },
            onEditTheme = { theme ->
                editing = theme.id
                building = current.id
            },
            onRemove = { theme ->
                store.removeFromBundle(current.id, theme.id)
                Bundles.publish(store)
            },
            onDeleteTheme = { theme ->
                store.deleteTheme(theme.id)
                Bundles.publish(store)
            },
            onAddExisting = { themeId ->
                store.addToBundle(current.id, themeId)
                Bundles.publish(store)
            },
        )
        return
    }

    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), shape)
            .padding(16.dp),
    ) {
        Text("Your bundles", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "A bundle is a named set of your own themes. One theme can sit in more than one bundle.",
            color = TextSoft,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        if (canEdit) {
            Button(onClick = { naming = true; draft = "" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New bundle")
            }
            Spacer(Modifier.height(10.dp))
        }
        if (bundles.isEmpty()) {
            Text(
                "No bundles yet. Make one, then build the themes inside it.",
                color = TextSoft,
                fontSize = 13.sp,
            )
        }
        bundles.forEach { bundle ->
            BundleRow(
                bundle = bundle,
                count = bundle.themeIds.count { id -> themes.any { it.id == id } },
                canEdit = canEdit,
                onOpen = { openBundle = bundle },
                onRename = { renaming = bundle; draft = bundle.name },
                onDelete = { deleting = bundle },
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (naming) {
        NameDialog(
            title = "New bundle",
            value = draft,
            onChange = { draft = it },
            onConfirm = {
                store.addBundle(draft)
                Bundles.publish(store)
                naming = false
            },
            onDismiss = { naming = false },
        )
    }
    renaming?.let { bundle ->
        NameDialog(
            title = "Rename bundle",
            value = draft,
            onChange = { draft = it },
            onConfirm = {
                store.renameBundle(bundle.id, draft)
                Bundles.publish(store)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { bundle ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${bundle.name}?") },
            text = { Text("The bundle goes; the themes inside it stay, ready for another bundle.") },
            confirmButton = {
                TextButton(onClick = {
                    store.removeBundle(bundle.id)
                    Bundles.publish(store)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun BundleRow(
    bundle: ThemeBundle,
    count: Int,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), shape)
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(bundle.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (count == 1) "1 theme" else "$count themes",
                color = TextSoft,
                fontSize = 12.sp,
            )
        }
        if (canEdit) {
            IconButton(onClick = onRename) {
                Icon(Icons.Outlined.Edit, contentDescription = "Rename", tint = TextSoft)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = TextSoft)
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    value: String,
    onChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { onChange(it.take(40)) },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = value.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BundleDetail(
    bundle: ThemeBundle,
    themes: List<OwnTheme>,
    canEdit: Boolean,
    onBack: () -> Unit,
    onApply: (OwnTheme) -> Unit,
    onNewTheme: () -> Unit,
    onEditTheme: (OwnTheme) -> Unit,
    onRemove: (OwnTheme) -> Unit,
    onDeleteTheme: (OwnTheme) -> Unit,
    onAddExisting: (String) -> Unit,
) {
    val mine = bundle.themeIds.mapNotNull { id -> themes.firstOrNull { it.id == id } }
    val bundles = Bundles.current.value.first
    val outside = themes.filterNot { it.id in bundle.themeIds }
    var removing by remember { mutableStateOf<OwnTheme?>(null) }
    var adding by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Backdrop()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(bundle.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                if (canEdit) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onNewTheme, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New theme", maxLines = 1)
                            }
                            if (outside.isNotEmpty()) {
                                OutlinedButton(onClick = { adding = true }, modifier = Modifier.weight(1f)) {
                                    Text("Add existing", maxLines = 1)
                                }
                            }
                        }
                    }
                }
                if (mine.isEmpty()) {
                    item {
                        Text(
                            "Nothing in this bundle yet. Make a theme: a background photo and a photo for each card.",
                            color = TextSoft,
                            fontSize = 13.sp,
                        )
                    }
                }
                items(mine, key = { it.id }) { theme ->
                    ThemeRow(
                        theme = theme,
                        applied = Bundles.applied.value?.id == theme.id,
                        canEdit = canEdit,
                        onApply = { onApply(theme) },
                        onEdit = { onEditTheme(theme) },
                        onRemove = { removing = theme },
                    )
                }
            }
        }
    }

    removing?.let { theme ->
        val shared = bundles.count { theme.id in it.themeIds }
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${theme.name}?") },
            text = {
                Text(
                    if (shared > 1) {
                        "It is in $shared bundles. Removing it here leaves it in the others."
                    } else {
                        "It leaves this bundle. The theme itself is kept, so you can add it back or delete it for good."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { onRemove(theme); removing = null }) { Text("Remove") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onDeleteTheme(theme); removing = null }) { Text("Delete for good") }
                    TextButton(onClick = { removing = null }) { Text("Keep") }
                }
            },
        )
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add a theme you made") },
            text = {
                Column {
                    outside.forEach { theme ->
                        Text(
                            theme.name,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAddExisting(theme.id); adding = false }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { adding = false }) { Text("Done") } },
        )
    }
}

/** One theme inside a bundle: a preview with its background and its card photos. */
@Composable
private fun ThemeRow(
    theme: OwnTheme,
    applied: Boolean,
    canEdit: Boolean,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
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
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemeSwatch(theme, Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(theme.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${theme.cards.size}/5 cards · " + if (theme.background == null) "colour" else "background",
                    color = TextSoft,
                    fontSize = 11.sp,
                )
            }
            if (applied) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "In use", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        if (canEdit) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Change", fontSize = 12.sp) }
                TextButton(onClick = onRemove) { Text("Remove", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(theme: OwnTheme, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val background: androidx.compose.ui.graphics.ImageBitmap? = remember(theme.background) {
        theme.background?.let { file -> readThumbnail(file) }
    }
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), shape),
    ) {
        if (background != null) {
            Image(
                bitmap = background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // A hint of the card photos, as small squares along the bottom.
        Row(
            Modifier.align(Alignment.BottomCenter).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CardPicture.entries.take(5).forEach { kind ->
                val file = theme.cards[kind]
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (file != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

/**
 * The builder: a name, a background photo, and a photo for each card. Everything is optional
 * except the name, so a theme can be just a background or just card photos.
 */
@Composable
fun ThemeBuilderScreen(
    bundleId: String,
    theme: OwnTheme?,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { BundleStore(context) }
    val scope = rememberCoroutineScope()
    val id = remember { theme?.id ?: store.newThemeId() }
    var name by remember { mutableStateOf(theme?.name.orEmpty()) }
    var background by remember { mutableStateOf(theme?.background) }
    var cards by remember { mutableStateOf(theme?.cards.orEmpty()) }
    var picking by remember { mutableStateOf<CardPicture?>(null) }
    var pickBackground by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val kind = picking
        val forBackground = pickBackground
        picking = null
        pickBackground = false
        if (uri == null || !forBackground && kind == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val label = if (forBackground) "background" else kind!!.name.lowercase()
            val saved = store.savePicture(uri, "${id}_$label.img")
            if (saved != null) {
                if (forBackground) background = saved else cards = cards + (kind!! to saved)
            }
            busy = false
        }
    }

    fun pick(kind: CardPicture?, forBackground: Boolean) {
        picking = kind
        pickBackground = forBackground
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize()) {
        Backdrop()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(if (theme == null) "New theme" else "Change theme", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("Name this theme") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    PickerRow(
                        title = "Background",
                        detail = if (background == null) "The look's own colour" else "Your photo",
                        action = if (background == null) "Choose" else "Change",
                        onAction = { pick(null, true) },
                        onClear = background?.let { { background = null } },
                    )
                }
                items(CardPicture.entries) { kind ->
                    PickerRow(
                        title = cardPictureName(kind),
                        detail = if (cards[kind] == null) "The look's own picture" else "Your photo",
                        action = if (cards[kind] == null) "Choose" else "Change",
                        onAction = { pick(kind, false) },
                        onClear = cards[kind]?.let { { cards = cards - kind } },
                    )
                }
                item {
                    Button(
                        onClick = {
                            val save = OwnTheme(id = id, name = name.trim(), background = background, cards = cards)
                            store.saveTheme(save)
                            store.addToBundle(bundleId, id)
                            Bundles.applied.value = save
                            Bundles.publish(store)
                            onDone()
                        },
                        enabled = name.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (busy) "Saving…" else "Save and use")
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
    onClear: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), shape)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = TextSoft, fontSize = 11.sp)
        }
        if (onClear != null) {
            TextButton(onClick = onClear) { Text("Clear", fontSize = 12.sp) }
        }
        TextButton(onClick = onAction) { Text(action, fontSize = 12.sp) }
    }
}

/** The look of a theme made on this phone, so the whole app can show it. */
fun OwnTheme.toPreset(base: ThemePreset = ThemePreset.NIGHTFALL): ThemePreset = base.copy(
    label = name,
    backdrop = background?.let { BackdropStyle.LocalFile(it, scrim = 0.42f) } ?: base.backdrop,
    glass = false,
    heroArt = false,
    adminOnly = false,
    // The card photos are files, not resources, so they are provided separately.
    cardPictures = emptyMap(),
    catalogId = null,
)

/** A small copy of a photo, for the little preview in a bundle row. */
private fun readThumbnail(file: File): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
    val sample = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / 120)
    val bitmap = android.graphics.BitmapFactory.decodeFile(
        file.absolutePath,
        android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
    )
    bitmap?.asImageBitmap()
}.getOrNull()
