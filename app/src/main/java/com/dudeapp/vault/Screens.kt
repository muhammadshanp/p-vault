package com.dudeapp.vault

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.positionChanged
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(
    onDismiss: () -> Unit,
    onPickPhotos: () -> Unit,
    onPickPdf: () -> Unit,
    onScan: () -> Unit,
    onCamera: () -> Unit,
    onTextNote: () -> Unit,
    onReel: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                "Add to your vault",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text("Everything stays on this device.", color = TextSoft, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            AddRow(Icons.Outlined.Image, Accent.Photo, "Photos", "Import images from your gallery", onPickPhotos)
            AddRow(Icons.Outlined.PhotoCamera, Accent.Photo, "Take a photo", "Straight into the vault, not the gallery", onCamera)
            AddRow(Icons.Outlined.PictureAsPdf, Accent.Pdf, "PDF documents", "Import PDF files", onPickPdf)
            AddRow(Icons.Outlined.DocumentScanner, Accent.Pdf, "Scan a document", "Photograph pages and save them as a PDF", onScan)
            AddRow(Icons.AutoMirrored.Outlined.StickyNote2, Accent.Note, "Text note", "Write something down", onTextNote)
            AddRow(Icons.Outlined.PlayCircle, Accent.Reel, "Reel link", "Save an Instagram Reel", onReel)
        }
    }
}

@Composable
private fun AddRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = icon, tint = tint, size = 48.dp, iconSize = 24.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSoft, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSoft)
    }
}

