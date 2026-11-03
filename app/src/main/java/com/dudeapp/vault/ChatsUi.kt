package com.dudeapp.vault

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
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
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
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = chatView, onClick = { onChange(true) }, label = { Text("Chats") })
        FilterChip(selected = !chatView, onClick = { onChange(false) }, label = { Text("Notes") })
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
    val shape = RoundedCornerShape(22.dp)
    val marked = selecting && selected
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (marked) 2.dp else 1.dp,
                color = if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatAvatar(chat, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = chat.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = chat.lastMessage.ifBlank { "No messages yet" },
                color = TextSoft,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selecting) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                ChatAvatar(chat, 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = chat.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Local chat",
                        color = TextSoft,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { pictureDialogOpen = true }) {
                    Icon(
                        imageVector = Icons.Outlined.AddAPhoto,
                        contentDescription = "Edit chat picture",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

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
                    Text(
                        text = "1 selected",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
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
                    items(messages, key = { it.file.absolutePath }) { message ->
                        MessageBubble(
                            message = message,
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
                        .padding(horizontal = 16.dp),
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
                        .padding(horizontal = 16.dp),
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

            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (editing == null) {
                    IconButton(
                        onClick = {
                            onExternal()
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        enabled = !recording,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = "Share a photo",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            when {
                                recording -> stopRecording(true)
                                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED -> startRecording()
                                else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        enabled = !sending,
                    ) {
                        Icon(
                            imageVector = if (recording) Icons.Default.Stop else Icons.Outlined.Mic,
                            contentDescription = if (recording) {
                                "Stop and send voice note"
                            } else {
                                "Record a voice note"
                            },
                            tint = if (recording) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(if (editing != null) "Edit message" else "Message") },
                    maxLines = 4,
                    enabled = !recording,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { send() },
                    enabled = draft.isNotBlank() && !sending && !recording,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = if (editing != null) {
                            Icons.Default.Check
                        } else {
                            Icons.AutoMirrored.Filled.Send
                        },
                        contentDescription = if (editing != null) "Save edit" else "Send",
                    )
                }
            }
        }
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
    selected: Boolean,
    playing: Boolean,
    onPlayVoice: () -> Unit,
    onOpenPhoto: () -> Unit,
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
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
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    },
                )
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    },
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
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "Voice message",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = formatDuration(message.durationMs),
                                color = TextSoft,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                else -> Text(message.text, fontSize = 15.sp)
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = listTimeOf(context, message.stamp),
                    color = TextSoft,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Saved on device",
                    tint = TextSoft,
                    modifier = Modifier.size(12.dp),
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
