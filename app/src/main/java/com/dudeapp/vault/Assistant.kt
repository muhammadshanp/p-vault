package com.dudeapp.vault

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The assistant: it looks through what is already on this phone and answers from that, here.
 * Nothing is sent anywhere and no account is needed, so it works offline and in aeroplane mode.
 */
@Composable
fun AssistantScreen(
    onOpenItem: (VaultItem) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { VaultStore(context) }
    var asked by remember { mutableStateOf("") }
    val chat = remember { AssistantChat(store) }
    val messages = chat.messages

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
                Column {
                    Text("Assistant", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Answers from this phone only · nothing is uploaded",
                        color = TextSoft,
                        fontSize = 11.sp,
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).navigationBarsPadding(),
            ) {
                items(messages, key = { it.id }) { message -> MessageBubble(message, onOpenItem) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = asked,
                    onValueChange = { asked = it.take(200) },
                    placeholder = { Text("Ask about your vault") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        chat.ask(asked)
                        asked = ""
                    }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { chat.ask(asked); asked = "" }, enabled = asked.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask")
                }
            }
        }
    }
}

/** One thing the assistant says: a line of text and, sometimes, the items it found. */
data class AssistantMessage(
    val id: Int,
    val fromUser: Boolean,
    val text: String,
    val items: List<VaultItem> = emptyList(),
)

/**
 * The assistant's memory of this visit, and the reading of the vault behind each answer. It is
 * a plain word search over the titles and file names, which is what works with no connection.
 */
class AssistantChat(private val store: VaultStore) {
    private var nextId = 0
    private val state = androidx.compose.runtime.mutableStateListOf(
        AssistantMessage(
            id = nextId++,
            fromUser = false,
            text = "Ask me what you have. Try \"how many photos\", \"my notes\" or \"find tax\".",
        ),
    )

    val messages: List<AssistantMessage> get() = state.toList()

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty()) return
        state += AssistantMessage(id = nextId++, fromUser = true, text = text)
        state += answer(text)
    }

    private fun answer(question: String): AssistantMessage {
        val files = store.list()
        val words = question.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 }
        val wanted = when {
            words.any { it in setOf("photo", "photos", "image", "images", "picture", "pictures") } -> ItemType.IMAGE
            words.any { it in setOf("pdf", "pdfs", "document", "documents") } -> ItemType.PDF
            words.any { it in setOf("note", "notes", "text", "texts") } -> ItemType.TEXT
            else -> null
        }
        val byType = if (wanted != null) files.filter { it.type == wanted } else files
        val found = if (words.isEmpty()) byType else byType.filter { item ->
            val haystack = (item.title + " " + item.file.name).lowercase()
            words.any { haystack.contains(it) }
        }
        val text = when {
            words.isEmpty() -> "You have ${files.size} things in the vault."
            found.isEmpty() -> "Nothing here matches \"${question.trim()}\"."
            wanted == ItemType.IMAGE -> "You have ${found.size} photo${if (found.size == 1) "" else "s"}:"
            wanted == ItemType.PDF -> "You have ${found.size} PDF${if (found.size == 1) "" else "s"}:"
            wanted == ItemType.TEXT -> "You have ${found.size} note${if (found.size == 1) "" else "s"}:"
            else -> "I found ${found.size} match${if (found.size == 1) "" else "es"}:"
        }
        return AssistantMessage(
            id = nextId++,
            fromUser = false,
            text = text,
            items = found.take(8),
        )
    }
}

@Composable
private fun MessageBubble(message: AssistantMessage, onOpenItem: (VaultItem) -> Unit) {
    val mine = message.fromUser
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
            )
            .border(
                1.dp,
                if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                shape,
            )
            .padding(12.dp),
    ) {
        Text(
            message.text,
            fontSize = 14.sp,
            color = if (mine) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
        )
        message.items.forEach { item ->
            val when_ = remember(item.stamp) {
                SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(item.stamp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenItem(item) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentOf(item.type)),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${item.type.name.lowercase()} · $when_",
                        color = TextSoft,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
