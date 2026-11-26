package com.dudeapp.vault

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import android.app.Activity
import java.io.File
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    sharedFiles: List<Uri> = emptyList(),
    onFilesConsumed: () -> Unit = {},
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
    // Seen-once screens. Welcome comes before anything else on a new install; the name
    // step only appears for a signed-in person who has not claimed one yet.
    val introPrefs = remember { context.getSharedPreferences("dude_intro", Context.MODE_PRIVATE) }
    var showWelcome by remember { mutableStateOf(!introPrefs.getBoolean("welcome_done", false)) }
    var showNameStep by remember { mutableStateOf(false) }
    fun finishIntro() {
        introPrefs.edit().putBoolean("welcome_done", true).apply()
        showWelcome = false
    }
    // A signed-out phone goes back to the login page rather than dropping into the vault
    // as a stranger. Home stays reachable from it with Skip.
    fun requireSignIn() {
        introPrefs.edit().putBoolean("welcome_done", false).apply()
        showWelcome = true
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

    val tabPrefs = remember { context.getSharedPreferences("dude_files", Context.MODE_PRIVATE) }
    // Which Files-tab page was showing when the user set off. A picker can come back after the
    // app was rebuilt (Android reclaims the window behind a document picker on Android 11), and
    // the page must still be the one the file is meant for, so it is saved rather than remembered.
    var tab by remember {
        mutableStateOf(
            Tab.entries.firstOrNull { it.name == tabPrefs.getString("files_tab", null) } ?: Tab.HOME,
        )
    }
    val pagerState = rememberPagerState(
        initialPage = tab.ordinal.coerceIn(0, Tab.entries.lastIndex),
        pageCount = { Tab.entries.size },
    )
    // True from the moment a picker is opened until it answers. While it is set, a fresh
    // tab page on Home must not pull the pager away from the page the file is meant for:
    // Android can rebuild the app behind the picker, and that rebuild starts on Home.
    var awaitingPicker by remember { mutableStateOf(false) }
    // The same, remembered across a rebuild: a rebuild happens exactly while a picker is open.
    var pickerOut by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (awaitingPicker && tab == Tab.PDFS && pagerState.currentPage != Tab.PDFS.ordinal) {
            pagerState.scrollToPage(Tab.PDFS.ordinal)
        }
    }
    fun switchTab(target: Tab) {
        tab = target
        scope.launch {
            pagerState.animateScrollToPage(
                page = target.ordinal,
                // A spring, not a 160ms tween: the page carries a little momentum into place
                // instead of stopping dead the instant the animation ends.
                animationSpec = spring(
                    dampingRatio = 0.86f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
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
    val sortPrefs = remember { context.getSharedPreferences("dude_app", Context.MODE_PRIVATE) }
    var sortOrder by remember {
        mutableStateOf(
            SortOrder.entries.firstOrNull { it.name == sortPrefs.getString("file_sort", null) } ?: SortOrder.NEWEST,
        )
    }
    // Sorted once per change, not once per page.
    val sortedItems = remember(vaultItems, sortOrder) { vaultItems.sortedFor(sortOrder) }
    val photoItems = remember(sortedItems) { sortedItems.filter { it.type == ItemType.IMAGE } }
    var backupProtected by remember { mutableStateOf(false) }
    var restorePasswordFor by remember { mutableStateOf<Uri?>(null) }
    var restorePasswordError by remember { mutableStateOf<String?>(null) }
    var reels by remember { mutableStateOf<List<Reel>>(emptyList()) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var chats by remember { mutableStateOf<List<ChatThread>>(emptyList()) }
    var open by remember { mutableStateOf<VaultItem?>(null) }
    // The Add sheet is closed the moment a picker is opened, and it must stay closed: a sheet
    // left open across the trip would cover the vault when the picker comes back.
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    // Text shared from another app, waiting in the note dialog. Saved, so a rebuild behind the
    // other app's window does not lose it.
    var sharedNote by rememberSaveable { mutableStateOf("") }
    var version by remember { mutableIntStateOf(0) }
    var autoBackupTick by remember { mutableIntStateOf(0) }
    var autoBackupSkip by remember { mutableIntStateOf(0) }
    val store = remember { VaultStore(context).apply { onChange = { scope.launch { autoBackupTick += 1 } } } }

    var chatView by remember { mutableStateOf(true) }
    var openChat by remember { mutableStateOf<ChatThread?>(null) }
    var chatDialogOpen by remember { mutableStateOf(false) }
    var chatsOpen by remember { mutableStateOf(false) }
    var remindersOpen by remember { mutableStateOf(false) }
    val reminderTick = ReminderCenter.tick.intValue

    var reelFilter by remember { mutableStateOf<String?>(null) }
    var reelDialogOpen by remember { mutableStateOf(false) }
    var reelDraftUrl by remember { mutableStateOf("") }
    var reelDraftTitle by remember { mutableStateOf("") }
    var reelDraftCategory by remember { mutableStateOf<String?>(null) }
    var reelMenu by remember { mutableStateOf<Reel?>(null) }
    var categoriesOpen by remember { mutableStateOf(false) }

    var profile by remember { mutableStateOf(profileStore.load()) }
    var profileOpen by remember { mutableStateOf(false) }
    // Whether a Google account is signed in on this phone, for the menu's sign-in item.
    var signedIn by remember { mutableStateOf(GoogleAccountStore(context).email() != null) }
    // Whether usernames may still be changed, as the admin last set it. Read from the public
    // document when the access list refreshes, so it is right without opening a screen.
    var namesFixed by remember { mutableStateOf(AccessStore(context).namesFixed()) }
    // Bumped whenever the profile changes, to reopen the sheet with the saved values.
    var profileSheetKey by remember { mutableIntStateOf(0) }
    val usernameStore = remember { UsernameStore(context) }
    var chatAvatarTarget by remember { mutableStateOf<ChatThread?>(null) }
    var drive by remember { mutableStateOf(driveStore.state()) }
    LaunchedEffect(Unit) { backupProtected = withContext(Dispatchers.IO) { driveStore.isEncrypted() } }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupReminder by remember { mutableStateOf(false) }
    var connectPrompt by remember { mutableStateOf(false) }
    var adminOpen by remember { mutableStateOf(false) }
    var inboxOpen by remember { mutableStateOf(false) }
    val pushInbox = remember { PushInbox(context) }
    val inboxChange = PushInbox.changes.intValue
    val unreadPushes = remember(inboxChange) { pushInbox.unreadCount() }
    var isAdmin by remember { mutableStateOf(AdminStore(context).isAdmin()) }
    // Picks up special access given or removed by the admin since the last time.
    // What this account may do: its level, its switches and the looks shared with it.
    var access by remember { mutableStateOf(AccessStore(context).access()) }
    // What the server says this name may use. Applied only when the server answers for this
    // device: with no claimed name, or offline, nothing is restricted — a lock that depends on
    // a network call must never make the vault unusable on a plane.
    var grantsActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("pre_re_supabase", android.content.Context.MODE_PRIVATE)
        // The owner can rename a name from the panel. The phone asks the server which name it
        // holds, so a rename lands here instead of leaving the phone with a name that is gone.
        val synced = Supabase.myName(context)
        if (synced != null && synced != prefs.getString("claimed_name", null)) {
            prefs.edit().putString("claimed_name", synced).apply()
        }
        val claimed = synced ?: prefs.getString("claimed_name", null) ?: return@LaunchedEffect
        val remote = Supabase.myAccess(context, claimed) ?: return@LaunchedEffect
        prefs.edit().putBoolean("verified", remote.granted && remote.verified).apply()
        if (!remote.granted) return@LaunchedEffect
        val granted = remote.features.mapNotNull { AccessFeature.of(it) }.toSet()
        access = access.copy(level = AccessLevel.of(remote.level), features = access.features + granted)
        grantsActive = true
    }
    val chatsAllowed = !grantsActive || access.has(AccessFeature.CHATS)
    LaunchedEffect(chatsAllowed) { if (!chatsAllowed) chatView = false }
    val specialAccess = access.theme
    var themesOpen by remember { mutableStateOf(false) }
    var advancedThemesOpen by remember { mutableStateOf(false) }
    var bundlesOpen by remember { mutableStateOf(false) }
    // A theme made on this phone, if one is being worn. Advanced off means none is.
    val ownTheme = Bundles.worn
    val bundlesStore = remember { BundleStore(context) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Bundles.publish(bundlesStore) } }
    // The photos this phone chose for its cards, one per card at most.
    val cardPhotos = remember { CardPhotoStore(context) }
    // Read as state, so a card redraws the moment its photo is chosen or removed.
    val photoFiles by CardPhotoFiles.current
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { cardPhotos.publish() } }

    val themeStore = remember { ThemeStore(context) }
    // Looks that arrive from the theme catalogue, without a new app build.
    val catalogue = remember { ThemeCatalogue(context) }
    var catalogThemes by remember { mutableStateOf<List<DownloadedTheme>>(emptyList()) }
    var catalogRefreshing by remember { mutableStateOf(false) }
    var catalogProblem by remember { mutableStateOf<String?>(null) }

    // Only the looks this account may use, with their pictures downloaded for offline use.
    LaunchedEffect(catalogThemes, access) {
        val email = GoogleAccountStore(context).email()
        val mine = catalogThemes.filter { it.isAllowedFor(email, access.theme, access) }
        mine.forEach { catalogue.downloadPicture(it) }
        catalogue.tidy(mine)
    }

    fun loadCatalogue(lookOnline: Boolean) {
        if (lookOnline) catalogRefreshing = true
        scope.launch {
            val fetched = if (lookOnline) catalogue.refresh() else null
            if (lookOnline && fetched == null) {
                catalogProblem = "No connection. Showing the looks already on this phone."
            } else {
                catalogProblem = null
            }
            catalogThemes = fetched ?: catalogue.cached()
            catalogRefreshing = false
        }
    }
    LaunchedEffect(Unit) { loadCatalogue(lookOnline = true) }
    // Someone who signed in with an earlier build has been waiting without a name: they are
    // allowed to defer it. Anyone signing in now is a new account and must leave with one.
    var nameStepMayDefer by remember { mutableStateOf(introPrefs.getBoolean("signed_in_before", true)) }
    LaunchedEffect(signedIn, showWelcome, profileSheetKey) {
        if (showWelcome) return@LaunchedEffect
        if (!signedIn) return@LaunchedEffect
        // Signing in is the whole sign-up: the only thing left is the display name, and the
        // server is asked rather than a local flag, so a reinstall does not ask twice.
        val held = withContext(Dispatchers.IO) {
            runCatching { Supabase.myName(context) }.getOrNull()
        }
        if (held == null) {
            // A person who was signed in before this update gets the choice to defer; a
            // fresh sign-in does not, because their account must end up with a name.
            nameStepMayDefer = GoogleAccountStore(context).email() != null &&
                introPrefs.getBoolean("signed_in_before", true)
            showNameStep = true
        } else {
            showNameStep = false
        }
    }

    // Marks the end of the "already signed in before this build" state, once the name step
    // has settled. Everything after this point treats a missing name as a new account.
    LaunchedEffect(Unit) {
        introPrefs.edit().putBoolean("signed_in_before", false).apply()
    }
    LaunchedEffect(Unit) {
        AccessStore(context).refresh()
        // The name this phone holds, its level and both ticks, in one call. A name is not
        // re-registered here: signing in is the only thing that ever claims one.
        access = AccessStore(context).refreshGrant()
        // Signing in is remembered: the app never asks Google again after the first time.
        // A stored address that the owner list no longer knows simply stops being an admin.
        access.username?.let { name ->
            introPrefs.edit().putString("last_name", name).apply()
        }
        // An owner address lights the panel up by itself: no held tap, no passphrase, no
        // sign-in twice. Anyone else never sees the entry.
        isAdmin = AdminStore(context).refreshFromServer()
        // The chosen look may need a permission this phone no longer has, and a look from
        // the catalogue is only known once its copy has been read.
        catalogThemes = catalogue.cached()
        val allowed = themeStore.load(catalogThemes).forAccess(access, isAdmin)
        if (allowed != theme) onThemeChange(allowed)
    }
    var reminderNeverBackedUp by remember { mutableStateOf(false) }
    var restorePrompt by remember { mutableStateOf(false) }
    val settingsStore = remember { SettingsStore(context) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    // With advanced mode off no card photo is shown at all, so the cards go back to the
    // pictures the chosen theme gives them.
    val advancedPhotos: Map<CardPicture, File> =
        if (settings.advancedMode) photoFiles else emptyMap()
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
        if (!drive.connected) {
            // Nothing is backed up anywhere: suggest Drive now and then, once there is
            // something worth keeping.
            val worthAsking = withContext(Dispatchers.IO) { store.hasUserContent() }
            if (worthAsking && driveStore.connectPromptDue()) connectPrompt = true
            return@LaunchedEffect
        }
        if (driveStore.hasPending()) {
            autoBackupTick += 1
            return@LaunchedEffect
        }
        if (!driveStore.reminderDue(settings.reminderDays)) return@LaunchedEffect
        reminderNeverBackedUp = driveStore.lastBackupAt() == 0L
        backupReminder = true
    }

    LaunchedEffect(Unit) {
        // Push notifications: listen for messages to all users, and ask once for
        // permission to show them (Android 13 and newer).
        ensurePushChannel(context)
        subscribeToPushes()
        val pushPrefs = context.getSharedPreferences("dude_app", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !pushPrefs.getBoolean("push_permission_asked", false)
        ) {
            pushPrefs.edit().putBoolean("push_permission_asked", true).apply()
            notificationAsked = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
            // Mark first, so the auto-backup that the tick starts always sees the change.
            driveStore.markPending()
            autoBackupTick += 1
        }
    }

    fun toggleSelected(path: String) {
        selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
        // Like a gallery: unticking the last item leaves selection mode.
        if (selectedPaths.isEmpty()) selecting = false
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

    // A picker can come back empty-handed: some phones hand PDFs to an app that cannot return
    // them, such as a browser. Say so instead of just flashing back to the vault.
    val pickerProblem = remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(50),
    ) { uris ->
        if (uris.isEmpty()) pickerProblem.value = "No photos came back. Try picking them again."
        if (uris.isNotEmpty()) {
            scope.launch {
                val added = withContext(Dispatchers.IO) { uris.count { store.addFile(it, "Image.jpg") != null } }
                refresh()
                Toast.makeText(context, addedMessage(added, uris.size, "photo"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Several PDFs can be ticked in one go.
    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        // A picker is back, so the tab must not be started on again.
        awaitingPicker = false
        pickerOut = false
        tabPrefs.edit().putBoolean("picker_open", false).apply()
        if (uris.isEmpty()) {
            pickerProblem.value = "No PDF came back from the file picker.\n\n" +
                "Some PDFs — one tapped in a browser's downloads, for example — are handed over " +
                "as \"Open with\" instead of being pickable. Save the file to your phone's Files " +
                "first, then use the PDFs tab's + button and pick it from there."
        }
        if (uris.isNotEmpty()) {
            scope.launch {
                val added = withContext(Dispatchers.IO) { uris.count { store.addFile(it, "Document.pdf") != null } }
                refresh()
                Toast.makeText(context, addedMessage(added, uris.size, "PDF"), Toast.LENGTH_SHORT).show()
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
                    // Read back from the store, so the picture on screen is the one saved
                    // rather than a copy made up here that can be missed by a comparison.
                    profile = profileStore.load()
                    profileSheetKey += 1
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

    fun exportInto(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val result = withContext(Dispatchers.IO) { driveStore.exportTo(uri) }
            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        }
    }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> exportInto(uri) }

    // A password-protected export is not a zip, so it is saved as a plain data file.
    val protectedExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> exportInto(uri) }

    // Shared text goes where it belongs: a reel link to Reels, anything else to a note carrying
    // the text, ready to save. Sharing is not a way to turn every sentence into a reel.
    LaunchedEffect(sharedText) {
        val text = sharedText?.trim().orEmpty()
        if (text.isEmpty()) return@LaunchedEffect
        val url = extractUrl(text)
        onShareConsumed()
        open = null
        openChat = null
        if (url != null && looksLikeReel(text)) {
            reelDraftUrl = url
            reelDraftTitle = text.replace(url, "").replace("\n", " ").trim().take(60)
                .ifBlank { "Instagram Reel" }
            reelDraftCategory = store.pendingCategoryName()
            reelDialogOpen = true
        } else {
            sharedNote = text
            noteOpen = true
        }
    }

    // Files shared in from another app go where they belong: photos to Images, PDFs to PDFs.
    LaunchedEffect(sharedFiles) {
        if (sharedFiles.isEmpty()) return@LaunchedEffect
        val files = sharedFiles
        onFilesConsumed()
        val sorted = sortedShared(context, files)
        val addedPhotos = withContext(Dispatchers.IO) {
            sorted.photos.count { store.addFile(it, "Image.jpg") != null }
        }
        val addedPdfs = withContext(Dispatchers.IO) {
            sorted.pdfs.count { store.addFile(it, "Document.pdf") != null }
        }
        val added = addedPhotos + addedPdfs
        if (added > 0) {
            val target = if (addedPhotos > 0) Tab.IMAGES else Tab.PDFS
            tabPrefs.edit().putString("files_tab", target.name).apply()
            refresh()
            switchTab(target)
        }
        val message = when {
            added == 0 && sorted.refused > 0 ->
                "Only photos and PDFs can go into the vault."
            added == 0 -> "Nothing in that share could be added to the vault."
            addedPhotos > 0 && addedPdfs > 0 -> "$added files added: $addedPhotos to Images, $addedPdfs to PDFs"
            addedPhotos > 0 -> addedMessage(addedPhotos, sorted.photos.size, "photo")
            else -> addedMessage(addedPdfs, sorted.pdfs.size, "PDF")
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun launchPhotos() {
        onExternal()
        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /**
     * Opens Android's own file picker for PDFs. The page a picked file is meant for is saved
     * first, so a picker that returns after the app was rebuilt still lands in the right place.
     * If no app on the phone can answer a PDF request, the vault is told instead of the app
     * quietly closing.
     */
    fun launchPdfs(anyFile: Boolean = false) {
        onExternal()
        pickerProblem.value = null
        if (tab == Tab.HOME) switchTab(Tab.PDFS)
        tabPrefs.edit().putString("files_tab", Tab.PDFS.name).apply()
        awaitingPicker = true
        val types = if (anyFile) {
            arrayOf("application/pdf", "application/octet-stream")
        } else {
            arrayOf("application/pdf")
        }
        tabPrefs.edit().putBoolean("picker_open", true).apply()
        pickerOut = true
        runCatching { pdfPicker.launch(types) }
            .onFailure {
                awaitingPicker = false
                tabPrefs.edit().putBoolean("picker_open", false).apply()
                pickerProblem.value = "No file picker answered on this phone. Open the PDF in " +
                    "Files first, then add it from the PDFs tab."
            }
    }

    // Shown once the picker is done, whatever came back, so an empty trip is explained.
    if (pickerProblem.value != null) {
        val message = pickerProblem.value.orEmpty()
        AlertDialog(
            onDismissRequest = { pickerProblem.value = null },
            confirmButton = {
                TextButton(onClick = { pickerProblem.value = null }) { Text("OK") }
            },
            text = { Text(message) },
        )
    }

    // ---- Scan and camera ----------------------------------------------------
    var addMenuOpen by rememberSaveable { mutableStateOf(false) }
    // Whether the photo being taken becomes a one-page PDF (from the PDFs tab) or a photo.
    var cameraAsPdf by rememberSaveable { mutableStateOf(false) }

    // Coming back with a picker's answer: close the sheet or menu the user never saw close.
    LaunchedEffect(pickerOut) {
        if (pickerOut) {
            addOpen = false
            addMenuOpen = false
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val pdf = if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri
        } else {
            null
        }
        if (pdf != null) {
            scope.launch {
                val added = withContext(Dispatchers.IO) {
                    val item = store.addFile(pdf, "Scan ${captureStamp()}.pdf")
                    // The scanner leaves its copy in the app's cache; the vault has its own now.
                    runCatching { pdf.path?.let { File(it).delete() } }
                    item
                }
                refresh()
                Toast.makeText(
                    context,
                    if (added != null) "Scan saved to PDFs" else "Could not save the scan",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun openScan() {
        val activity = context as? Activity
        if (activity == null) {
            Toast.makeText(context, "The scanner could not be opened", Toast.LENGTH_SHORT).show()
            return
        }
        onExternal()
        startDocumentScan(
            activity,
            onReady = { request -> scanLauncher.launch(request) },
            onUnavailable = {
                Toast.makeText(
                    context,
                    "The document scanner is not available. It needs Google Play services and, the first time, the internet.",
                    Toast.LENGTH_LONG,
                ).show()
            },
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { taken ->
        val asPdf = cameraAsPdf
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                val photo = cameraTempFile(context)
                try {
                    if (!taken || photo.length() == 0L) return@withContext null
                    if (asPdf) {
                        val pdf = File(photo.parentFile, "capture.pdf")
                        try {
                            if (photoToPdf(photo, pdf)) {
                                store.addFile(Uri.fromFile(pdf), "Scan ${captureStamp()}.pdf")
                            } else {
                                null
                            }
                        } finally {
                            pdf.delete()
                        }
                    } else {
                        store.addFile(Uri.fromFile(photo), "Photo ${captureStamp()}.jpg")
                    }
                } finally {
                    // Nothing is left outside the vault.
                    photo.delete()
                }
            }
            if (taken) {
                refresh()
                Toast.makeText(
                    context,
                    when {
                        added == null -> "Could not save the photo"
                        asPdf -> "Saved to PDFs"
                        else -> "Saved to Images"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun openCamera(asPdf: Boolean) {
        cameraAsPdf = asPdf
        onExternal()
        tabPrefs.edit().putString("files_tab", tab.name).apply()
        runCatching {
            // The photo itself lands in this temporary file. It is written to disk now because
            // the camera app can make Android reclaim P Vault's window, and the file has to
            // still be there, and still be the one this app knows, when the camera comes back.
            cameraTempFile(context).delete()
            if (!cameraTempFile(context).createNewFile()) {
                throw IllegalStateException("No room for the photo")
            }
            cameraLauncher.launch(cameraTempUri(context))
        }.onFailure {
            Toast.makeText(context, "The camera could not be opened", Toast.LENGTH_SHORT).show()
        }
    }

    // Android asks for the camera the first time it is needed, not at start-up.
    var afterCameraPermission by rememberSaveable { mutableStateOf("") }

    fun runCameraAction(action: String) {
        when (action) {
            "scan" -> openScan()
            "pdf" -> openCamera(asPdf = true)
            "photo" -> openCamera(asPdf = false)
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = afterCameraPermission
        afterCameraPermission = ""
        if (granted) {
            runCameraAction(action)
        } else {
            Toast.makeText(
                context,
                "P Vault needs the camera for this. You can allow it in Android's settings for P Vault.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun withCamera(action: String) {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            runCameraAction(action)
        } else {
            afterCameraPermission = action
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchScan() = withCamera("scan")

    fun launchCamera(asPdf: Boolean) = withCamera(if (asPdf) "pdf" else "photo")

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

    /** Sets, changes (non-null) or removes (null) the password that protects backups. */
    fun changeBackupPassword(password: String?) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (password == null) {
                    driveStore.clearPassword()
                    true
                } else {
                    driveStore.setPassword(password.toCharArray())
                }
            }
            backupProtected = withContext(Dispatchers.IO) { driveStore.isEncrypted() }
            if (!ok) {
                Toast.makeText(context, "Could not set the password", Toast.LENGTH_LONG).show()
                return@launch
            }
            Toast.makeText(
                context,
                if (password == null) "Backup password removed" else "Backups are now password-protected",
                Toast.LENGTH_LONG,
            ).show()
            // Make a fresh backup in the new form soon.
            driveStore.markPending()
            autoBackupTick += 1
        }
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
                driveStore.backupNow(settings.keepBackups, settings.backupLimitGb * GB)
            }
            drive = driveStore.state().copy(busy = false, message = result.message)
            if (showToast || !result.ok) {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var aboutOpen by remember { mutableStateOf(false) }

    /** Slides the menu shut, then runs the chosen action. */
    fun closeMenu(then: () -> Unit = {}) {
        scope.launch {
            drawerState.close()
            then()
        }
    }

    fun checkUpdatesNow() {
        if (availableUpdate != null) {
            updateDialogOpen = true
            return
        }
        Toast.makeText(context, "Checking for updates…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val latest = checkForUpdate(currentVersion)
            when {
                latest == null ->
                    Toast.makeText(context, "Could not check. Is the internet on?", Toast.LENGTH_LONG).show()
                isNewerVersion(latest.version, currentVersion) -> presentUpdate(latest)
                else ->
                    Toast.makeText(context, "You have the latest version", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportVault() {
        onExternal()
        if (backupProtected) {
            protectedExportPicker.launch(driveStore.suggestedName())
        } else {
            exportPicker.launch(driveStore.suggestedName())
        }
    }

    fun clearVaultData() {
        if (drive.busy) {
            Toast.makeText(context, "Wait for the current backup to finish", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            val cleared = withContext(Dispatchers.IO) {
                val ok = store.clearAll()
                if (ok) Reminders.cancelAll(context)
                if (ok) profileStore.clear()
                // Clearing the vault is a fresh start: account, grants and panel go too,
                // through the same sign-out as everywhere else.
                if (ok) signOutOfAccount(context)
                if (ok) access = MyAccess()
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

    fun restoreBackup(uri: Uri, password: String? = null) {
        if (drive.busy) return
        autoBackupTick += 1
        autoBackupSkip = autoBackupTick
        drive = drive.copy(busy = true, message = null)
        scope.launch {
            val result = withContext(Dispatchers.IO + NonCancellable) {
                driveStore.restoreFromZip(uri, password?.toCharArray())
            }
            if (result.passwordNeeded) {
                // Ask for the password (again, if the one typed did not fit).
                drive = driveStore.state().copy(busy = false)
                restorePasswordError = if (password != null) result.message else null
                restorePasswordFor = uri
                return@launch
            }
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
            // Photos and PDFs can come from several places, so + opens a small menu.
            Tab.IMAGES, Tab.PDFS -> addMenuOpen = true
            Tab.NOTES -> if (chatView) chatDialogOpen = true else noteOpen = true
        }
    }

    val pendingName = categories.firstOrNull { it.equals("Pending", ignoreCase = true) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val settled = page.coerceIn(0, Tab.entries.lastIndex)
            tab = Tab.entries[settled]
            tabPrefs.edit().putString("files_tab", tab.name).apply()
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

    // Before the vault: the welcome page, then the name, each only once.
    if (showWelcome) {
        WelcomeScreen(
            onSignedIn = { user ->
                GoogleAccountStore(context).save(user)
                signedIn = true
                finishIntro()
            },
            onSkip = {
                finishIntro()
                signedIn = GoogleAccountStore(context).email() != null
            },
            onExternal = onExternal,
        )
        return
    }
    if (showNameStep) {
        ChooseNameScreen(
            allowLater = nameStepMayDefer,
            onDone = {
                showNameStep = false
                scope.launch { access = AccessStore(context).refreshGrant() }
            },
            onSkip = {
                showNameStep = false
                scope.launch { access = AccessStore(context).refreshGrant() }
            },
        )
        return
    }

    val detail = open
    val chatDetail = openChat
    BackHandler(
        enabled = detail == null && chatDetail == null && !settingsOpen && !adminOpen && !inboxOpen &&
            !themesOpen && !advancedThemesOpen && !bundlesOpen &&
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
    if (inboxOpen) {
        InboxScreen(onBack = { inboxOpen = false })
    } else if (adminOpen) {
        AdminScreen(
            shareableLooks = (
                catalogThemes.map { it.id to it.label } +
                    ThemePreset.all.filter { it.cardPictures.isNotEmpty() }.map { it.shareId() to it.label }
                ).distinctBy { it.first },
            onExternal = onExternal,
            onAdminChanged = { isAdmin = AdminStore(context).isAdmin() },
            onBack = { adminOpen = false },
        )
    } else if (advancedThemesOpen) {
        AdvancedThemesScreen(
            theme = theme,
            access = access.copy(admin = isAdmin),
            photos = advancedPhotos,
            onPhoto = { kind, uri ->
                if (uri != null) {
                    scope.launch {
                        if (cardPhotos.save(kind, uri)) {
                            cardPhotos.publish()
                        } else {
                            Toast.makeText(context, "Could not save that photo", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onClearPhoto = { kind ->
                cardPhotos.clear(kind)
                cardPhotos.publish()
            },
            // The whole background: one image, or back to the theme's own.
            onBackground = { uri ->
                if (uri != null) {
                    scope.launch {
                        if (cardPhotos.saveBackground(uri)) {
                            cardPhotos.publish()
                        } else {
                            Toast.makeText(context, "Could not save that image", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onClearBackground = {
                cardPhotos.clearBackground()
                cardPhotos.publish()
            },
            bundlesOpen = bundlesOpen,
            onBundles = { bundlesOpen = it },
            onOpenBundles = {
                bundlesOpen = true
            },
            onPick = onThemeChange,
            onBack = { advancedThemesOpen = false },
        )
    } else if (themesOpen) {
        ThemesScreen(
            theme = theme,
            access = access.copy(admin = isAdmin),
            // The switch is on the Theme page itself, so advanced mode is turned on and off
            // where the Advanced page lives.
            advancedOn = settings.advancedMode,
            // Advanced is hidden outright for an ordinary account: only the admin and names
            // the owner gave Theme + Advanced (or Everything) ever see the entry.
            advancedAllowed = access.advanced,
            onAdvancedChange = { on ->
                settings = settings.copy(advancedMode = on)
                settingsStore.save(settings)
                // Advanced off switches the advanced things themselves off, not just their
                // screens: the theme's own background and cards come back for the whole app.
                Bundles.advancedOn.value = on
                cardPhotos.publish()
                if (!on) {
                    // Leaving also closes the pages the switch hides.
                    advancedThemesOpen = false
                    bundlesOpen = false
                }
            },
            catalogThemes = catalogThemes.filter {
                it.isAllowedFor(GoogleAccountStore(context).email(), access.theme, access)
            },
            refreshing = catalogRefreshing,
            catalogueProblem = catalogProblem,
            onRefresh = { loadCatalogue(lookOnline = true) },
            onPick = onThemeChange,
            // Advanced mode has to be on for the page to mean anything: it is the switch
            // that lets a look carry its own background and card pictures.
            onAdvanced = {
                if (settings.advancedMode) {
                    themesOpen = false
                    advancedThemesOpen = true
                }
            },
            advancedInUse = Bundles.applied.value != null || CardPhotoFiles.current.value.isNotEmpty() || CardPhotoFiles.background.value != null,
            onBack = { themesOpen = false },
        )
    } else if (settingsOpen) {
        SettingsScreen(
            theme = theme,
            settings = settings,
            update = availableUpdate,
            access = access,
            onTheme = onThemeChange,
            onThemes = {
                settingsOpen = false
                themesOpen = true
            },
            onSettings = { next ->
                val autoWasOff = !settings.autoBackup
                val advancedChanged = next.advancedMode != settings.advancedMode
                settings = next
                settingsStore.save(next)
                // The switch here and the one on the Theme page are the same setting, so
                // advanced off sets the advanced look aside from either place.
                if (advancedChanged) {
                    Bundles.advancedOn.value = next.advancedMode
                    cardPhotos.publish()
                }
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
            backupProtected = backupProtected,
            lastBackupBytes = remember(drive) { driveStore.lastBackupBytes() },
            onBackupPassword = { changeBackupPassword(it) },
            onExport = { exportVault() },
            onImport = {
                onExternal()
                restorePicker.launch(
                    BACKUP_MIME_TYPES,
                )
            },
            onClearData = { clearVaultData() },
            onProfile = {
                settingsOpen = false
                profileOpen = true
            },
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
            onExternal = onExternal,
            photos = photoItems,
        )
    } else {
        // Back closes the menu first.
        BackHandler(enabled = drawerState.isOpen) { closeMenu() }

        ModalNavigationDrawer(
            drawerState = drawerState,
            // Swipes belong to the tab pager; the menu opens from its button and can be
            // swiped or tapped away once it is open.
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                MenuDrawer(
                    profile = profile,
                    version = currentVersion,
                    updateReady = availableUpdate != null,
                    onProfile = {
                        // The admin may have fixed names since this phone last looked.
                        scope.launch {
                            AccessStore(context).refresh()
                            namesFixed = AccessStore(context).namesFixed()
                        }
                        closeMenu { profileOpen = true }
                    },
                    onSettings = { closeMenu { settingsOpen = true } },
                    isAdmin = isAdmin,
                    onAdmin = { closeMenu { adminOpen = true } },
                    canTheme = access.theme,
                    onThemes = { closeMenu { themesOpen = true } },
                    signedIn = signedIn,
                    tick = access.tick,
                    onSignIn = { closeMenu { adminOpen = true } },
                    onBackup = { closeMenu { backupNow() } },
                    onCheckUpdates = { closeMenu { checkUpdatesNow() } },
                    onAbout = { closeMenu { aboutOpen = true } },
                )
            },
        ) {
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
                    onMenu = { scope.launch { drawerState.open() } },
                    unreadNotifications = unreadPushes,
                    onNotifications = { inboxOpen = true },
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
                    // One page either side. Composing all five meant every tab's grid, its
                    // bitmaps and its Backdrop were built and kept alive while any one page
                    // moved, which is what made a fast swipe stutter.
                    beyondViewportPageCount = 1,
                    // A flick always completes the page change and lands centred, so a half
                    // swipe can no longer leave the content sitting between two tabs.
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        pagerSnapDistance = PagerSnapDistance.atMost(1),
                    ),
                    key = { Tab.entries[it].name },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val currentTab = Tab.entries[page]
                    val isHome = currentTab == Tab.HOME
                    val isReels = currentTab == Tab.REELS
                    val isNotesChat = currentTab == Tab.NOTES && chatView
                    val currentVisibleFiles = sortedItems.filter { item ->
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
                                favourites = sortedItems.filter { it.favourite },
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
                            onReminders = if (isNotesChat) { { remindersOpen = true } } else null,
                            sortOrder = if (isReels || isNotesChat) null else sortOrder,
                            onSort = { chosen ->
                                sortOrder = chosen
                                sortPrefs.edit().putString("file_sort", chosen.name).apply()
                            },
                        )

                        if (selecting) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.Screen, vertical = 4.dp)
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
                                // Chats are folders of messages, so there is nothing to hand
                                // to another app; files and reel links can be shared.
                                if (!isNotesChat) {
                                    TextButton(
                                        onClick = {
                                            onExternal()
                                            val opened = if (isReels) {
                                                shareReelLinks(
                                                    context,
                                                    reels.filter { it.file.absolutePath in selectedPaths },
                                                )
                                            } else {
                                                shareVaultFiles(
                                                    context,
                                                    sortedItems.filter { it.file.absolutePath in selectedPaths },
                                                )
                                            }
                                            if (opened) {
                                                cancelSelection()
                                            } else {
                                                Toast.makeText(context, "Could not share the selection", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = selectedPaths.isNotEmpty(),
                                    ) {
                                        Text("Share")
                                    }
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
                            if (chatsAllowed) {
                                TextModeChips(chatView = chatView, onChange = { chatView = it })
                            }
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
                                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = floatingBarSpace() + 88.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                ) {
                                    currentVisibleReels.forEach { reel ->
                                        item(key = reel.file.absolutePath) {
                                            Box(Modifier.animateItem()) {
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
                                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = floatingBarSpace() + 88.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                ) {
                                    currentVisibleChats.forEach { chat ->
                                        item(key = chat.dir.absolutePath) {
                                            Box(Modifier.animateItem()) {
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
                                // Photos and PDFs can be cards (two across) or a list.
                                columns = GridCells.Fixed(if (settings.cardsAsList) 1 else 2),
                                contentPadding = PaddingValues(start = Spacing.Screen, end = Spacing.Screen, top = 4.dp, bottom = floatingBarSpace() + 88.dp),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.Card),
                                verticalArrangement = Arrangement.spacedBy(Spacing.Card),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                items(currentVisibleFiles, key = { it.file.absolutePath }) { item ->
                                    if (settings.cardsAsList) {
                                        VaultRow(
                                            modifier = Modifier.animateItem(),
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
                                            onLongClick = {
                                                if (selecting) {
                                                    toggleSelected(item.file.absolutePath)
                                                } else {
                                                    selecting = true
                                                    selectedPaths = setOf(item.file.absolutePath)
                                                }
                                            },
                                        )
                                        return@items
                                    }
                                    VaultCard(
                                        modifier = Modifier.animateItem(),
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
                                        onLongClick = {
                                            if (selecting) {
                                                toggleSelected(item.file.absolutePath)
                                            } else {
                                                selecting = true
                                                selectedPaths = setOf(item.file.absolutePath)
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
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = floatingBarSpace() + 12.dp),
                    ) {
                        FloatingActionButton(
                            onClick = { sectionAdd() },
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                        // Opens upwards from the + button (there is no room below it).
                        DropdownMenu(
                            expanded = addMenuOpen,
                            onDismissRequest = { addMenuOpen = false },
                        ) {
                            val forPdfs = tab == Tab.PDFS
                            if (forPdfs) {
                                AddMenuItem(Icons.Outlined.DocumentScanner, Accent.Pdf, "Scan document") {
                                    addMenuOpen = false
                                    launchScan()
                                }
                            }
                            AddMenuItem(Icons.Outlined.PhotoCamera, Accent.Photo, "Camera") {
                                addMenuOpen = false
                                launchCamera(asPdf = forPdfs)
                            }
                            if (forPdfs) {
                                AddMenuItem(Icons.Outlined.FolderOpen, Accent.Note, "Files") {
                                    addMenuOpen = false
                                    launchPdfs()
                                }
                                // Some phones, and browsers' download pages, only answer to a
                                // plain file request; this opens the same picker showing every
                                // file, with PDFs first.
                                AddMenuItem(
                                    Icons.Outlined.Description,
                                    Accent.Pdf,
                                    "All files",
                                ) {
                                    addMenuOpen = false
                                    launchPdfs(anyFile = true)
                                }
                            } else {
                                AddMenuItem(Icons.Outlined.Image, Accent.Photo, "Gallery") {
                                    addMenuOpen = false
                                    launchPhotos()
                                }
                            }
                        }
                    }
                }

                // Drawn over the page, so content scrolls behind the floating capsule.
                PagerBottomBar(
                    pagerState = pagerState,
                    onSelect = { target -> switchTab(target) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                }
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
                    onScan = {
                        addOpen = false
                        launchScan()
                    },
                    onCamera = {
                        addOpen = false
                        launchCamera(asPdf = false)
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
                    onDismiss = {
                        noteOpen = false
                        sharedNote = ""
                    },
                    startTitle = sharedNote.lineSequence().firstOrNull()?.take(40).orEmpty(),
                    startBody = sharedNote,
                    onSave = { title, body ->
                        noteOpen = false
                        sharedNote = ""
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

            if (remindersOpen) {
                RemindersSheet(
                    // Reloads when a reminder comes due while the list is open.
                    refreshKey = reminderTick,
                    onOpenChat = { reminder ->
                        val target = chats.firstOrNull { it.dir.absolutePath == reminder.chatDir }
                            ?: chats.firstOrNull { it.name == reminder.chatName }
                        if (target == null) {
                            Toast.makeText(context, "That chat no longer exists", Toast.LENGTH_SHORT).show()
                        } else {
                            remindersOpen = false
                            openChat = target
                        }
                    },
                    onDismiss = { remindersOpen = false },
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
                key(profileSheetKey) {
                ProfileSheet(
                    // The key above rebuilds the sheet whenever the profile changes, so what is
                    // on screen is always what is saved rather than a leftover draft.
                    name = profile.name,
                    avatar = profile.avatar,
                    drive = drive,
                    backupAllowed = !grantsActive || access.has(AccessFeature.BACKUP),
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
                            BACKUP_MIME_TYPES,
                        )
                    },
                    onExternal = onExternal,
                    email = GoogleAccountStore(context).email().orEmpty(),
                    tick = access.tick,
                    username = access.username,
                    onSignOut = {
                        signOutOfAccount(context)
                        signedIn = false
                        access = MyAccess()
                        profileOpen = false
                        profileSheetKey += 1
                        // Straight to the login page: a signed-out phone should not land in
                        // the vault as a stranger.
                        requireSignIn()
                    },
                    onGoogleSignedIn = { user ->
                        scope.launch { isAdmin = AdminStore(context).refreshFromServer() }
                        // Only fills gaps: a name or picture the user already chose is kept.
                        var changed = false
                        if (profile.name.isBlank() && user.name.isNotBlank()) {
                            profileStore.setName(user.name.take(40))
                            profile = profileStore.load()
                            changed = true
                        }
                        if (changed) {
                            autoBackupTick += 1
                            driveStore.markPending()
                        }
                        if (profile.avatar == null && user.picture.isNotBlank()) {
                            scope.launch {
                                val saved = downloadGooglePicture(context, user.picture)?.let { file ->
                                    withContext(Dispatchers.IO) {
                                        profileStore.saveAvatar(Uri.fromFile(file)).also { file.delete() }
                                    }
                                }
                                if (saved != null && profile.avatar == null) {
                                    profile = profile.copy(avatar = saved)
                                    autoBackupTick += 1
                                    driveStore.markPending()
                                }
                            }
                        }
                    },
                    onNameChanged = {
                        // The server name moved: pick the new one up for the header and the
                        // settings rows without waiting for a restart.
                        scope.launch { access = AccessStore(context).refreshGrant() }
                    },
                    onSave = { name ->
                        val changed = name != profile.name
                        profileStore.setName(name)
                        profile = profileStore.load()
                        profileOpen = false
                        // A rebuilt sheet, so no half-filled field survives into the next look.
                        profileSheetKey += 1
                        if (changed) {
                            autoBackupTick += 1
                            driveStore.markPending()
                        }
                    },
                    onDismiss = {
                        profileOpen = false
                        // Signing in or out on the sheet can change this.
                        signedIn = GoogleAccountStore(context).email() != null
                        scope.launch {
                            access = AccessStore(context).refreshTicks()
                                            }
                    },
                )
                }
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

            if (connectPrompt) {
                AlertDialog(
                    onDismissRequest = {
                        driveStore.markConnectPromptShown()
                        connectPrompt = false
                    },
                    title = { Text("Back up to Google Drive?") },
                    text = {
                        Text("Your vault is only on this phone. If the phone is lost or the app is removed, everything in it is gone. Connect a Google Drive folder to keep a backup.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                driveStore.markConnectPromptShown()
                                connectPrompt = false
                                connectDrive()
                            },
                        ) {
                            Text("Connect Drive")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                driveStore.markConnectPromptShown()
                                connectPrompt = false
                            },
                        ) {
                            Text("Later")
                        }
                    },
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
    }

    if (aboutOpen) {
        AboutDialog(
            version = currentVersion,
            onWebsite = {
                aboutOpen = false
                openUrl(context, "https://muhammadshanp.github.io/Pre-release-/")
            },
            onDismiss = { aboutOpen = false },
        )
    }

    val pendingRestore = restoreUri
    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = { if (!drive.busy) restoreUri = null },
            title = { Text("Restore backup?") },
            text = {
Text("This adds everything from the selected backup to your vault. Files already on this phone are kept.")
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

    val passwordRestore = restorePasswordFor
    if (passwordRestore != null) {
        var typed by remember(passwordRestore) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { restorePasswordFor = null },
            title = { Text("Backup password") },
            text = {
                Column {
                    Text("This backup is password-protected. Type the password it was made with.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = {
                            typed = it
                            restorePasswordError = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        isError = restorePasswordError != null,
                        supportingText = if (restorePasswordError != null) {
                            { Text(restorePasswordError.orEmpty()) }
                        } else {
                            null
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        restorePasswordFor = null
                        restoreBackup(passwordRestore, typed)
                    },
                    enabled = typed.isNotEmpty() && !drive.busy,
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { restorePasswordFor = null }) { Text("Cancel") }
            },
        )
    }

    // ---- Chat reminders ---------------------------------------------------
    var dueReminders by remember { mutableStateOf<List<Reminder>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Reminders.rearmAll(context) }
    }
    LaunchedEffect(reminderTick) {
        dueReminders = withContext(Dispatchers.IO) {
            ReminderStore.list(context).filter { it.fired }.sortedBy { it.at }
        }
    }
    val dueReminder = dueReminders.firstOrNull()
    if (dueReminder != null) {
        val reminderChat = chats.firstOrNull { it.dir.absolutePath == dueReminder.chatDir }
            ?: chats.firstOrNull { it.name == dueReminder.chatName }
        fun finishReminder() {
            Reminders.cancel(context, dueReminder)
            dueReminders = dueReminders.filterNot { it.id == dueReminder.id }
        }
        AlertDialog(
            onDismissRequest = { finishReminder() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Reminder") },
            text = {
                Column {
                    Text(
                        text = dueReminder.chatName,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(text = dueReminder.text, maxLines = 8, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Set for " + reminderTimeLabel(context, dueReminder.at),
                        color = TextSoft,
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                if (reminderChat != null) {
                    TextButton(
                        onClick = {
                            finishReminder()
                            settingsOpen = false
                            inboxOpen = false
                            open = null
                            openChat = reminderChat
                        },
                    ) {
                        Text("Open chat")
                    }
                } else {
                    TextButton(onClick = { finishReminder() }) { Text("Done") }
                }
            },
            dismissButton = {
                if (reminderChat != null) {
                    TextButton(onClick = { finishReminder() }) { Text("Done") }
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
                    "No vault data was found on this phone. If you backed up to Google Drive, restore the latest P_Vault backup now. Otherwise start fresh.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dismissRestorePrompt()
                        onExternal()
                        restorePicker.launch(
                            BACKUP_MIME_TYPES,
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
    favourites: List<VaultItem>,
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
    var allFavourites by remember { mutableStateOf(false) }
    val shownFavourites = if (allFavourites) favourites else favourites.take(FAVOURITES_ON_HOME)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = floatingBarSpace() + 88.dp),
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
                        kind = CardPicture.PHOTOS,
                        modifier = Modifier.weight(1f),
                    )
                    HomeTile(
                        icon = Icons.Outlined.PictureAsPdf,
                        tint = Accent.Pdf,
                        title = "PDFs",
                        detail = countLabel(pdfs, "PDF"),
                        onClick = { onOpenTab(Tab.PDFS) },
                        kind = CardPicture.PDFS,
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
                        kind = CardPicture.NOTES,
                        modifier = Modifier.weight(1f),
                    )
                    HomeTile(
                        icon = Icons.Outlined.PlayCircle,
                        tint = Accent.Reel,
                        title = "Reels",
                        detail = countLabel(reels.size, "reel"),
                        onClick = { onOpenTab(Tab.REELS) },
                        kind = CardPicture.REELS,
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

        if (favourites.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { HomeSectionTitle("Favourites") }
                    if (favourites.size > FAVOURITES_ON_HOME) {
                        TextButton(onClick = { allFavourites = !allFavourites }) {
                            Text(if (allFavourites) "Show less" else "Show all ${favourites.size}")
                        }
                    }
                }
            }
            shownFavourites.forEach { item ->
                item(key = "fav:" + item.file.absolutePath) {
                    HomeRecentRow(item = item, onClick = { onOpenItem(item) })
                }
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
    val context = LocalContext.current
    val preset = LocalThemePreset.current
    val colors = preset.gradient ?: listOf(Color(0xFFF0703F), Color(0xFFB9391A))
    val shape = RoundedCornerShape(28.dp)
    val heroPhoto = LocalThemeCards.current[CardPicture.HERO] ?: CardPhotoFiles.current.value[CardPicture.HERO]
    val heroPicture = if (heroPhoto != null) null else preset.cardPictures[CardPicture.HERO]
    val artwork = if (heroPicture != null) {
        remember(heroPicture) {
            BitmapFactory.decodeResource(context.resources, heroPicture)?.asImageBitmap()
        }
    } else if (preset.heroArt) {
        themeArtwork(preset)
    } else {
        null
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(colors)),
    ) {
        val ownPhotoArt = heroPhoto?.let { remember(it) { CardPictures.load(context.resources, null, it) } }
        if (ownPhotoArt != null) {
            // A photo of the user's own fills the card, cropped; the number and the label stay
            // where they are, over a wash that keeps them readable.
            Image(
                bitmap = ownPhotoArt,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.5f),
                            0.55f to Color.Black.copy(alpha = 0.15f),
                            1f to Color.Black.copy(alpha = 0.45f),
                        ),
                    ),
            )
        } else if (heroPicture != null && artwork != null) {
            // A card picture of its own covers the whole card, cropped to fill, so any
            // picture works whatever its size; the card keeps its size and shape.
            Image(
                bitmap = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.62f),
                            0.45f to Color.Black.copy(alpha = 0.32f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
        } else if (artwork != null) {
            // A theme picture (not a card picture) keeps the right-side treatment.
            Image(
                bitmap = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.62f)
                    .align(Alignment.CenterEnd),
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.62f)
                    .align(Alignment.CenterEnd)
                    .background(
                        Brush.horizontalGradient(
                            0f to colors.first(),
                            0.45f to colors.first().copy(alpha = 0.55f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            0f to colors.first().copy(alpha = 0.65f),
                            0.5f to Color.Transparent,
                        ),
                    ),
            )
        }
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
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
}

/**
 * The picture a theme wants inside its hero card, or null for Light, Dark and the colour
 * looks. A build-in picture is read as a bitmap once; a downloaded one comes from the
 * theme catalogue's files.
 */
@Composable
private fun themeArtwork(preset: ThemePreset): ImageBitmap? {
    val context = LocalContext.current
    val downloaded = LocalDownloadedThemes.current
    return when (val style = preset.backdrop) {
        is BackdropStyle.Remote -> {
            val theme = downloaded.firstOrNull { it.pictureUrl == style.url }
            val file = theme?.let { ThemeCatalogue(context).picture(it) }
            file?.let { ThemePictures.get(it)?.asImageBitmap() }
        }
        is BackdropStyle.Picture -> remember(style.image) {
            BitmapFactory.decodeResource(context.resources, style.image)?.asImageBitmap()
        }
        // A theme made on this phone carries its photos with it, so its backdrop is not an
        // artwork source here; the card photos come from LocalThemeCards instead.
        is BackdropStyle.LocalFile -> null
        BackdropStyle.Plain -> null
    }
}

@Composable
private fun HomeTile(
    kind: CardPicture,
    icon: ImageVector,
    tint: Color,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    // A theme made on this phone first, then a photo chosen for this card on its own.
    val photo = LocalThemeCards.current[kind] ?: CardPhotoFiles.current.value[kind]
    // A photo of the user's own is the whole card; the theme's picture sits behind a wash.
    val ownPhoto = photo != null
    val picture = if (ownPhoto) null else LocalThemePreset.current.cardPictures[kind]
    val overPicture = ownPhoto || picture != null
    // Over a picture the name and the count are white, whatever the theme's surface is.
    val onPicture = Color.White
    Column(
        modifier
            .vaultCard(
                shape = shape,
                picture = picture,
                file = photo,
                // Any picture on a tile is the card, with a wash over it for the label.
                pictureIsCard = overPicture,
            )
            .tileTint(tint, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        IconBadge(icon = icon, tint = tint)
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (overPicture) onPicture else Color.Unspecified,
            fontWeight = if (overPicture) FontWeight.Bold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = detail,
            color = if (overPicture) onPicture.copy(alpha = 0.9f) else TextSoft,
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

/** One line of the menu that opens from the + button. */
@Composable
private fun AddMenuItem(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        onClick = onClick,
    )
}

private const val FAVOURITES_ON_HOME = 4

/** Zips, and password-protected backups (which Drive lists as plain data files). */
private val BACKUP_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream",
)

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

/** The round profile picture (or a person icon when none is set). */
@Composable
private fun ProfileAvatar(profile: UserProfile, size: Dp, onClick: (() -> Unit)? = null) {
    val base = Modifier
        .size(size)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer)
    Box(
        if (onClick != null) base.clickable(onClick = onClick) else base,
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
                modifier = Modifier.size(size * 0.52f),
            )
        }
    }
}

/** The slide-in menu: who you are at the top, then the places you can go. */
@Composable
private fun MenuDrawer(
    profile: UserProfile,
    version: String,
    updateReady: Boolean,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    isAdmin: Boolean,
    onAdmin: () -> Unit,
    canTheme: Boolean,
    onThemes: () -> Unit,
    /** A Google account is signed in on this phone. */
    signedIn: Boolean,
    /** The tick this account holds, if any. */
    tick: AccessTick = AccessTick.None,
    /** Starts Google's own sign-in screen for the admin panel. */
    onSignIn: () -> Unit,
    onBackup: () -> Unit,
    onCheckUpdates: () -> Unit,
    onAbout: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.width(288.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp)) {
            ProfileAvatar(profile = profile, size = 60.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = profile.name.trim().ifBlank { "P Vault" },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Private vault · on this device",
                    color = TextSoft,
                    fontSize = 13.sp,
                )
                if (tick != AccessTick.None) {
                    Spacer(Modifier.width(5.dp))
                    TickBadge(tick, 14.dp)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
        Spacer(Modifier.height(10.dp))
        MenuDrawerItem(Icons.Outlined.Person, "Profile", onProfile)
        MenuDrawerItem(Icons.Outlined.Settings, "Settings", onSettings)
        // The Theme page itself is for everyone; only its picture looks need special access.
        MenuDrawerItem(Icons.Outlined.Palette, "Theme", onThemes)
        if (!signedIn) {
            MenuDrawerItem(Icons.Outlined.Login, "Sign in with Google", onSignIn)
        }
        MenuDrawerItem(Icons.Outlined.CloudUpload, "Back up now", onBackup)
        MenuDrawerItem(
            icon = Icons.Outlined.SystemUpdateAlt,
            label = if (updateReady) "Update available" else "Check for updates",
            onClick = onCheckUpdates,
            highlight = updateReady,
        )
        MenuDrawerItem(Icons.Outlined.Info, "About", onAbout)
        if (isAdmin) {
            MenuDrawerItem(Icons.Outlined.AdminPanelSettings, "Admin panel", onAdmin)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "Version $version",
            color = TextSoft,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun MenuDrawerItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    val tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun AboutDialog(version: String, onWebsite: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = { IconBadge(icon = Icons.Outlined.Lock, tint = MaterialTheme.colorScheme.primary, size = 56.dp, iconSize = 28.dp) },
        title = { Text("P Vault", textAlign = TextAlign.Center) },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Version $version",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "A private vault for photos, PDFs, notes and reels. Everything stays on this device unless you back it up to Google Drive.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "The PIN is an app lock, not encryption, and Drive backups are plain zip files.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = onWebsite) { Text("Website") } },
    )
}

@Composable
private fun ProfileHeader(
    profile: UserProfile,
    onProfile: () -> Unit,
    onMenu: () -> Unit,
    unreadNotifications: Int,
    onNotifications: () -> Unit,
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
    Column(Modifier.padding(horizontal = Spacing.Screen)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderButton(Icons.Default.Menu, "Menu", onMenu)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        name.isBlank() -> "Tap your picture to set up a profile"
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
            Spacer(Modifier.width(8.dp))
            if (showSearch && !searchOpen) {
                HeaderButton(Icons.Default.Search, "Search", onSearchOpen)
                Spacer(Modifier.width(8.dp))
            }
            Box {
                HeaderButton(
                    Icons.Outlined.Notifications,
                    if (unreadNotifications > 0) "Notifications, $unreadNotifications new" else "Notifications",
                    onNotifications,
                )
                if (unreadNotifications > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 9.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            ProfileAvatar(profile = profile, size = 44.dp, onClick = onProfile)
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
    sortOrder: SortOrder? = null,
    onSort: (SortOrder) -> Unit = {},
    onReminders: (() -> Unit)? = null,
) {
    var sortOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Screen, vertical = 6.dp),
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
        if (onReminders != null) {
            HeaderButton(Icons.Outlined.Alarm, "Reminders", onReminders)
            Spacer(Modifier.width(8.dp))
        }
        if (onManage != null) {
            HeaderButton(Icons.Outlined.Tune, manageDescription, onManage)
            Spacer(Modifier.width(8.dp))
        }
        if (sortOrder != null) {
            Box {
                HeaderButton(Icons.AutoMirrored.Filled.Sort, "Sort $title", { sortOpen = true })
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    SortOrder.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option.label,
                                    fontWeight = if (option == sortOrder) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (option == sortOrder) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            },
                            trailingIcon = {
                                if (option == sortOrder) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            onClick = {
                                sortOpen = false
                                onSort(option)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        HeaderButton(Icons.Default.Edit, "Select $title", onSelect)
    }
}

/** "3 photos added", or how many were skipped when some could not be imported. */
private fun addedMessage(added: Int, picked: Int, unit: String): String {
    val noun = if (added == 1) unit else "${unit}s"
    return when {
        added == 0 -> "Nothing could be added"
        added < picked -> "$added $noun added, ${picked - added} skipped"
        else -> "$added $noun added"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultCard(
    modifier: Modifier = Modifier,
    item: VaultItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val marked = selecting && selected
    Box(
        modifier
            .aspectRatio(0.86f)
            .vaultCard(RoundedCornerShape(22.dp), marked)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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

        if (item.favourite && !selecting) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Favourite",
                    tint = FavouriteGold,
                    modifier = Modifier.size(16.dp),
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
                        Tab.IMAGES -> "Tap + to take a photo or import from your gallery."
                        Tab.PDFS -> "Tap + to scan a document or import PDF files."
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

/** Shared files, sorted by what the vault can keep: photos, PDFs, and everything else. */
/**
 * True when shared text is a link to a reel, or is only a link plus a line about it. A link to
 * anything else, or plain text, belongs in a note instead.
 */
private fun looksLikeReel(text: String): Boolean {
    val link = extractUrl(text) ?: return false
    val host = runCatching { Uri.parse(link).host.orEmpty().lowercase() }.getOrDefault("")
    val reelHost = host.contains("instagram") || host.contains("instagr.am") ||
        host.contains("youtube.com") || host.contains("youtu.be") ||
        host.contains("facebook.com") || host.contains("tiktok.com") ||
        host.contains("twitter.com") || host.contains("x.com")
    if (!reelHost) return false
    // The rest of the message, if any, must be a short caption rather than a paragraph.
    val rest = text.replace(link, "").replace("\n", " ").trim()
    return rest.length <= 120
}

private data class SharedSet(val photos: List<Uri>, val pdfs: List<Uri>, val refused: Int)

private fun sortedShared(context: Context, uris: List<Uri>): SharedSet {
    val photos = ArrayList<Uri>()
    val pdfs = ArrayList<Uri>()
    var refused = 0
    uris.forEach { uri ->
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        val name = uri.lastPathSegment.orEmpty().lowercase()
        when {
            mime.startsWith("image/") -> photos.add(uri)
            mime == "application/pdf" -> pdfs.add(uri)
            // A few apps hand over a file with no type at all; the name is all there is to go on.
            mime.isEmpty() && name.substringAfterLast('.', "") in setOf(
                "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif",
            ) -> photos.add(uri)
            mime.isEmpty() && name.endsWith(".pdf") -> pdfs.add(uri)
            else -> refused += 1
        }
    }
    return SharedSet(photos, pdfs, refused)
}

private data class VaultSnapshot(
    val files: List<VaultItem>,
    val reels: List<Reel>,
    val categories: List<String>,
    val chats: List<ChatThread>,
)

/**
 * One item as a row: its picture or badge, its name and its type, with the same selection
 * ring as the card. It is the list form of [VaultCard], so the two can be swapped freely.
 */
@Composable
private fun VaultRow(
    modifier: Modifier = Modifier,
    item: VaultItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val marked = selecting && selected
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .vaultCard(shape, marked)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (item.type == ItemType.IMAGE) {
                AsyncImage(
                    model = item.file,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                IconBadge(
                    icon = if (item.type == ItemType.PDF) {
                        Icons.Outlined.PictureAsPdf
                    } else {
                        Icons.AutoMirrored.Outlined.StickyNote2
                    },
                    tint = accentOf(item.type),
                    size = 42.dp,
                    iconSize = 20.dp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                when (item.type) {
                    ItemType.IMAGE -> "Photo"
                    ItemType.PDF -> "PDF"
                    ItemType.TEXT -> "Note"
                },
                color = TextSoft,
                fontSize = 11.sp,
            )
        }
        if (item.favourite && !selecting) {
            Icon(
                Icons.Default.Star,
                contentDescription = "Favourite",
                tint = FavouriteGold,
                modifier = Modifier.size(18.dp),
            )
        }
        if (selecting) {
            Box(
                Modifier
                    .padding(start = 6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .border(
                        1.5.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
