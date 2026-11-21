package com.dudeapp.vault

import androidx.compose.material.icons.outlined.ContentCopy
import android.os.PersistableBundle
import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.ClipData
import android.text.format.DateUtils
import android.app.TimePickerDialog
import android.app.DatePickerDialog
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Date

@Composable
fun TextModeChips(chatView: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Screen, vertical = 4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        listOf(true to "Chats", false to "Notes").forEach { (mode, label) ->
            val active = chatView == mode
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                    )
                    .clickable { onChange(mode) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
fun ChatRow(
    chat: ChatThread,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .vaultCard(RoundedCornerShape(20.dp), selecting && selected)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatAvatar(chat, 50.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = chat.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = chat.lastMessage.ifBlank { "No messages yet" },
                color = TextSoft,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selecting) {
            Spacer(Modifier.width(8.dp))
            SelectionDot(selected = selected)
        } else if (chat.lastStamp > 0L) {
            Spacer(Modifier.width(8.dp))
            Text(listTimeOf(context, chat.lastStamp), color = TextSoft, fontSize = 11.sp)
        }
    }
}

@Composable
fun AddChatDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("New chat") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("Chat name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ChatPictureDialog(
    hasPicture: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Chat picture") },
        text = {
            Text(
                if (hasPicture) {
                    "Change or remove this chat's picture."
                } else {
                    "Choose a picture for this chat."
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onPick()
                },
            ) {
                Text(if (hasPicture) "Change" else "Choose")
            }
        },
        dismissButton = {
            Row {
                if (hasPicture) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onRemove()
                        },
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatManagerSheet(
    chats: List<ChatThread>,
    onPickAvatar: (ChatThread) -> Unit,
    onRemoveAvatar: (ChatThread) -> Unit,
    onRename: (ChatThread, String) -> Unit,
    onDelete: (ChatThread) -> Unit,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf<ChatThread?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ChatThread?>(null) }
    var editingPicture by remember { mutableStateOf<ChatThread?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
        ) {
            Text(
                text = "Chats",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Set a picture, rename a chat, or remove it with all its messages.",
                color = TextSoft,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))

            if (chats.isEmpty()) {
                Text("No chats yet.", color = TextSoft, fontSize = 13.sp)
            }

            chats.forEach { chat ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatAvatar(chat, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chat.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (chat.messageCount == 1) {
                                "1 message"
                            } else {
                                "${chat.messageCount} messages"
                            },
                            color = TextSoft,
                            fontSize = 11.sp,
                        )
                    }
                    IconButton(onClick = { editingPicture = chat }) {
                        Icon(
                            imageVector = Icons.Outlined.AddAPhoto,
                            contentDescription = "Edit picture for ${chat.name}",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = {
                        renaming = chat
                        renameDraft = chat.name
                    }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename ${chat.name}",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = { deleting = chat }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete ${chat.name}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    val renameTarget = renaming
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(30) },
                    label = { Text("Chat name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(renameTarget, renameDraft.trim())
                        renaming = null
                    },
                    enabled = renameDraft.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("Cancel") }
            },
        )
    }

    val deleteTarget = deleting
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete \"${deleteTarget.name}\"?") },
            text = { Text("All messages in this chat will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(deleteTarget)
                        deleting = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }

    val pictureTarget = editingPicture
    if (pictureTarget != null) {
        ChatPictureDialog(
            hasPicture = pictureTarget.avatar != null,
            onPick = { onPickAvatar(pictureTarget) },
            onRemove = { onRemoveAvatar(pictureTarget) },
            onDismiss = { editingPicture = null },
        )
    }
}

