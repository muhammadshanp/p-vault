package com.dudeapp.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import kotlinx.coroutines.launch

/**
 * The owner's user list: everybody who has claimed a name, with the Google address behind
 * it, when it was claimed, and what it may use.
 *
 * Two ways in, because they are the same question:
 *   - whoever already opened the panel with the passphrase hands it on, and the list is
 *     there without typing it again;
 *   - anyone else types the passphrase, exactly as the Access page does.
 *
 * A row can be opened for the full detail, and the tap that matters — setting a level —
 * is a jump to the Access page rather than a second copy of it. One place edits access.
 */
@Composable
internal fun AdminUsersCard(
    /** The passphrase the panel is already unlocked with, if any. */
    passphrase: String = "",
    /** Open the Access page, where a name's level is changed. */
    onOpenAccess: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var typed by remember { mutableStateOf(passphrase) }
    var users by remember { mutableStateOf<List<Supabase.AdminUser>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<Supabase.AdminUser?>(null) }
    var query by remember { mutableStateOf("") }

    fun load(with: String) {
        if (with.isBlank()) return
        busy = true
        problem = null
        scope.launch {
            val list = Supabase.adminUsers(context, with)
            busy = false
            if (list == null) {
                problem = "The passphrase was not accepted, or the server could not be reached."
                users = emptyList()
            } else {
                users = list
                typed = with
            }
        }
    }

    // The panel that already has the passphrase shows the list straight away, instead of
    // asking for the same passphrase on a second page.
    LaunchedEffect(passphrase) {
        if (passphrase.isNotBlank() && users.isEmpty()) load(passphrase)
    }

    Column(Modifier.fillMaxWidth()) {
        Text("Users", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Every name that has been claimed, the Google address it belongs to, and " +
                "what it can use. The list costs the owner passphrase and is never stored on " +
                "a phone.",
            color = TextSoft,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        if (users.isEmpty()) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it; problem = null },
                label = { Text("Owner passphrase") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { load(typed) },
                enabled = typed.isNotBlank() && !busy,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(if (busy) "Opening…" else "Show users")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${users.size} user${if (users.size == 1) "" else "s"}, " +
                            "${users.count { it.email != null }} with an address",
                        color = TextSoft,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "${users.count { it.verified }} blue, " +
                            "${users.count { it.partner }} black, " +
                            "${users.count { it.level != "NONE" }} with access",
                        color = TextSoft,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = { load(typed) }, enabled = !busy) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Reload the user list")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Searching by name or address: with more than a screenful, that is the only
            // thing anyone does with a user list.
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Find a name or address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            val needle = query.trim().lowercase()
            val shown = users.filter {
                needle.isEmpty() ||
                    it.username.contains(needle) ||
                    it.email.orEmpty().contains(needle)
            }
            if (shown.isEmpty()) {
                Text("Nobody matches that.", color = TextSoft, fontSize = 12.sp)
            }
            shown.forEach { user ->
                UserRow(user = user, onOpen = { open = user })
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "To change what someone can use, open Access and pick their name.",
                color = TextSoft,
                fontSize = 11.sp,
            )
            TextButton(onClick = onOpenAccess) { Text("Change access") }
        }

        val note = problem
        if (note != null) {
            Spacer(Modifier.height(8.dp))
            Text(note, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }

    val shown = open
    if (shown != null) {
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text("@${shown.username}") },
            text = {
                Column {
                    DetailLine("Google address", shown.email ?: "not linked yet")
                    DetailLine("Name claimed", shown.claimedAt.take(10).ifBlank { "unknown" })
                    DetailLine("Access", accessLabel(shown.level))
                    DetailLine("Features", shown.features.ifEmpty { listOf("none") }.joinToString(", "))
                    DetailLine(
                        "Ticks",
                        when {
                            shown.verified && shown.partner -> "blue and black"
                            shown.verified -> "blue"
                            shown.partner -> "black"
                            else -> "none"
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        open = null
                        onOpenAccess()
                    },
                ) { Text("Change access") }
            },
            dismissButton = { TextButton(onClick = { open = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = TextSoft, fontSize = 12.sp, modifier = Modifier.width(120.dp))
        Text(value, fontSize = 12.sp)
    }
}

/** The level as a person reads it, from the same words the settings use. */
private fun accessLabel(level: String): String = when (level.uppercase()) {
    "THEME" -> "Theme"
    "ADVANCED" -> "Theme + Advanced"
    "EVERYTHING" -> "Everything"
    else -> "No access"
}

/** One person: name, ticks, address, and what they may use. */
@Composable
private fun UserRow(user: Supabase.AdminUser, onOpen: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("@${user.username}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (user.verified || user.partner) {
                    Spacer(Modifier.width(4.dp))
                    TickBadge(if (user.verified) AccessTick.Blue else AccessTick.Black, 13.dp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = accessLabel(user.level),
                    color = if (user.level == "NONE") TextSoft else MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = user.email ?: "No Google address linked",
                color = TextSoft,
                fontSize = 11.sp,
            )
            if (user.claimedAt.length >= 10) {
                Text("joined " + user.claimedAt.take(10), color = TextSoft, fontSize = 10.sp)
            }
        }
    }
}