@Composable
fun NoteDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf(TextFieldValue("")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("New text note") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Note") },
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 130.dp),
                )
                TextButton(onClick = {
                    val next = withNewTickBox(body.text)
                    body = TextFieldValue(next, TextRange(next.length))
                }) {
                    Icon(Icons.Outlined.CheckBox, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add a tick box")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, body.text) },
                enabled = title.isNotBlank() || body.text.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** The star colour for favourites. */
internal val FavouriteGold = Color(0xFFF5B301)

@Composable
fun DetailScreen(
    item: VaultItem,
    store: VaultStore,
    onBack: () -> Unit,
    onChanged: (VaultItem) -> Unit,
    onDeleted: () -> Unit,
    onExternal: () -> Unit = {},
    /** The photos to swipe through, in the order the Images tab shows them. */
    photos: List<VaultItem> = emptyList(),
) {
    var current by remember(item) { mutableStateOf(item) }
    var menuOpen by remember { mutableStateOf(false) }

    // Swiping left and right moves through the photos. The pager is only used when the
    // opened photo is in the list; notes and PDFs keep their single viewer.
    val startPage = remember { photos.indexOfFirst { it.file == item.file } }
    val swipeable = item.type == ItemType.IMAGE && startPage >= 0
    val pagerState = rememberPagerState(initialPage = startPage.coerceAtLeast(0)) { photos.size }
    val latestPhotos by rememberUpdatedState(photos)
    // The page that `current` belongs to. It shows `current` itself, so a rename or a
    // star appears at once, before the list behind it has been reloaded.
    var currentPage by remember { mutableIntStateOf(startPage) }
    if (swipeable) {
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                val photo = latestPhotos.getOrNull(page)
                if (photo != null && page != currentPage) {
                    current = photo
                    currentPage = page
                }
            }
        }
    }
    var saving by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    fun saved(ok: Boolean, where: String) {
        saving = false
        Toast.makeText(
            context,
            if (ok) "Saved to $where" else "Could not save the file",
            Toast.LENGTH_LONG,
        ).show()
    }

    // Older Android has no shared Downloads collection for apps, so ask where to save.
    val savePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { target ->
        if (target == null) {
            saving = false
        } else {
            scope.launch { saved(saveToPicked(context, current, target), "the chosen folder") }
        }
    }

    fun download() {
        if (saving) return
        saving = true
        if (canSaveToDownloads()) {
            scope.launch { saved(saveToDownloads(context, current), "Downloads/P Vault") }
        } else {
            onExternal()
            runCatching { savePicker.launch(exportName(current.title, current.file.extension)) }
                .onFailure { saved(false, "") }
        }
    }

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
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = current.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val target = current
                        scope.launch {
                            val updated = withContext(Dispatchers.IO) {
                                store.setFavourite(target, !target.favourite)
                            }
                            if (current.file == target.file) current = updated
                            onChanged(updated)
                        }
                    },
                ) {
                    if (current.favourite) {
                        Icon(Icons.Default.Star, contentDescription = "Remove from favourites", tint = FavouriteGold)
                    } else {
                        Icon(Icons.Outlined.StarBorder, contentDescription = "Add to favourites")
                    }
                }
                IconButton(
                    onClick = {
                        onExternal()
                        if (!shareVaultFile(context, current)) {
                            Toast.makeText(context, "Could not share the file", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
                IconButton(onClick = { download() }, enabled = !saving) {
                    Icon(Icons.Default.Download, contentDescription = "Download to phone")
                }
                // Rename and Delete sit behind the menu so the title keeps its room.
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                renameOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuOpen = false
                                deleteOpen = true
                            },
                        )
                    }
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (current.type) {
                    ItemType.IMAGE -> if (swipeable) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            pageSpacing = 16.dp,
                            key = { page -> page },
                        ) { page ->
                            val photo = if (page == currentPage) current else photos.getOrNull(page)
                            if (photo != null) {
                                ImageViewer(photo.file, active = page == pagerState.currentPage)
                            }
                        }
                    } else {
                        ImageViewer(current.file)
                    }
                    ItemType.PDF -> PdfViewer(current.file)
                    ItemType.TEXT -> TextViewer(current, store)
                }
            }
        }
    }

    if (renameOpen) {
        RenameDialog(
            currentTitle = current.title,
            onDismiss = { renameOpen = false },
            onConfirm = { newTitle ->
                renameOpen = false
                scope.launch {
                    val updated = withContext(Dispatchers.IO) { store.rename(current, newTitle) }
                    if (updated != null) {
                        current = updated
                        onChanged(updated)
                    } else {
                        Toast.makeText(context, "That name is already used", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    if (deleteOpen) {
        DeleteDialog(
            title = current.title,
            onDismiss = { deleteOpen = false },
            onConfirm = {
                deleteOpen = false
                scope.launch {
                    withContext(Dispatchers.IO) { store.delete(current) }
                    onDeleted()
                }
            },
        )
    }
}

@Composable
private fun RenameDialog(currentTitle: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim()) },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Delete \"$title\"?") },
        text = { Text("This removes the file from your vault. It can't be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ImageViewer(file: File, active: Boolean = true) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // A photo that was swiped away comes back at its normal size.
    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        // Leave the edges to Android so the system back gesture is not eaten by pan/zoom.
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp)
                .pointerInput(Unit) {
                    // Two fingers zoom; one finger pans only while zoomed in. A one-finger
                    // drag at normal size is left alone, so it swipes to the next photo.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            if (fingers >= 2 || scale > 1.01f) {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                                offset = if (scale <= 1.01f) Offset.Zero else offset + event.calculatePan()
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        )
    }
}

@Composable
private fun PdfViewer(file: File) {
    var pageCount by remember(file) { mutableIntStateOf(0) }
    var loading by remember(file) { mutableStateOf(true) }
    var failed by remember(file) { mutableStateOf(false) }

    LaunchedEffect(file) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
                }
            }
        }
        result.onSuccess { pageCount = it }.onFailure { failed = true }
        loading = false
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        failed -> CenterMessage("This PDF can't be opened.")

        pageCount == 0 -> CenterMessage("This PDF has no pages.")

        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(pageCount) { index -> PdfPage(file, index) }
        }
    }
}