@Composable
fun ChatScreen(
    chat: ChatThread,
    store: VaultStore,
    onExternal: () -> Unit,
    onPickPicture: () -> Unit,
    onRemovePicture: () -> Unit,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val preColors = preReColors()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var messages by remember(chat.dir.absolutePath) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var selected by remember(chat.dir.absolutePath) { mutableStateOf<ChatMessage?>(null) }
    var editing by remember(chat.dir.absolutePath) { mutableStateOf<ChatMessage?>(null) }
    var deleting by remember(chat.dir.absolutePath) { mutableStateOf<ChatMessage?>(null) }
    var pictureDialogOpen by remember(chat.dir.absolutePath) { mutableStateOf(false) }
    var photoViewer by remember { mutableStateOf<File?>(null) }
    var reminders by remember { mutableStateOf(ReminderStore.list(context)) }
    var remindingAbout by remember(chat.dir.absolutePath) { mutableStateOf<ChatMessage?>(null) }
    // Android 13+ asks before P Vault may show notifications. The reminder is kept
    // either way; without the permission it only appears as a pop-up inside the app.
    val reminderPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                "Notifications are off, so the reminder will only show inside P Vault.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingFile by remember { mutableStateOf<File?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recordingStarted by remember { mutableLongStateOf(0L) }
    var recordingElapsed by remember { mutableLongStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(chat.dir.absolutePath) {
        messages = withContext(Dispatchers.IO) { store.chatMessages(chat.name) }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }
    LaunchedEffect(editing) {
        if (editing != null) focusRequester.requestFocus()
    }

    fun startRecording() {
        if (recording) return
        val file = File(chat.dir, "recording_${System.nanoTime()}.m4a")
        val rec = newVoiceRecorder(context)
        val started = runCatching {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(64_000)
            rec.setAudioSamplingRate(44_100)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
        }.isSuccess
        if (!started) {
            rec.release()
            file.delete()
            Toast.makeText(context, "Could not start recording", Toast.LENGTH_SHORT).show()
            return
        }
        recorder = rec
        recordingFile = file
        recordingStarted = System.currentTimeMillis()
        recordingElapsed = 0L
        recording = true
    }

    fun stopRecording(send: Boolean) {
        val rec = recorder ?: return
        val file = recordingFile
        val duration = (System.currentTimeMillis() - recordingStarted).coerceAtLeast(0L)
        recording = false
        recorder = null
        recordingFile = null
        val stopped = runCatching { rec.stop() }.isSuccess
        rec.release()
        if (!stopped || !send || duration < 500L) {
            file?.delete()
            if (send && stopped && duration < 500L) {
                Toast.makeText(context, "Hold longer to record a voice note", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (file == null || file.length() == 0L) {
            file?.delete()
            Toast.makeText(context, "Could not save that voice note", Toast.LENGTH_SHORT).show()
            return
        }
        val saved = store.addVoiceMessage(chat.name, file, duration)
        if (saved != null) {
            messages = messages + saved
            onChanged()
        } else {
            Toast.makeText(context, "Could not save that voice note", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleVoice(message: ChatMessage) {
        val attachment = message.attachment ?: return
        if (playingFile?.absolutePath == attachment.absolutePath) {
            player?.release()
            player = null
            playingFile = null
            return
        }
        player?.release()
        player = null
        playingFile = null
        val next = MediaPlayer()
        val started = runCatching {
            next.setDataSource(attachment.absolutePath)
            next.prepare()
            next.start()
        }.isSuccess
        if (!started) {
            next.release()
            Toast.makeText(context, "Could not play that voice note", Toast.LENGTH_SHORT).show()
            return
        }
        next.setOnCompletionListener {
            next.release()
            if (player === next) {
                player = null
                playingFile = null
            }
        }
        player = next
        playingFile = attachment
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startRecording()
        } else {
            Toast.makeText(context, "Microphone permission is needed for voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { store.addPhotoMessage(chat.name, uri) }
                if (saved != null) {
                    messages = messages + saved
                    onChanged()
                } else {
                    Toast.makeText(context, "Could not add that photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recorder?.stop() }
            recorder?.release()
            recordingFile?.delete()
            player?.release()
        }
    }

    LaunchedEffect(recording) {
        while (recording) {
            recordingElapsed = System.currentTimeMillis() - recordingStarted
            delay(100)
        }
    }

    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && recording) stopRecording(send = false)
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    BackHandler {
        when {
            recording -> stopRecording(send = false)
            editing != null -> {
                editing = null
                draft = ""
            }
            selected != null -> selected = null
            else -> onBack()
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        sending = true
        val target = editing
        // shortcut: one tiny file per message on the UI thread; move to IO if messages get large.
        val saved = if (target != null) {
            store.editMessage(target, text)
        } else {
            store.addMessage(chat.name, text)
        }
        if (saved != null) {
            if (target != null) {
                messages = messages.map { if (it.file == target.file) saved else it }
                editing = null
            } else {
                messages = messages + saved
            }
            draft = ""
            onChanged()
        } else {
            Toast.makeText(
                context,
                if (target != null) "Could not update that message" else "Could not save that message",
                Toast.LENGTH_SHORT,
            ).show()
        }
        sending = false
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            // Header from the design: 56dp, chevron, 32dp avatar, name over a 50% line.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(preColors.page)
                    .padding(start = 4.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = preColors.ink,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                ChatAvatar(chat, 32.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = chat.name,
                        color = preColors.ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The name this phone claimed, with the owner's tick when it has one.
                    val identityPrefs = remember {
                        context.getSharedPreferences("pre_re_supabase", android.content.Context.MODE_PRIVATE)
                    }
                    var identity by remember {
                        mutableStateOf(identityPrefs.getString("claimed_name", null) to identityPrefs.getBoolean("verified", false))
                    }
                    DisposableEffect(identityPrefs) {
                        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                            if (key == "claimed_name" || key == "verified") {
                                identity = identityPrefs.getString("claimed_name", null) to identityPrefs.getBoolean("verified", false)
                            }
                        }
                        identityPrefs.registerOnSharedPreferenceChangeListener(listener)
                        onDispose { identityPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = identity.first?.let { "@$it" } ?: "On this phone",
                            color = preColors.ink.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (identity.first != null && identity.second) {
                            Spacer(Modifier.width(3.dp))
                            VerifiedTick(12.dp)
                        }
                    }
                }
                IconButton(onClick = { pictureDialogOpen = true }) {
                    Icon(
                        imageVector = Icons.Outlined.AddAPhoto,
                        contentDescription = "Edit chat picture",
                        tint = preColors.ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(preColors.divider),
            )

            val selectedMessage = selected
            if (selectedMessage != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    // The buttons need the room, so the bar no longer spells out "1 selected".
                    Spacer(Modifier.weight(1f))
                    val hasReminder = reminders.any {
                        it.messagePath == selectedMessage.file.absolutePath && !it.fired
                    }
                    TextButton(onClick = { remindingAbout = selectedMessage }) {
                        Icon(
                            imageVector = Icons.Outlined.Alarm,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (hasReminder) "Reminder set" else "Remind me", fontSize = 13.sp)
                    }
                    if (selectedMessage.type == ChatMessageType.TEXT) {
                        IconButton(onClick = {
                            copyMessageText(context, selectedMessage.text)
                            selected = null
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy message",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (selectedMessage.type == ChatMessageType.TEXT) {
                        IconButton(onClick = {
                            editing = selectedMessage
                            draft = selectedMessage.text
                            selected = null
                        }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit message",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    IconButton(onClick = {
                        deleting = selectedMessage
                        selected = null
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete message",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = { selected = null }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear selection",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (messages.isEmpty()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No messages yet. Write the first one.",
                        color = TextSoft,
                        fontSize = 13.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    itemsIndexed(messages, key = { _, m -> m.file.absolutePath }) { index, message ->
                        val previous = messages.getOrNull(index - 1)
                        val next = messages.getOrNull(index + 1)
                        if (previous == null || dayStamp(previous.stamp) != dayStamp(message.stamp)) {
                            Text(
                                text = dayLabel(context, message.stamp),
                                color = preColors.ink.copy(alpha = 0.45f),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 2.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                        MessageBubble(
                            message = message,
                            first = previous == null || dayStamp(previous.stamp) != dayStamp(message.stamp),
                            last = next == null || dayStamp(next.stamp) != dayStamp(message.stamp),
                            selected = selected?.file == message.file,
                            playing = playingFile?.absolutePath == message.attachment?.absolutePath,
                            onPlayVoice = { toggleVoice(message) },
                            onOpenPhoto = { message.attachment?.let { photoViewer = it } },
                            onSelect = {
                                selected = if (selected?.file == message.file) null else message
                            },
                        )
                    }
                }
            }

            if (editing != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Screen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Editing message",
                        color = TextSoft,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        editing = null
                        draft = ""
                    }) { Text("Cancel") }
                }
            }

            if (recording) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Screen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Recording ${formatDuration(recordingElapsed)} — tap stop to send",
                        color = TextSoft,
                        fontSize = 12.sp,
                    )
                }
            }

            // The design's footer: a hairline rule, then field and actions on one 40dp line.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(preColors.divider),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing == null) {
                    ComposerIcon(
                        icon = Icons.Outlined.Image,
                        description = "Share a photo",
                        enabled = !recording,
                        onClick = {
                            onExternal()
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    )
                    ComposerIcon(
                        icon = if (recording) Icons.Default.Stop else Icons.Outlined.Mic,
                        description = if (recording) {
                            "Stop and send voice note"
                        } else {
                            "Record a voice note"
                        },
                        enabled = !sending,
                        tint = if (recording) MaterialTheme.colorScheme.error else null,
                        onClick = {
                            when {
                                recording -> stopRecording(true)
                                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED -> startRecording()
                                else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    )
                    Spacer(Modifier.width(4.dp))
                }
                PreReChatField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = if (editing != null) "Edit message" else "Message...",
                    enabled = !recording,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.width(8.dp))
                val canSend = draft.isNotBlank() && !sending && !recording
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(PreReControlShape)
                        .background(if (canSend) preColors.primaryFill else preColors.border)
                        .clickable(enabled = canSend) { send() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (editing != null) {
                            Icons.Default.Check
                        } else {
                            Icons.AutoMirrored.Filled.Send
                        },
                        contentDescription = if (editing != null) "Save edit" else "Send",
                        tint = if (canSend) preColors.onPrimaryFill else preColors.muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    val remindTarget = remindingAbout
    if (remindTarget != null) {
        val existing = reminders.firstOrNull {
            it.messagePath == remindTarget.file.absolutePath && !it.fired
        }
        ReminderDialog(
            existingAt = existing?.at,
            onDismiss = { remindingAbout = null },
            onRemove = {
                existing?.let { Reminders.cancel(context, it) }
                reminders = ReminderStore.list(context)
                remindingAbout = null
                selected = null
                Toast.makeText(context, "Reminder removed", Toast.LENGTH_SHORT).show()
            },
            onPick = { at ->
                Reminders.set(context, remindTarget, chat, at)
                reminders = ReminderStore.list(context)
                remindingAbout = null
                selected = null
                Toast.makeText(
                    context,
                    "Reminder set for " + reminderTimeLabel(context, at),
                    Toast.LENGTH_LONG,
                ).show()
                if (!Reminders.canNotify(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    reminderPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }

    val deleteTarget = deleting
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete message?") },
            text = { Text("This message will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (store.deleteMessage(deleteTarget)) {
                            Reminders.cancelFor(context, deleteTarget.file.absolutePath)
                            reminders = ReminderStore.list(context)
                            messages = messages.filterNot { it.file == deleteTarget.file }
                            if (editing?.file == deleteTarget.file) {
                                editing = null
                                draft = ""
                            }
                            onChanged()
                        } else {
                            Toast.makeText(context, "Could not delete that message", Toast.LENGTH_SHORT).show()
                        }
                        deleting = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }

    if (pictureDialogOpen) {
        ChatPictureDialog(
            hasPicture = chat.avatar != null,
            onPick = onPickPicture,
            onRemove = onRemovePicture,
            onDismiss = { pictureDialogOpen = false },
        )
    }

    val viewingPhoto = photoViewer
    if (viewingPhoto != null) {
        Dialog(
            onDismissRequest = { photoViewer = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                AsyncImage(
                    model = viewingPhoto,
                    contentDescription = "Shared photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                )
                IconButton(
                    onClick = { photoViewer = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close photo",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    first: Boolean,
    last: Boolean,
    selected: Boolean,
    playing: Boolean,
    onPlayVoice: () -> Unit,
    onOpenPhoto: () -> Unit,
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    val preColors = preReColors()
    val shape = preReBubbleShape(mine = true, first = first, last = last)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(preColors.bubbleMine)
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = shape,
                )
                .combinedClickable(onClick = onSelect, onLongClick = onSelect)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            when {
                message.type == ChatMessageType.PHOTO && message.attachment != null -> {
                    Box {
                        AsyncImage(
                            model = message.attachment,
                            contentDescription = "Shared photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 240.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        IconButton(
                            onClick = onOpenPhoto,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "Open photo",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                message.type == ChatMessageType.VOICE && message.attachment != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onPlayVoice,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playing) "Pause voice note" else "Play voice note",
                                tint = preColors.onBubbleMine,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "Voice message",
                                color = preColors.onBubbleMine,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = formatDuration(message.durationMs),
                                color = preColors.onBubbleMine.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                else -> Text(message.text, fontSize = 15.sp, color = preColors.onBubbleMine)
            }
            Spacer(Modifier.height(2.dp))
            if (last) Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = listTimeOf(context, message.stamp),
                    color = preColors.onBubbleMine.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                )

            }
        }
    }
}

@Composable
private fun ChatAvatar(chat: ChatThread, size: Dp) {
    val avatar = chat.avatar
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Accent.Chat.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = "${chat.name} picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = "${chat.name} picture",
                tint = Accent.Chat,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun newVoiceRecorder(context: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0L)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private fun timeOf(context: Context, stamp: Long): String =
    DateFormat.getTimeFormat(context).format(Date(stamp))

private fun listTimeOf(context: Context, stamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = stamp }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        timeOf(context, stamp)
    } else {
        DateFormat.getDateFormat(context).format(Date(stamp))
    }
}

/** Midnight of the day a message was written, for grouping. */
private fun dayStamp(stamp: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = stamp
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

/** "Today", "Yesterday", or the date — the divider above a day's messages. */
private fun dayLabel(context: android.content.Context, stamp: Long): String {
    val today = dayStamp(System.currentTimeMillis())
    return when (dayStamp(stamp)) {
        today -> "Today"
        today - 86_400_000L -> "Yesterday"
        else -> DateUtils.formatDateTime(
            context,
            stamp,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL,
        )
    }
}

/** A composer action: 36dp tap target, 20dp glyph, ink at 70% unless told otherwise. */
@Composable
private fun ComposerIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val colors = preReColors()
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint ?: colors.ink.copy(alpha = if (enabled) 0.7f else 0.25f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** "Sat, 15 Nov, 9:00 am" in the phone's own date and time style. */
internal fun reminderTimeLabel(context: Context, at: Long): String =
    DateUtils.formatDateTime(
        context,
        at,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL,
    )

/** Lets the user choose when to be reminded: a ready-made time, or any date and time. */
@Composable
private fun ReminderDialog(
    existingAt: Long?,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val context = LocalContext.current
    val options = remember { reminderOptions(System.currentTimeMillis()) }

    fun pickDateAndTime() {
        val start = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
        val dates = DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val chosen = Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        if (chosen <= System.currentTimeMillis()) {
                            Toast.makeText(context, "Pick a time in the future", Toast.LENGTH_SHORT).show()
                        } else {
                            onPick(chosen)
                        }
                    },
                    start.get(Calendar.HOUR_OF_DAY),
                    start.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(context),
                ).show()
            },
            start.get(Calendar.YEAR),
            start.get(Calendar.MONTH),
            start.get(Calendar.DAY_OF_MONTH),
        )
        dates.datePicker.minDate = System.currentTimeMillis() - 1_000
        dates.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Remind me about this") },
        text = {
            Column {
                if (existingAt != null) {
                    Text(
                        "Reminder set for " + reminderTimeLabel(context, existingAt) + ". Pick a new time to change it.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                options.forEach { option ->
                    ReminderChoice(option.label) { onPick(option.at) }
                }
                ReminderChoice("Pick date and time…") { pickDateAndTime() }
                if (existingAt != null) {
                    ReminderChoice("Remove reminder", MaterialTheme.colorScheme.error, onRemove)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReminderChoice(label: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Text(
        text = label,
        color = color,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 13.dp),
    )
}

/** Every reminder that is set, soonest first, with the ones already due on top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersSheet(
    refreshKey: Int,
    onOpenChat: (Reminder) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var reminders by remember { mutableStateOf<List<Reminder>>(emptyList()) }
    var changing by remember { mutableStateOf<Reminder?>(null) }

    fun reload() {
        reminders = ReminderStore.list(context)
            .sortedWith(compareByDescending<Reminder> { it.fired }.thenBy { it.at })
    }
    LaunchedEffect(refreshKey) { reload() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
        ) {
            Text(
                text = "Reminders",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (reminders.isEmpty()) {
                    "No reminders yet. Hold a message in a chat and tap Remind me."
                } else {
                    "Tap a reminder to open its chat."
                },
                color = TextSoft,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))

            reminders.forEach { reminder ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenChat(reminder) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Alarm,
                        contentDescription = null,
                        tint = if (reminder.fired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = reminder.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = reminder.chatName + " · " +
                                (if (reminder.fired) "Due since " else "") +
                                reminderTimeLabel(context, reminder.at),
                            color = if (reminder.fired) MaterialTheme.colorScheme.error else TextSoft,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { changing = reminder }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Change time",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            Reminders.cancel(context, reminder)
                            reload()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove reminder",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    val target = changing
    if (target != null) {
        ReminderDialog(
            existingAt = target.at,
            onDismiss = { changing = null },
            onRemove = {
                Reminders.cancel(context, target)
                changing = null
                reload()
            },
            onPick = { at ->
                Reminders.reschedule(context, target, at)
                changing = null
                reload()
                Toast.makeText(
                    context,
                    "Reminder set for " + reminderTimeLabel(context, at),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }
}

/** Puts a message on the clipboard, marked private so Android does not show a preview of it. */
private fun copyMessageText(context: Context, text: String) {
    val copied = runCatching {
        val clip = ClipData.newPlainText("P Vault message", text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }.isSuccess
    // Android 13+ shows its own "copied" note.
    if (!copied || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, if (copied) "Copied" else "Could not copy", Toast.LENGTH_SHORT).show()
    }
}
