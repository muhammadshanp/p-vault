package com.dudeapp.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AUTO_BACKUP_DELAY_MS = 20_000L

@Composable
fun VaultApp(
    sharedText: String?,
    onShareConsumed: () -> Unit,
    onExternal: () -> Unit,
    theme: ThemePreset,
    onThemeChange: (ThemePreset) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { VaultStore(context) }
    val profileStore = remember { ProfileStore(context) }
    val driveStore = remember { DriveBackupStore(context) }
    val scope = rememberCoroutineScope()
    val currentVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }

    var tab by remember { mutableStateOf(Tab.FILES) }
    var query by remember { mutableStateOf("") }
    var vaultItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var reels by remember { mutableStateOf<List<Reel>>(emptyList()) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var chats by remember { mutableStateOf<List<ChatThread>>(emptyList()) }
    var open by remember { mutableStateOf<VaultItem?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    var autoBackupTick by remember { mutableIntStateOf(0) }
    var autoBackupSkip by remember { mutableIntStateOf(0) }

    var chatView by remember { mutableStateOf(true) }
    var openChat by remember { mutableStateOf<ChatThread?>(null) }
    var chatDialogOpen by remember { mutableStateOf(false) }
    var chatsOpen by remember { mutableStateOf(false) }

    var reelFilter by remember { mutableStateOf<String?>(null) }
    var reelDialogOpen by remember { mutableStateOf(false) }
    var reelDraftUrl by remember { mutableStateOf("") }
    var reelDraftTitle by remember { mutableStateOf("") }
    var reelDraftCategory by remember { mutableStateOf<String?>(null) }
    var reelMenu by remember { mutableStateOf<Reel?>(null) }
    var categoriesOpen by remember { mutableStateOf(false) }

    var profile by remember { mutableStateOf(profileStore.load()) }
    var profileOpen by remember { mutableStateOf(false) }
    var chatAvatarTarget by remember { mutableStateOf<ChatThread?>(null) }
    var drive by remember { mutableStateOf(driveStore.state()) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupReminder by remember { mutableStateOf(false) }
    var reminderNeverBackedUp by remember { mutableStateOf(false) }
    var restorePrompt by remember { mutableStateOf(false) }
    val settingsStore = remember { SettingsStore(context) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var settingsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (sharedText != null) return@LaunchedEffect
        if (!drive.connected) return@LaunchedEffect
        if (driveStore.hasPending()) {
            autoBackupTick += 1
            return@LaunchedEffect
        }
        if (!driveStore.reminderDue(settings.reminderDays)) return@LaunchedEffect
        reminderNeverBackedUp = driveStore.lastBackupAt() == 0L
        backupReminder = true
    }

    LaunchedEffect(Unit) {
        val latest = checkForUpdate() ?: return@LaunchedEffect
        if (isNewerVersion(latest.version, currentVersion)) availableUpdate = latest
    }

    LaunchedEffect(Unit) {
        val appPrefs = context.getSharedPreferences("dude_app", Context.MODE_PRIVATE)
        if (appPrefs.getBoolean("restore_prompted", false)) return@LaunchedEffect
        val empty = withContext(Dispatchers.IO) {
            val existingProfile = profileStore.load()
            !store.hasUserContent() && existingProfile.name.isBlank() && existingProfile.avatar == null
        }
        if (empty) {
            restorePrompt = true
        } else {
            appPrefs.edit().putBoolean("restore_prompted", true).apply()
        }
    }

    LaunchedEffect(version) {
        val snapshot = withContext(Dispatchers.IO) {
            VaultSnapshot(
                files = store.list(),
                reels = store.listReels(),
                categories = store.categoryNames(),
                chats = store.listChats(),
            )
        }
        vaultItems = snapshot.files
        reels = snapshot.reels
        categories = snapshot.categories
        chats = snapshot.chats
        val filter = reelFilter
        if (filter != null && filter !in categories) reelFilter = null
    }

    fun refresh(notifyAuto: Boolean = true) {
        version += 1
        if (notifyAuto) {
            autoBackupTick += 1
            driveStore.markPending()
        }
    }

    fun dismissRestorePrompt() {
        context.getSharedPreferences("dude_app", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("restore_prompted", true)
            .apply()
        restorePrompt = false
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                withContext(Dispatchers.IO) { uris.forEach { store.addFile(it, "Image.jpg") } }
                refresh()
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                withContext(Dispatchers.IO) { uris.forEach { store.addFile(it, "Document.pdf") } }
                refresh()
            }
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { profileStore.saveAvatar(uri) }
                if (saved != null) {
                    profile = profile.copy(avatar = saved)
                    autoBackupTick += 1
                    driveStore.markPending()
                }
            }
        }
    }

    val chatAvatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val target = chatAvatarTarget
        chatAvatarTarget = null
        if (uri != null && target != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { store.setChatAvatar(target, uri) }
                if (saved == null) {
                    Toast.makeText(context, "Could not save that picture", Toast.LENGTH_SHORT).show()
                } else {
                    val updated = target.copy(avatar = saved)
                    if (openChat?.dir?.absolutePath == updated.dir.absolutePath) {
                        openChat = updated
                    }
                    refresh()
                }
            }
        }
    }

    val driveFolderPicker = rememberLauncherForActivityResult(
        PersistentOpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val connected = driveStore.connect(uri)
            drive = driveStore.state().copy(
                message = if (connected) "Google Drive folder connected" else "Could not use that folder",
            )
            if (connected && driveStore.hasPending()) autoBackupTick += 1
        }
    }

    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) restoreUri = uri
    }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { driveStore.exportTo(uri) }
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(sharedText) {
        val text = sharedText?.trim().orEmpty()
        if (text.isEmpty()) return@LaunchedEffect
        val url = Regex("""https?://\S+""").find(text)?.value ?: text
        val title = text.replace(url, "").replace("\n", " ").trim().take(60)
        reelDraftUrl = url
        reelDraftTitle = title.ifBlank { "Instagram Reel" }
        reelDraftCategory = store.pendingCategoryName()
        tab = Tab.REELS
        open = null
        openChat = null
        reelDialogOpen = true
        onShareConsumed()
    }

    fun launchPhotos() {
        onExternal()
        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun launchPdfs() {
        onExternal()
        pdfPicker.launch(arrayOf("application/pdf"))
    }

    fun pickChatAvatar(chat: ChatThread) {
        onExternal()
        chatAvatarTarget = chat
        chatAvatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun removeChatAvatar(chat: ChatThread) {
        scope.launch {
            val removed = withContext(Dispatchers.IO) { store.removeChatAvatar(chat) }
            if (!removed) {
                Toast.makeText(context, "Could not remove that picture", Toast.LENGTH_SHORT).show()
            } else {
                val current = openChat
                if (current?.dir?.absolutePath == chat.dir.absolutePath) {
                    openChat = current.copy(avatar = null)
                }
                refresh()
            }
        }
    }

    fun connectDrive() {
        if (drive.busy) return
        onExternal()
        driveFolderPicker.launch(null)
    }

    fun disconnectDrive() {
        val result = driveStore.disconnect()
        drive = driveStore.state().copy(message = result.message)
    }

    fun backupNow(showToast: Boolean = true) {
        if (drive.busy) return
        if (!drive.connected) {
            if (showToast) Toast.makeText(context, "Connect Google Drive first", Toast.LENGTH_SHORT).show()
            return
        }
        drive = drive.copy(busy = true, message = null)
        scope.launch {
            val result = withContext(Dispatchers.IO + NonCancellable) {
                driveStore.backupNow(settings.keepBackups)
            }
            drive = driveStore.state().copy(busy = false, message = result.message)
            if (showToast || !result.ok) {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun exportVault() {
        onExternal()
        exportPicker.launch(driveStore.suggestedName())
    }

    fun clearVaultData() {
        if (drive.busy) {
            Toast.makeText(context, "Wait for the current backup to finish", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            val cleared = withContext(Dispatchers.IO) {
                val ok = store.clearAll()
                if (ok) profileStore.clear()
                ok
            }
            if (!cleared) {
                Toast.makeText(context, "Could not clear the vault", Toast.LENGTH_LONG).show()
            }
            profile = profileStore.load()
            open = null
            openChat = null
            query = ""
            driveStore.clearPending()
            refresh(notifyAuto = false)
        }
    }

    LaunchedEffect(autoBackupTick) {
        if (autoBackupTick == 0) return@LaunchedEffect
        if (autoBackupSkip == autoBackupTick) {
            autoBackupSkip = 0
            return@LaunchedEffect
        }
        if (!drive.connected) return@LaunchedEffect
        if (!settings.autoBackup) return@LaunchedEffect
        if (!driveStore.hasPending()) return@LaunchedEffect
        val hasContent = withContext(Dispatchers.IO) {
            store.hasUserContent() || profile.name.isNotBlank() || profile.avatar != null
        }
        if (!hasContent) return@LaunchedEffect
        delay(AUTO_BACKUP_DELAY_MS)
        if (!drive.connected) return@LaunchedEffect
        if (!settings.autoBackup) return@LaunchedEffect
        if (!driveStore.hasPending()) return@LaunchedEffect
        val stillHasContent = withContext(Dispatchers.IO) {
            store.hasUserContent() || profile.name.isNotBlank() || profile.avatar != null
        }
        if (!stillHasContent) return@LaunchedEffect
        while (drive.busy) delay(2_000)
        if (!driveStore.hasPending()) return@LaunchedEffect
        backupNow(showToast = false)
    }

    fun restoreBackup(uri: Uri) {
        if (drive.busy) return
        autoBackupTick += 1
        autoBackupSkip = autoBackupTick
        drive = drive.copy(busy = true, message = null)
        scope.launch {
            val result = withContext(Dispatchers.IO + NonCancellable) { driveStore.restoreFromZip(uri) }
            profile = profileStore.load()
            open = null
            openChat = null
            refresh(notifyAuto = false)
            drive = driveStore.state().copy(busy = false, message = result.message)
            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        }
    }

    fun openReelDialog(initialUrl: String = "", initialTitle: String = "") {
        reelDraftUrl = initialUrl
        reelDraftTitle = initialTitle
        reelDraftCategory = null
        reelDialogOpen = true
    }

    fun sectionAdd() {
        when (tab) {
            Tab.FILES -> addOpen = true
            Tab.REELS -> openReelDialog()
            Tab.IMAGES -> launchPhotos()
            Tab.PDFS -> launchPdfs()
            Tab.NOTES -> if (chatView) chatDialogOpen = true else noteOpen = true
        }
    }

    val visibleFiles = vaultItems.filter { item ->
        val inTab = when (tab) {
            Tab.FILES -> true
            Tab.REELS -> false
            Tab.IMAGES -> item.type == ItemType.IMAGE
            Tab.PDFS -> item.type == ItemType.PDF
            Tab.NOTES -> !chatView && item.type == ItemType.TEXT
        }
        inTab && (query.isBlank() || item.title.contains(query.trim(), ignoreCase = true))
    }

    val visibleReels = reels.filter { reel ->
        tab == Tab.REELS &&
            (reelFilter == null || reel.category == reelFilter) &&
            (query.isBlank() || reel.title.contains(query.trim(), ignoreCase = true))
    }

    val visibleChats = chats.filter { chat ->
        query.isBlank() ||
            chat.name.contains(query.trim(), ignoreCase = true) ||
            chat.lastMessage.contains(query.trim(), ignoreCase = true)
    }

    val pendingName = categories.firstOrNull { it.equals("Pending", ignoreCase = true) }

    val detail = open
    val chatDetail = openChat
    if (settingsOpen) {
        SettingsScreen(
            theme = theme,
            settings = settings,
            onTheme = onThemeChange,
            onSettings = { next ->
                val autoWasOff = !settings.autoBackup
                settings = next
                settingsStore.save(next)
                if (autoWasOff && next.autoBackup && driveStore.hasPending()) {
                    autoBackupTick += 1
                }
            },
            onExport = { exportVault() },
            onImport = {
                onExternal()
                restorePicker.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream",
                    ),
                )
            },
            onClearData = { clearVaultData() },
            onBack = { settingsOpen = false },
        )
    } else if (chatDetail != null) {
        ChatScreen(
            chat = chatDetail,
            store = store,
            onPickPicture = { pickChatAvatar(chatDetail) },
            onRemovePicture = { removeChatAvatar(chatDetail) },
            onChanged = { refresh() },
            onBack = {
                openChat = null
                refresh(notifyAuto = false)
            },
        )
    } else if (detail != null) {
        DetailScreen(
            item = detail,
            store = store,
            onBack = { open = null },
            onChanged = { updated ->
                open = updated
                refresh()
            },
            onDeleted = {
                open = null
                refresh()
            },
        )
    } else {
        Box(Modifier.fillMaxSize()) {
            Backdrop()

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                    ProfileHeader(
                        profile = profile,
                        onProfile = { profileOpen = true },
                        onSettings = { settingsOpen = true },
                        query = query,
                        onQuery = { query = it },
                        placeholder = when (tab) {
                            Tab.FILES -> "Search files"
                            Tab.REELS -> "Search reels"
                            Tab.IMAGES -> "Search images"
                            Tab.PDFS -> "Search PDFs"
                            Tab.NOTES -> if (chatView) "Search chats" else "Search notes"
                        },
                    )

                    SectionHeader(
                        title = if (tab == Tab.NOTES && chatView) "Chats" else tab.label,
                        count = when {
                            tab == Tab.REELS -> visibleReels.size
                            tab == Tab.NOTES && chatView -> visibleChats.size
                            else -> visibleFiles.size
                        },
                        unit = when (tab) {
                            Tab.FILES -> "item"
                            Tab.REELS -> "reel"
                            Tab.IMAGES -> "image"
                            Tab.PDFS -> "PDF"
                            Tab.NOTES -> if (chatView) "chat" else "note"
                        },
                        onAdd = { sectionAdd() },
                        onManage = when {
                            tab == Tab.REELS -> { { categoriesOpen = true } }
                            tab == Tab.NOTES && chatView -> { { chatsOpen = true } }
                            else -> null
                        },
                        manageDescription = if (tab == Tab.REELS) "Edit categories" else "Manage chats",
                    )

                    if (tab == Tab.NOTES) {
                        TextModeChips(chatView = chatView, onChange = { chatView = it })
                        Spacer(Modifier.height(6.dp))
                    }

                    if (tab == Tab.REELS) {
                        CategoryChips(
                            categories = categories,
                            selected = reelFilter,
                            onSelect = { reelFilter = it },
                        )
                        Spacer(Modifier.height(6.dp))

                        if (visibleReels.isEmpty()) {
                            EmptyState(
                                tab = tab,
                                searching = query.isNotBlank(),
                                pendingView = pendingName != null && reelFilter == pendingName,
                                onAdd = { sectionAdd() },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                visibleReels.forEach { reel ->
                                    item(key = reel.file.absolutePath) {
                                        ReelCard(
                                            reel = reel,
                                            onClick = { openUrl(context, reel.url) },
                                            onMenu = { reelMenu = reel },
                                        )
                                    }
                                }
                            }
                        }
                    } else if (tab == Tab.NOTES && chatView) {
                        if (visibleChats.isEmpty()) {
                            EmptyState(
                                tab = tab,
                                searching = query.isNotBlank(),
                                chatView = true,
                                onAdd = { sectionAdd() },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                visibleChats.forEach { chat ->
                                    item(key = chat.dir.absolutePath) {
                                        ChatRow(chat = chat, onClick = { openChat = chat })
                                    }
                                }
                            }
                        }
                    } else if (visibleFiles.isEmpty()) {
                        EmptyState(
                            tab = tab,
                            searching = query.isNotBlank(),
                            onAdd = { sectionAdd() },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            items(visibleFiles, key = { it.file.absolutePath }) { item ->
                                VaultCard(item = item, onClick = { open = item })
                            }
                        }
                    }
            }

            BottomBar(
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            if (addOpen) {
                AddSheet(
                    onDismiss = { addOpen = false },
                    onPickPhotos = {
                        addOpen = false
                        launchPhotos()
                    },
                    onPickPdf = {
                        addOpen = false
                        launchPdfs()
                    },
                    onTextNote = {
                        addOpen = false
                        noteOpen = true
                    },
                    onReel = {
                        addOpen = false
                        openReelDialog()
                    },
                )
            }

            if (noteOpen) {
                NoteDialog(
                    onDismiss = { noteOpen = false },
                    onSave = { title, body ->
                        noteOpen = false
                        scope.launch {
                            withContext(Dispatchers.IO) { store.addText(title, body) }
                            refresh()
                        }
                    },
                )
            }

            if (chatDialogOpen) {
                AddChatDialog(
                    onDismiss = { chatDialogOpen = false },
                    onAdd = { name ->
                        chatDialogOpen = false
                        scope.launch {
                            val added = withContext(Dispatchers.IO) { store.addChat(name) }
                            if (added == null) {
                                Toast.makeText(context, "Could not create that chat", Toast.LENGTH_SHORT).show()
                            } else {
                                chatView = true
                                query = ""
                                refresh()
                            }
                        }
                    },
                )
            }

            if (chatsOpen) {
                ChatManagerSheet(
                    chats = chats,
                    onPickAvatar = { pickChatAvatar(it) },
                    onRemoveAvatar = { removeChatAvatar(it) },
                    onRename = { chat, name ->
                        scope.launch {
                            val renamed = withContext(Dispatchers.IO) { store.renameChat(chat, name) }
                            if (!renamed) {
                                Toast.makeText(context, "That name is already used", Toast.LENGTH_SHORT).show()
                            }
                            refresh()
                        }
                    },
                    onDelete = { chat ->
                        scope.launch {
                            val deleted = withContext(Dispatchers.IO) { store.deleteChat(chat) }
                            if (!deleted) {
                                Toast.makeText(context, "Could not delete that chat", Toast.LENGTH_SHORT).show()
                            }
                            refresh()
                        }
                    },
                    onDismiss = { chatsOpen = false },
                )
            }

            if (profileOpen) {
                ProfileSheet(
                    name = profile.name,
                    avatar = profile.avatar,
                    drive = drive,
                    onPickPhoto = {
                        onExternal()
                        avatarPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onConnectDrive = { connectDrive() },
                    onDisconnectDrive = { disconnectDrive() },
                    onBackupNow = { name ->
                        val clean = name.trim()
                        if (clean.isNotBlank() && clean != profile.name) {
                            profileStore.setName(clean)
                            profile = profileStore.load()
                        }
                        backupNow()
                    },
                    onRestoreFile = {
                        onExternal()
                        restorePicker.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream",
                            ),
                        )
                    },
                    onSave = { name ->
                        val changed = name != profile.name
                        profileStore.setName(name)
                        profile = profileStore.load()
                        profileOpen = false
                        if (changed) {
                            autoBackupTick += 1
                            driveStore.markPending()
                        }
                    },
                    onDismiss = { profileOpen = false },
                )
            }

            if (reelDialogOpen) {
                AddReelDialog(
                    initialUrl = reelDraftUrl,
                    initialTitle = reelDraftTitle,
                    categories = categories,
                    initialCategory = reelDraftCategory
                        ?: reelFilter
                        ?: categories.firstOrNull { it.equals("Others", ignoreCase = true) }
                        ?: categories.firstOrNull(),
                    onDismiss = { reelDialogOpen = false },
                    onSave = { url, title, category ->
                        reelDialogOpen = false
                        scope.launch {
                            withContext(Dispatchers.IO) { store.addReel(url, title, category) }
                            tab = Tab.REELS
                            reelFilter = category
                            query = ""
                            refresh()
                        }
                    },
                )
            }

            val menuReel = reelMenu
            if (menuReel != null) {
                ReelMenuSheet(
                    reel = menuReel,
                    categories = categories,
                    onOpen = {
                        openUrl(context, menuReel.url)
                        reelMenu = null
                    },
                    onMove = { category ->
                        scope.launch {
                            withContext(Dispatchers.IO) { store.moveReel(menuReel, category) }
                            reelMenu = null
                            refresh()
                        }
                    },
                    onDelete = {
                        scope.launch {
                            withContext(Dispatchers.IO) { store.deleteReel(menuReel) }
                            reelMenu = null
                            refresh()
                        }
                    },
                    onDismiss = { reelMenu = null },
                )
            }

            if (categoriesOpen) {
                CategoryManagerSheet(
                    categories = categories,
                    pendingName = pendingName,
                    onAdd = { name ->
                        scope.launch {
                            val added = withContext(Dispatchers.IO) { store.addCategory(name) }
                            if (added == null) {
                                Toast.makeText(context, "That category already exists", Toast.LENGTH_SHORT).show()
                            }
                            refresh()
                        }
                    },
                    onRename = { oldName, newName ->
                        scope.launch {
                            val renamed = withContext(Dispatchers.IO) { store.renameCategory(oldName, newName) }
                            if (!renamed) {
                                Toast.makeText(context, "Could not rename that category", Toast.LENGTH_SHORT).show()
                            } else if (reelFilter == oldName) {
                                reelFilter = newName
                            }
                            refresh()
                        }
                    },
                    onDismiss = { categoriesOpen = false },
                )
            }

            if (backupReminder) {
                AlertDialog(
                    onDismissRequest = {
                        driveStore.markReminderShown()
                        backupReminder = false
                    },
                    title = { Text("Back up your vault") },
                    text = {
                        Text(
                            if (reminderNeverBackedUp) {
                                "You haven't backed up to Google Drive yet. Back up your vault now?"
                            } else {
                                val days = settings.reminderDays
                                "It has been $days ${if (days == 1) "day" else "days"} since your last Google Drive backup. Back up now?"
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                backupReminder = false
                                backupNow()
                            },
                        ) {
                            Text("Back up now")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                driveStore.markReminderShown()
                                backupReminder = false
                            },
                        ) {
                            Text("Later")
                        }
                    },
                )
            }
        }
    }

    val pendingRestore = restoreUri
    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = { if (!drive.busy) restoreUri = null },
            title = { Text("Restore backup?") },
            text = {
                Text("This replaces the local vault with the selected zip. It cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreUri = null
                        restoreBackup(pendingRestore)
                    },
                    enabled = !drive.busy,
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { restoreUri = null },
                    enabled = !drive.busy,
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    if (restorePrompt) {
        AlertDialog(
            onDismissRequest = { dismissRestorePrompt() },
            title = { Text("Restore your vault?") },
            text = {
                Text(
                    "No vault data was found on this phone. If you backed up to Google Drive, restore the latest P_Vault zip now. Otherwise start fresh.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dismissRestorePrompt()
                        onExternal()
                        restorePicker.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream",
                            ),
                        )
                    },
                ) {
                    Text("Restore from Drive")
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissRestorePrompt() }) {
                    Text("Start fresh")
                }
            },
        )
    }

    val update = availableUpdate
    if (update != null) {
        AlertDialog(
            onDismissRequest = { availableUpdate = null },
            title = { Text("Update available") },
            text = {
                Text("P Vault ${update.version} is available. You have $currentVersion.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        availableUpdate = null
                        openUrl(context, update.url)
                    },
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { availableUpdate = null }) {
                    Text("Later")
                }
            },
        )
    }
}