@Composable
private fun CenterMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = TextSoft, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PdfPage(file: File, index: Int) {
    val context = LocalContext.current

    // shortcut: one renderer per page; fine for typical documents, pool it for 100+ page PDFs.
    var bitmap by remember(file, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file, index) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        renderer.openPage(index).use { page ->
                            val targetWidth = (context.resources.displayMetrics.widthPixels - 40).coerceAtLeast(480)
                            val targetHeight = (targetWidth.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                            Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { output ->
                                output.eraseColor(android.graphics.Color.WHITE)
                                page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }.getOrNull()
        }
    }

    val pageBitmap = bitmap
    if (pageBitmap != null) {
        Image(
            bitmap = pageBitmap.asImageBitmap(),
            contentDescription = "Page ${index + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Rendering page ${index + 1}…", color = TextSoft, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TextViewer(item: VaultItem, store: VaultStore) {
    var text by remember(item.file.path) { mutableStateOf("") }
    var editing by remember(item.file.path) { mutableStateOf(false) }
    var draft by remember(item.file.path) { mutableStateOf(TextFieldValue("")) }
    var newItem by remember(item.file.path) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(item.file.path) {
        text = withContext(Dispatchers.IO) {
            runCatching { store.readText(item) }.getOrDefault("")
        }
    }

    /** Ticking a box or adding an item is saved straight away. */
    fun change(updated: String) {
        if (updated == text) return
        text = updated
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { store.saveText(item, updated) } }
        }
    }

    fun addItem() {
        change(addChecklistItem(text, newItem))
        newItem = ""
    }

    Column(
        Modifier
            .fillMaxSize()
            // Keeps the text and the "add item" field above the keyboard.
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!editing) {
                TextButton(onClick = {
                    draft = TextFieldValue(text, TextRange(text.length))
                    editing = true
                }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
            } else {
                TextButton(onClick = {
                    val next = withNewTickBox(draft.text)
                    draft = TextFieldValue(next, TextRange(next.length))
                }) {
                    Icon(Icons.Outlined.CheckBox, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tick box")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { editing = false }) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val saved = draft.text
                    text = saved
                    editing = false
                    scope.launch {
                        withContext(Dispatchers.IO) { store.saveText(item, saved) }
                    }
                }) { Text("Save") }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val lines = remember(text) { parseNoteLines(text) }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (text.isBlank()) {
                    Text(
                        text = "(Empty note)",
                        color = TextSoft,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                // Ordinary lines are shown together as selectable text; each tick-box
                // line becomes a row that can be tapped.
                var index = 0
                while (index < lines.size) {
                    val line = lines[index]
                    if (line.checked == null) {
                        val start = index
                        while (index < lines.size && lines[index].checked == null) index += 1
                        val block = lines.subList(start, index).joinToString("\n") { it.text }
                        if (block.isNotBlank()) {
                            SelectionContainer {
                                Text(
                                    text = block,
                                    style = MaterialTheme.typography.bodyLarge,
                                    lineHeight = 24.sp,
                                )
                            }
                        } else if (start > 0 && index < lines.size) {
                            Spacer(Modifier.height(10.dp))
                        }
                    } else {
                        val lineIndex = index
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { change(toggleNoteLine(text, lineIndex)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = line.checked,
                                onCheckedChange = { change(toggleNoteLine(text, lineIndex)) },
                            )
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (line.checked) TextSoft else Color.Unspecified,
                                textDecoration = if (line.checked) TextDecoration.LineThrough else null,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { change(removeNoteLine(text, lineIndex)) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove item",
                                    tint = TextSoft,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        index += 1
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newItem,
                onValueChange = { newItem = it },
                placeholder = { Text("Add a checklist item") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.CheckBox, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { addItem() }, enabled = newItem.isNotBlank()) {
                        Icon(Icons.Default.Add, contentDescription = "Add item")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (newItem.isNotBlank()) addItem() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
