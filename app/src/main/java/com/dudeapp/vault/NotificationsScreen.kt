package com.dudeapp.vault

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Messages from the P Vault admin. Everyone can read them; the admin signs in at the
 * bottom of the screen to write or remove them.
 */
@Composable
fun NotificationsScreen(
    news: AnnouncementStore,
    items: List<Announcement>,
    unreadAfter: Int,
    onItems: (List<Announcement>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var admin by remember { mutableStateOf(news.isAdmin()) }
    var busy by remember { mutableStateOf(false) }
    var signInOpen by remember { mutableStateOf(false) }
    var composeOpen by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Announcement?>(null) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    fun refresh(quiet: Boolean = false) {
        if (busy) return
        busy = true
        scope.launch {
            val latest = news.fetch()
            busy = false
            if (latest != null) onItems(latest) else if (!quiet) toast("Could not load notifications. Check your connection.")
        }
    }

    BackHandler(onBack = onBack)

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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Notifications",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { refresh() }, enabled = !busy) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
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

                if (admin) {
                    Button(
                        onClick = { composeOpen = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("New notification")
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (items.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconBadge(
                            icon = Icons.Outlined.Notifications,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 64.dp,
                            iconSize = 30.dp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text("No notifications", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
                    val unread = item.number > unreadAfter
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
                        Row(
                            Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = if (admin) 4.dp else 16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                if (item.time > 0L) {
                                    Text(
                                        (if (unread) "New · " else "") + formatDay(item.time),
                                        color = if (unread) MaterialTheme.colorScheme.primary else TextSoft,
                                        fontSize = 12.sp,
                                    )
                                }
                                if (item.body.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(item.body, fontSize = 14.sp, lineHeight = 20.sp)
                                }
                            }
                            if (admin) {
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { removing = item }, enabled = !busy) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        if (admin) {
                            news.signOut()
                            admin = false
                            toast("Signed out")
                        } else {
                            signInOpen = true
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(if (admin) "Sign out of admin" else "Admin sign-in", color = TextSoft, fontSize = 12.sp)
                }
            }
        }
    }

    if (signInOpen) {
        var token by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) signInOpen = false },
            title = { Text("Admin sign-in") },
            text = {
                Column {
                    Text(
                        "For the P Vault owner only. Paste a GitHub token for the owner account with \"Issues: Read and write\" on the p-vault repository. It is stored only on this phone.",
                        fontSize = 13.sp,
                        color = TextSoft,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = {
                            token = it.trim()
                            error = null
                        },
                        label = { Text("GitHub token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val message = error
                    if (message != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && token.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            val problem = news.signIn(token)
                            busy = false
                            if (problem == null) {
                                admin = true
                                signInOpen = false
                                toast("Signed in as admin")
                            } else {
                                error = problem
                            }
                        }
                    },
                ) { Text(if (busy) "Checking…" else "Sign in") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { signInOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (composeOpen) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!busy) composeOpen = false },
            title = { Text("New notification") },
            text = {
                Column {
                    Text(
                        "Everyone who uses P Vault will see this. It is also public on GitHub.",
                        fontSize = 13.sp,
                        color = TextSoft,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it.take(80)
                            error = null
                        },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = body,
                        onValueChange = {
                            body = it.take(1000)
                            error = null
                        },
                        label = { Text("Message") },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val message = error
                    if (message != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && title.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            val problem = news.send(title, body)
                            if (problem == null) {
                                val latest = news.fetch()
                                busy = false
                                composeOpen = false
                                if (latest != null) {
                                    // The admin wrote it, so it is not "new" on this phone.
                                    latest.firstOrNull()?.let {
                                        news.markSeen(it.number)
                                        news.markNotified(it.number)
                                    }
                                    onItems(latest)
                                }
                                toast("Notification sent")
                            } else {
                                busy = false
                                error = problem
                            }
                        }
                    },
                ) { Text(if (busy) "Sending…" else "Send to everyone") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { composeOpen = false }) { Text("Cancel") }
            },
        )
    }

    val target = removing
    if (target != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) removing = null },
            title = { Text("Remove this notification?") },
            text = { Text("\"${target.title}\" will disappear for everyone the next time they open P Vault.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val problem = news.remove(target.number)
                            val latest = if (problem == null) news.fetch() else null
                            busy = false
                            removing = null
                            if (problem != null) {
                                toast(problem)
                            } else {
                                onItems(latest ?: items.filter { it.number != target.number })
                                toast("Notification removed")
                            }
                        }
                    },
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { removing = null }) { Text("Cancel") }
            },
        )
    }
}

private fun formatDay(time: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(time))