@Composable
private fun ProfileHeader(
    profile: UserProfile,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) {
                val avatar = profile.avatar
                if (avatar != null) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = "Profile",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onProfile),
            ) {
                Text(
                    text = profile.name.ifBlank { "Set up your profile" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Private vault · on this device",
                    color = TextSoft,
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    unit: String,
    onAdd: () -> Unit,
    onManage: (() -> Unit)?,
    manageDescription: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (count == 1) "1 $unit" else "$count ${unit}s",
                color = TextSoft,
                fontSize = 12.sp,
            )
        }
        if (onManage != null) {
            IconButton(onClick = onManage) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = manageDescription,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        FilledIconButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = "Add to $title")
        }
    }
}

@Composable
private fun VaultCard(item: VaultItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .aspectRatio(0.82f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick),
    ) {
        when (item.type) {
            ItemType.IMAGE -> {
                AsyncImage(
                    model = item.file,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                            ),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }

            ItemType.PDF, ItemType.TEXT -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (item.type == ItemType.PDF) {
                            Icons.Outlined.PictureAsPdf
                        } else {
                            Icons.AutoMirrored.Outlined.StickyNote2
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(40.dp),
                    )
                }
                Text(
                    text = item.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    tab: Tab,
    searching: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    pendingView: Boolean = false,
    chatView: Boolean = false,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = 110.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (chatView) Icons.Outlined.ChatBubbleOutline else tab.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(46.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (searching) {
                    "No matches"
                } else if (chatView) {
                    "No chats yet"
                } else {
                    "Nothing here yet"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (searching) {
                    "Try a different search."
                } else {
                    when (tab) {
                        Tab.REELS -> if (pendingView) {
                            "Shared reels land here. Move them to a category when you're ready."
                        } else {
                            "Share a reel from Instagram, or add a link with +."
                        }
                        Tab.NOTES -> if (chatView) {
                            "Tap + to start a chat. Everything stays on this device."
                        } else {
                            "Tap + to write your first note."
                        }
                        Tab.IMAGES -> "Tap + to import photos from your gallery."
                        Tab.PDFS -> "Tap + to import a PDF document."
                        Tab.FILES -> "Tap + to add a photo, PDF, note or reel."
                    }
                },
                color = TextSoft,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            if (!searching) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add")
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val uri = Uri.parse(if (url.startsWith("http")) url else "https://$url")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        .onFailure {
            Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
        }
}

private data class VaultSnapshot(
    val files: List<VaultItem>,
    val reels: List<Reel>,
    val categories: List<String>,
    val chats: List<ChatThread>,
)
