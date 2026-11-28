package com.dudeapp.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * "This Google account is an owner" — the way back into the admin panel.
 *
 * The panel is otherwise only offered when the server says the signed-in address is on the
 * owner list, which left a real dead end: an owner whose address had never been added could
 * not open the panel, and the panel is the only place that list can be edited. So the owner
 * passphrase — which only the owner has — is accepted here too. It adds this phone's address
 * to the owner list on the server and marks it on this phone, after which the panel is in
 * the three-line menu.
 *
 * A wrong passphrase changes nothing, and there is no way to read the list from here: the
 * server refuses every call that does not carry the phrase.
 */
@Composable
internal fun OwnerUnlockDialog(
    email: String,
    onDone: (Boolean, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun unlock() {
        if (key.isBlank() || email.isBlank()) return
        busy = true
        problem = null
        scope.launch {
            // Adding an address that is already there is not an error, so this is safe to
            // run any time: it either repairs the list or confirms it.
            val status = Supabase.adminAddOwner(context, key, email)
            busy = false
            when (status) {
                "ok" -> {
                    AdminStore(context).save(email)
                    onDone(true, "$email can open the admin panel now.")
                }
                null -> problem = "Could not reach the server. Check the connection and try again."
                "bad_key" -> problem = "That passphrase was not accepted."
                "bad_email" -> problem = "This phone has no Google address to add. Sign in first."
                else -> problem = "The server refused that ($status)."
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Make this account an owner") },
        text = {
            Column {
                Text(
                    "Enter the owner passphrase once. If " +
                        (email.ifBlank { "this Google account" }) +
                        " is the owner, it is added to the owner list and the " +
                        "admin panel appears in the three-line menu.",
                    fontSize = 13.sp,
                    color = TextSoft,
                )
                if (problem != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(problem.orEmpty(), fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFFE05252))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it; problem = null },
                    label = { Text("Owner passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { unlock() }, enabled = !busy && key.isNotBlank() && email.isNotBlank()) {
                Text(if (busy) "Checking…" else "Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}
