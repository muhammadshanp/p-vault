package com.dudeapp.vault

import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.AddAPhoto
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

private val avatarColors = listOf(
    Color(0xFF7C3AED) to Color(0xFFE1306C),
    Color(0xFF0EA5E9) to Color(0xFF22D3EE),
    Color(0xFFF59E0B) to Color(0xFFEF4444),
    Color(0xFF10B981) to Color(0xFF84CC16),
    Color(0xFFEC4899) to Color(0xFF8B5CF6),
    Color(0xFF14B8A6) to Color(0xFF3B82F6),
)

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
        ChatAvatar(chat, 52.dp, 20.sp)
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
                    ChatAvatar(chat, 42.dp, 16.sp)
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
    onPickPicture: () -> Unit,
    onRemovePicture: () -> Unit,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var messages by remember(chat.dir.absolutePath) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var selected by remember(chat.dir.absolutePath) { mutableStateOf<ChatMessage?>(null) }
    var editing by remember(chat.dir.absolutePath) { mutableStateOf<ChatMessage?>(null) }
    var deleting by remember(chat.dir.absolutePath) { mutableStateOf<ChatMessage?>(null) }
    var pictureDialogOpen by remember(chat.dir.absolutePath) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    BackHandler {
        when {
            editing != null -> {
                editing = null
                draft = ""
            }
            selected != null -> selected = null
            else -> onBack()
        }
    }

    LaunchedEffect(chat.dir.absolutePath) {
        messages = withContext(Dispatchers.IO) { store.chatMessages(chat.name) }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }
    LaunchedEffect(editing) {
        if (editing != null) focusRequester.requestFocus()
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
                ChatAvatar(chat, 38.dp, 15.sp)
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

            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(if (editing != null) "Edit message" else "Message") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { send() },
                    enabled = draft.isNotBlank() && !sending,
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    selected: Boolean,
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
            Text(message.text, fontSize = 15.sp)
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
private fun ChatAvatar(chat: ChatThread, size: Dp, fontSize: TextUnit) {
    val colors = avatarColors[(chat.name.hashCode() and Int.MAX_VALUE) % avatarColors.size]
    val avatar = chat.avatar
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (avatar == null) {
                    Modifier.background(Brush.linearGradient(listOf(colors.first, colors.second)))
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                },
            )
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
            Text(
                text = chat.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "#",
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
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
