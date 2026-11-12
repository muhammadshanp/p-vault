package com.dudeapp.vault

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    onPinSetup: () -> Unit,
    theme: ThemePreset,
    onThemeChange: (ThemePreset) -> Unit,
) {
    val context = LocalContext.current
    val profileStore = remember { ProfileStore(context) }
    val driveStore = remember { DriveBackupStore(context) }
    val scope = rememberCoroutineScope()
    val currentVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) availableUpdate?.let { showUpdateNotification(context, it) }
    }
    var updateDialogOpen by remember { mutableStateOf(false) }
    var notificationAsked by remember { mutableStateOf(false) }
    var suppressNextStart by remember { mutableStateOf(false) }
    var wasStopped by remember { mutableStateOf(false) }

    var tab by remember { mutableStateOf(Tab.HOME) }
    val pagerState = rememberPagerState(initialPage = tab.ordinal, pageCount = { Tab.entries.size })
    fun switchTab(target: Tab) {
        tab = target
        scope.launch {
            pagerState.animateScrollToPage(
                page = target.ordinal,
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
            )
        }
    }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchAutoFocus by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteSelectionOpen by remember { mutableStateOf(false) }
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
    val store = remember { VaultStore(context).apply { onChange = { scope.launch { autoBackupTick += 1 } } } }

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

    fun presentUpdate(latest: AppUpdate) {
        if (!isNewerVersion(latest.version, currentVersion)) {
            clearUpdateNotification(context)
            return
        }
        availableUpdate = latest
        updateDialogOpen = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (!notificationAsked) {
                notificationAsked = true
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            showUpdateNotification(context, latest)
        }
    }

    // null = idle, -1 = downloading with unknown size, 0..100 = download progress.
    var updateProgress by remember { mutableStateOf<Int?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }

    /** Downloads the release inside the app, checks it, then opens Android's installer. */
    fun installUpdate(update: AppUpdate) {
        if (updateProgress != null) return
        updateError = null
        updateProgress = -1
        updateDialogOpen = true
        scope.launch {
            val file = downloadUpdate(context, update) { updateProgress = it }
            if (file == null) {
                updateProgress = null
                updateError = "Could not download the update. Check your connection and try again."
                return@launch
            }
            val problem = withContext(Dispatchers.IO) { updateProblem(context, file) }
            updateProgress = null
            if (problem != null) {
                updateError = problem
                return@launch
            }
            clearUpdateNotification(context)
            suppressNextStart = true
            onExternal()
            if (launchInstaller(context, file)) {
                updateDialogOpen = false
            } else {
                suppressNextStart = false
                updateError = "Could not open the installer."
            }
        }
    }

    fun openUpdatePage(url: String) {
        updateDialogOpen = false
        suppressNextStart = true
        clearUpdateNotification(context)
        openUrl(context, url)
    }

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
        val latest = checkForUpdate(currentVersion) ?: return@LaunchedEffect
        presentUpdate(latest)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> wasStopped = true
                Lifecycle.Event.ON_START -> if (wasStopped) {
                    wasStopped = false
                    if (suppressNextStart) {
                        suppressNextStart = false
                    } else {
                        if (availableUpdate != null) updateDialogOpen = true
                        scope.launch {
                            val latest = checkForUpdate(currentVersion) ?: return@launch
                            presentUpdate(latest)
                        }
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    fun toggleSelected(path: String) {
        selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
    }

    fun cancelSelection() {
        selecting = false
        selectedPaths = emptySet()
    }

    fun deleteSelected() {
        val paths = selectedPaths
        if (paths.isEmpty()) return
        deleteSelectionOpen = false
        scope.launch {
            withContext(Dispatchers.IO) {
                when {
                    tab == Tab.REELS -> reels
                        .filter { it.file.absolutePath in paths }
                        .forEach { store.deleteReel(it) }

                    tab == Tab.NOTES && chatView -> chats
                        .filter { it.dir.absolutePath in paths }
                        .forEach { store.deleteChat(it) }

                    else -> vaultItems
                        .filter { it.file.absolutePath in paths }
                        .forEach { store.delete(it) }
                }
            }
            cancelSelection()
            refresh()
        }
    }

    LaunchedEffect(tab, chatView, query, reelFilter) {
        if (selecting) cancelSelection()
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
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) { store.addFile(uri, "Document.pdf") }
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
        ActivityResultContracts.OpenDocumentTree(),
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
        val url = extractUrl(text) ?: text
        val title = text.replace(url, "").replace("\n", " ").trim().take(60)
        reelDraftUrl = url
        reelDraftTitle = title.ifBlank { "Instagram Reel" }
        reelDraftCategory = store.pendingCategoryName()
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
            Tab.HOME -> addOpen = true
            Tab.REELS -> openReelDialog()
            Tab.IMAGES -> launchPhotos()
            Tab.PDFS -> launchPdfs()
            Tab.NOTES -> if (chatView) chatDialogOpen = true else noteOpen = true
        }
    }

    val pendingName = categories.firstOrNull { it.equals("Pending", ignoreCase = true) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val settled = page.coerceIn(0, Tab.entries.lastIndex)
            tab = Tab.entries[settled]
            selecting = false
            selectedPaths = emptySet()
            searchOpen = false
            searchAutoFocus = false
            query = ""
            reelFilter = null
            // Preserve the user's chat/notes mode across tab switches; the toggle only
            // appears on the Notes tab, so resetting it here would discard their choice.
        }
    }

    val detail = open
    val chatDetail = openChat
    BackHandler(
        enabled = detail == null && chatDetail == null && !settingsOpen &&
            (searchOpen || query.isNotBlank() || selecting || tab != Tab.HOME || reelFilter != null),
    ) {
        when {
            searchOpen -> {
                searchOpen = false
                searchAutoFocus = false
                query = ""
            }
            query.isNotBlank() -> query = ""
            selecting -> {
                selecting = false
                selectedPaths = emptySet()
            }
            reelFilter != null -> reelFilter = null
            tab != Tab.HOME -> switchTab(Tab.HOME)
        }
    }
    if (settingsOpen) {
        SettingsScreen(
            theme = theme,
            settings = settings,
            update = availableUpdate,
            onTheme = onThemeChange,
            onSettings = { next ->
                val autoWasOff = !settings.autoBackup
                settings = next
                settingsStore.save(next)
                if (next.appLock && !Auth.hasPin(context)) onPinSetup()
                if (autoWasOff && next.autoBackup && driveStore.hasPending()) {
                    autoBackupTick += 1
                }
            },
            onUpdate = {
                availableUpdate?.let {
                    settingsOpen = false
                    installUpdate(it)
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
            onExternal = onExternal,
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
                    showSearch = tab != Tab.HOME,
                    searchOpen = searchOpen,
                    autoFocus = searchAutoFocus,
                    onSearchOpen = {
                        searchOpen = true
                        searchAutoFocus = true
                    },
                    onSearchClose = {
                        searchOpen = false
                        searchAutoFocus = false
                        query = ""
                    },
                    onAutoFocused = { searchAutoFocus = false },
                    welcome = tab == Tab.HOME,
                    placeholder = when (tab) {
                        Tab.HOME -> "Search"
                        Tab.REELS -> "Search reels"
                        Tab.IMAGES -> "Search images"
                        Tab.PDFS -> "Search PDFs"
                        Tab.NOTES -> if (chatView) "Search chats" else "Search notes"
                    },
                )

                // The pager and the + button share the space between the header and the
                // navigation bar, so nothing is ever drawn underneath the bar.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                HorizontalPager(
                    state = pagerState,
                    // Keep every tab composed. Swiping then only moves pages that already
                    // exist, instead of building the next one in the middle of the gesture.
                    beyondViewportPageCount = Tab.entries.size,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val currentTab = Tab.entries[page]
                    val isHome = currentTab == Tab.HOME
                    val isReels = currentTab == Tab.REELS
                    val isNotesChat = currentTab == Tab.NOTES && chatView
                    val currentVisibleFiles = vaultItems.filter { item ->
                        val inTab = when (currentTab) {
                            Tab.HOME -> false
                            Tab.REELS -> false
                            Tab.IMAGES -> item.type == ItemType.IMAGE
                            Tab.PDFS -> item.type == ItemType.PDF
                            Tab.NOTES -> !chatView && item.type == ItemType.TEXT
                        }
                        inTab && (query.isBlank() || item.title.contains(query.trim(), ignoreCase = true))
                    }
                    val currentVisibleReels = if (isReels) {
                        reels.filter { reel ->
                            (reelFilter == null || reel.category == reelFilter) &&
                                (query.isBlank() || reel.title.contains(query.trim(), ignoreCase = true))
                        }
                    } else {
                        emptyList()
                    }
                    val currentVisibleChats = if (isNotesChat) {
                        chats.filter { chat ->
                            query.isBlank() ||
                                chat.name.contains(query.trim(), ignoreCase = true) ||
                                chat.lastMessage.contains(query.trim(), ignoreCase = true)
                        }
                    } else {
                        emptyList()
                    }

                    Column(Modifier.fillMaxSize()) {
                        if (isHome) {
                            HomeContent(
                                items = vaultItems,
                                reels = reels,
                                chats = chats,
                                onOpenItem = { open = it },
                                onOpenChat = { openChat = it },
                                onOpenTab = { target -> switchTab(target) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {

                        SectionHeader(
                            title = if (isNotesChat) "Chats" else currentTab.label,
                            count = when {
                                isReels -> currentVisibleReels.size
                                isNotesChat -> currentVisibleChats.size
                                else -> currentVisibleFiles.size
                            },
                            unit = when (currentTab) {
                                Tab.HOME -> "item"
                                Tab.REELS -> "reel"
                                Tab.IMAGES -> "image"
                                Tab.PDFS -> "PDF"
                                Tab.NOTES -> if (chatView) "chat" else "note"
                            },
                            onSelect = {
                                if (selecting) {
                                    cancelSelection()
                                } else {
                                    selecting = true
                                    selectedPaths = emptySet()
                                }
                            },
                            onManage = when {
                                isReels -> { { categoriesOpen = true } }
                                isNotesChat -> { { chatsOpen = true } }
                                else -> null
                            },
                            manageDescription = if (isReels) "Edit categories" else "Manage chats",
                        )

                        if (selecting) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${selectedPaths.size} selected",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { cancelSelection() }) {
                                    Text("Cancel")
                                }
                                TextButton(
                                    onClick = {
                                        if (selectedPaths.isNotEmpty()) deleteSelectionOpen = true
                                    },
                                    enabled = selectedPaths.isNotEmpty(),
                                ) {
                                    Text(
                                        "Delete",
                                        color = if (selectedPaths.isEmpty()) {
                                            TextSoft
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }

                        if (currentTab == Tab.NOTES) {
                            TextModeChips(chatView = chatView, onChange = { chatView = it })
                            Spacer(Modifier.height(6.dp))
                        }

                        if (isReels) {
                            CategoryChips(
                                categories = categories,
                                selected = reelFilter,
                                onSelect = { reelFilter = it },
                            )
                            Spacer(Modifier.height(6.dp))

                            if (currentVisibleReels.isEmpty()) {
                                EmptyState(
                                    tab = currentTab,
                                    searching = query.isNotBlank(),
                                    pendingView = pendingName != null && reelFilter == pendingName,
                                    onAdd = { sectionAdd() },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                ) {
                                    currentVisibleReels.forEach { reel ->
                                        item(key = reel.file.absolutePath) {
                                            ReelCard(
                                                reel = reel,
                                                selected = reel.file.absolutePath in selectedPaths,
                                                selecting = selecting,
                                                onClick = {
                                                    if (selecting) {
                                                        toggleSelected(reel.file.absolutePath)
                                                    } else {
                                                        openUrl(context, reel.url)
                                                    }
                                                },
                                                onMenu = { if (!selecting) reelMenu = reel },
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (isNotesChat) {
                            if (currentVisibleChats.isEmpty()) {
                                EmptyState(
                                    tab = currentTab,
                                    searching = query.isNotBlank(),
                                    chatView = true,
                                    onAdd = { sectionAdd() },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                ) {
                                    currentVisibleChats.forEach { chat ->
                                        item(key = chat.dir.absolutePath) {
                                            ChatRow(
                                                chat = chat,
                                                selected = chat.dir.absolutePath in selectedPaths,
                                                selecting = selecting,
                                                onClick = {
                                                    if (selecting) {
                                                        toggleSelected(chat.dir.absolutePath)
                                                    } else {
                                                        openChat = chat
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (currentVisibleFiles.isEmpty()) {
                            EmptyState(
                                tab = currentTab,
                                searching = query.isNotBlank(),
                                onAdd = { sectionAdd() },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                items(currentVisibleFiles, key = { it.file.absolutePath }) { item ->
                                    VaultCard(
                                        item = item,
                                        selected = item.file.absolutePath in selectedPaths,
                                        selecting = selecting,
                                        onClick = {
                                            if (selecting) {
                                                toggleSelected(item.file.absolutePath)
                                            } else {
                                                open = item
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        }
                    }
                }

                if (!selecting) {
                    FloatingActionButton(
                        onClick = { sectionAdd() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
                }

                PagerBottomBar(
                    pagerState = pagerState,
                    onSelect = { target -> switchTab(target) },
                )
            }

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

            if (deleteSelectionOpen) {
                val count = selectedPaths.size
                AlertDialog(
                    onDismissRequest = { deleteSelectionOpen = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = {
                        Text(if (count == 1) "Delete 1 item?" else "Delete $count items?")
                    },
                    text = {
                        Text("This removes the selected items from your vault. It can't be undone.")
                    },
                    confirmButton = {
                        TextButton(onClick = { deleteSelected() }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteSelectionOpen = false }) {
                            Text("Cancel")
                        }
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
                            switchTab(Tab.REELS)
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
    if (update != null && updateDialogOpen) {
        AlertDialog(
            onDismissRequest = { updateDialogOpen = false },
            icon = {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdateAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            title = { Text("Update available", textAlign = TextAlign.Center) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "P Vault ${update.version}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "A newer version is ready. You have $currentVersion. It downloads and installs here, inside P Vault.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val progress = updateProgress
                    if (progress != null) {
                        Spacer(Modifier.height(16.dp))
                        if (progress in 0..100) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (progress in 0..100) "Downloading… $progress%" else "Downloading…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    val failure = updateError
                    if (failure != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = failure,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { installUpdate(update) },
                    enabled = updateProgress == null,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        when {
                            updateProgress != null -> "Downloading…"
                            updateError != null -> "Try again"
                            else -> "Update now"
                        },
                    )
                }
            },
            dismissButton = {
                if (updateError != null && updateProgress == null) {
                    // Fallback when the in-app download or check fails.
                    TextButton(onClick = { openUpdatePage(update.url) }) {
                        Text("Use browser")
                    }
                } else {
                    TextButton(onClick = { updateDialogOpen = false }) {
                        Text(if (updateProgress != null) "Hide" else "Not now")
                    }
                }
            },
            shape = RoundedCornerShape(28.dp),
        )
    }
}

@Composable
private fun HomeContent(
    items: List<VaultItem>,
    reels: List<Reel>,
    chats: List<ChatThread>,
    onOpenItem: (VaultItem) -> Unit,
    onOpenChat: (ChatThread) -> Unit,
    onOpenTab: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val images = items.count { it.type == ItemType.IMAGE }
    val pdfs = items.count { it.type == ItemType.PDF }
    val notes = items.count { it.type == ItemType.TEXT }
    val total = items.size + reels.size + chats.size
    val recent = items.take(4)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HomeHero(total = total) }

        item { HomeSectionTitle("Library") }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeTile(
                        icon = Icons.Outlined.Image,
                        tint = Accent.Photo,
                        title = "Photos",
                        detail = countLabel(images, "photo"),
                        onClick = { onOpenTab(Tab.IMAGES) },
                        modifier = Modifier.weight(1f),
                    )
                    HomeTile(
                        icon = Icons.Outlined.PictureAsPdf,
                        tint = Accent.Pdf,
                        title = "PDFs",
                        detail = countLabel(pdfs, "PDF"),
                        onClick = { onOpenTab(Tab.PDFS) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeTile(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        tint = Accent.Chat,
                        title = "Notes & chats",
                        detail = "${countLabel(notes, "note")} · ${countLabel(chats.size, "chat")}",
                        onClick = { onOpenTab(Tab.NOTES) },
                        modifier = Modifier.weight(1f),
                    )
                    HomeTile(
                        icon = Icons.Outlined.PlayCircle,
                        tint = Accent.Reel,
                        title = "Reels",
                        detail = countLabel(reels.size, "reel"),
                        onClick = { onOpenTab(Tab.REELS) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (total == 0) {
            item {
                Text(
                    text = "Your vault is empty. Tap + to add a photo, PDF, note or reel.",
                    color = TextSoft,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        }

        if (recent.isNotEmpty()) {
            item { HomeSectionTitle("Recently added") }
            recent.forEach { item ->
                item(key = item.file.absolutePath) {
                    HomeRecentRow(item = item, onClick = { onOpenItem(item) })
                }
            }
        }

        if (chats.isNotEmpty()) {
            item { HomeSectionTitle("Recent chats") }
            chats.take(3).forEach { chat ->
                item(key = chat.dir.absolutePath) {
                    ChatRow(
                        chat = chat,
                        selected = false,
                        selecting = false,
                        onClick = { onOpenChat(chat) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHero(total: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFFF0703F), Color(0xFFB9391A))),
            )
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "PRIVATE VAULT",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = total.toString(),
            color = Color.White,
            fontSize = 44.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (total == 1) {
                "item kept safe on this device"
            } else {
                "items kept safe on this device"
            },
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun HomeTile(
    icon: ImageVector,
    tint: Color,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .vaultCard(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        IconBadge(icon = icon, tint = tint)
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = detail,
            color = TextSoft,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeRecentRow(item: VaultItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .vaultCard(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (item.type) {
            ItemType.IMAGE -> AsyncImage(
                model = item.file,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            ItemType.PDF -> IconBadge(
                icon = Icons.Outlined.PictureAsPdf,
                tint = Accent.Pdf,
                size = 48.dp,
                iconSize = 24.dp,
            )

            ItemType.TEXT -> IconBadge(
                icon = Icons.AutoMirrored.Outlined.StickyNote2,
                tint = Accent.Note,
                size = 48.dp,
                iconSize = 24.dp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = when (item.type) {
                    ItemType.IMAGE -> "Photo"
                    ItemType.PDF -> "PDF"
                    ItemType.TEXT -> "Note"
                },
                color = TextSoft,
                fontSize = 12.sp,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSoft,
        )
    }
}

@Composable
private fun HomeSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private fun countLabel(count: Int, unit: String): String =
    if (count == 1) "1 $unit" else "$count ${unit}s"

/** Pulls the first well-formed http(s) URL out of a shared message, stripping trailing
 *  punctuation that often trails a pasted link (e.g. "Check this out! https://..."). */
private fun extractUrl(text: String): String? {
    val match = Regex("""https?://[^\s]+""").find(text) ?: return null
    var url = match.value
    while (url.isNotEmpty() && url.last() in ".,;:!?)]}'\"") {
        url = url.dropLast(1)
    }
    return url.ifBlank { null }
}

@Composable
private fun HeaderButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
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
    showSearch: Boolean = true,
    searchOpen: Boolean = false,
    autoFocus: Boolean = false,
    onSearchOpen: () -> Unit = {},
    onSearchClose: () -> Unit = {},
    onAutoFocused: () -> Unit = {},
    welcome: Boolean = false,
) {
    val name = profile.name.trim()
    val firstName = name.substringBefore(' ')
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus && showSearch && searchOpen) {
            focusRequester.requestFocus()
            onAutoFocused()
        }
    }
    Column(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onProfile),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
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
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            name.isBlank() -> "Tap to set up your profile"
                            welcome -> "Welcome back"
                            else -> "P Vault"
                        },
                        color = TextSoft,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            name.isBlank() -> "Welcome"
                            welcome -> firstName
                            else -> name
                        },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (showSearch && !searchOpen) {
                HeaderButton(Icons.Default.Search, "Search", onSearchOpen)
                Spacer(Modifier.width(8.dp))
            }
            HeaderButton(Icons.Outlined.Settings, "Settings", onSettings)
        }
        Spacer(Modifier.height(14.dp))
        if (showSearch && searchOpen) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(placeholder) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = onSearchClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close search")
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    unit: String,
    onSelect: () -> Unit,
    onManage: (() -> Unit)?,
    manageDescription: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = countLabel(count, unit),
                color = TextSoft,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        if (onManage != null) {
            HeaderButton(Icons.Outlined.Tune, manageDescription, onManage)
            Spacer(Modifier.width(8.dp))
        }
        HeaderButton(Icons.Default.Edit, "Select $title", onSelect)
    }
}

@Composable
private fun VaultCard(
    item: VaultItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
) {
    val marked = selecting && selected
    Box(
        Modifier
            .aspectRatio(0.86f)
            .vaultCard(RoundedCornerShape(22.dp), marked)
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
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                            ),
                        )
                        .padding(start = 12.dp, end = 12.dp, top = 26.dp, bottom = 10.dp),
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
            ) {
                IconBadge(
                    icon = if (item.type == ItemType.PDF) {
                        Icons.Outlined.PictureAsPdf
                    } else {
                        Icons.AutoMirrored.Outlined.StickyNote2
                    },
                    tint = accentOf(item.type),
                    size = 48.dp,
                    iconSize = 24.dp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = item.title,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (item.type == ItemType.PDF) "PDF" else "Note",
                    color = TextSoft,
                    fontSize = 11.sp,
                )
            }
        }

        if (selecting) {
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                )
            }
            SelectionDot(
                selected = selected,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            )
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
            .padding(start = 36.dp, end = 36.dp, bottom = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(
                icon = when {
                    searching -> Icons.Default.Search
                    chatView -> Icons.Outlined.ChatBubbleOutline
                    else -> tab.icon
                },
                tint = MaterialTheme.colorScheme.primary,
                size = 76.dp,
                iconSize = 34.dp,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (searching) {
                    "No matches"
                } else if (chatView) {
                    "No chats yet"
                } else {
                    "Nothing here yet"
                },
                style = MaterialTheme.typography.titleLarge,
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
                        Tab.HOME -> "Tap + to add a photo, PDF, note or reel."
                    }
                },
                color = TextSoft,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            if (!searching) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAdd) {
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
