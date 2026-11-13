package com.dudeapp.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The list behind the bell: every notification this phone has received, newest first. */
@Composable
fun InboxScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val inbox = remember { PushInbox(context) }
    // What was unread when the screen opened stays marked "New" while it is open.
    val unreadAfter = remember { inbox.seenTime() }
    val change = PushInbox.changes.intValue
    val items = remember(change) { inbox.items() }
    var clearOpen by remember { mutableStateOf(false) }

    val close = {
        inbox.markAllRead()
        onBack()
    }
    BackHandler(onBack = close)

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
                    .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = close) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Notifications",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (items.isNotEmpty()) {
                    TextButton(onClick = { clearOpen = true }) { Text("Clear all") }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            ) {
                Spacer(Modifier.height(10.dp))

                if (items.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconBadge(
                            icon = Icons.Outlined.Notifications,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 64.dp,
                            iconSize = 30.dp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text("No notifications yet", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "News from the P Vault team shows up here.",
                            color = TextSoft,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                items.forEach { item ->
                    val unread = item.time > unreadAfter
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (unread) 2.dp else 1.dp,
                            if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text(
                                item.title.ifBlank { "P Vault" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                (if (unread) "New · " else "") + formatWhen(item.time),
                                color = if (unread) MaterialTheme.colorScheme.primary else TextSoft,
                                fontSize = 12.sp,
                            )
                            if (item.body.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(item.body, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (clearOpen) {
        AlertDialog(
            onDismissRequest = { clearOpen = false },
            title = { Text("Clear all notifications?") },
            text = { Text("This removes the list from this phone only.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearOpen = false
                        inbox.clear()
                    },
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { clearOpen = false }) { Text("Cancel") } },
        )
    }
}

private fun formatWhen(time: Long): String =
    SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(time))
