package com.dudeapp.vault

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/*
 * Special access: the admin keeps a list of email addresses in the Firebase database
 * (Firestore). Two documents are written together:
 *   access/emails  - the addresses themselves; only the admin's Google account can read it
 *   access/special - a SHA-256 fingerprint of each address; anyone can read it
 * An install fingerprints its own signed-in address and looks for it in the public list,
 * so the addresses are never published.
 */

internal const val SCOPE_DATASTORE = "https://www.googleapis.com/auth/datastore"
private const val DOCS_ROOT = "projects/p-vault-app/databases/(default)/documents"
private const val FIRESTORE = "https://firestore.googleapis.com/v1/$DOCS_ROOT"
private const val MAX_SPECIAL = 500

/** A tidy lower-case address, or null when the text is not an email address. */
internal fun cleanEmail(text: String): String? {
    val email = text.trim().lowercase()
    return email.takeIf { Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+").matches(it) }
}

internal fun emailHash(email: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun arrayField(values: List<String>): JSONObject {
    val items = JSONArray()
    values.forEach { items.put(JSONObject().put("stringValue", it)) }
    return JSONObject().put("arrayValue", JSONObject().put("values", items))
}

/** The request that saves the address list and its public fingerprints in one step. */
internal fun specialCommitJson(emails: List<String>): String {
    fun write(doc: String, field: String, values: List<String>) = JSONObject().put(
        "update",
        JSONObject()
            .put("name", "$DOCS_ROOT/access/$doc")
            .put("fields", JSONObject().put(field, arrayField(values))),
    )
    return JSONObject().put(
        "writes",
        JSONArray()
            .put(write("emails", "emails", emails))
            .put(write("special", "hashes", emails.map(::emailHash))),
    ).toString()
}

/** The text values of one list field of a Firestore document. */
internal fun parseStringList(json: String, field: String): List<String> = runCatching {
    val values = JSONObject(json).optJSONObject("fields")
        ?.optJSONObject(field)
        ?.optJSONObject("arrayValue")
        ?.optJSONArray("values")
        ?: return@runCatching emptyList()
    (0 until values.length()).mapNotNull { index ->
        values.optJSONObject(index)?.optString("stringValue")?.takeIf { it.isNotEmpty() }
    }
}.getOrDefault(emptyList())

/** Remembers whether this install has special access, so it also works offline. */
class AccessStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("dude_access", Context.MODE_PRIVATE)

    /** True for the admin and for a signed-in address on the admin's list. */
    fun isSpecial(): Boolean = AdminStore(context).isAdmin() || prefs.getBoolean("special", false)

    fun clear() {
        prefs.edit().clear().apply()
    }

    /** Looks the signed-in address up in the public list. Keeps the last answer when offline. */
    suspend fun refresh(): Boolean {
        val email = GoogleAccountStore(context).email()
        if (email == null) {
            clear()
            return isSpecial()
        }
        val hashes = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL("$FIRESTORE/access/special").openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                try {
                    when (connection.responseCode) {
                        200 -> parseStringList(connection.inputStream.bufferedReader().use { it.readText() }, "hashes")
                        404 -> emptyList()
                        else -> null
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
        if (hashes != null) prefs.edit().putBoolean("special", emailHash(email) in hashes).apply()
        return isSpecial()
    }
}

private fun databaseProblem(code: Int): String = when (code) {
    401 -> "Google sign-in expired. Try again."
    403 -> "This Google account may not change the list, or the Firestore database is not turned on."
    404 -> "The Firestore database is not created yet in the Firebase console."
    else -> "Firebase refused the request ($code)."
}

/** The admin's list of addresses with special access, shown in the admin panel. */
@Composable
fun SpecialAccessCard(
    busy: Boolean,
    withGoogle: ((String) -> Unit) -> Unit,
    onDone: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var emails by remember { mutableStateOf<List<String>?>(null) }
    var draft by remember { mutableStateOf("") }

    fun load() = withGoogle { token ->
        scope.launch {
            val response = googleRequest("$FIRESTORE/access/emails", token, null)
            when {
                response == null -> onDone("No connection.")
                response.first == 200 -> {
                    emails = parseStringList(response.second, "emails")
                    onDone(null)
                }
                // No list has been saved yet.
                response.first == 404 -> {
                    emails = emptyList()
                    onDone(null)
                }
                else -> onDone(databaseProblem(response.first))
            }
        }
    }

    fun save(next: List<String>) = withGoogle { token ->
        scope.launch {
            val response = googleRequest("$FIRESTORE:commit", token, specialCommitJson(next))
            when {
                response == null -> onDone("No connection. The list was not changed.")
                response.first == 200 -> {
                    emails = next
                    draft = ""
                    onDone(null)
                }
                else -> onDone(databaseProblem(response.first))
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Special access", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "People who sign in to P Vault with one of these Google addresses get special access. It reaches their phone the next time they open the app online.",
                color = TextSoft,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            val list = emails
            if (list == null) {
                Button(onClick = { load() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Working…" else "Show the list")
                }
            } else {
                if (list.isEmpty()) {
                    Text("No addresses yet.", color = TextSoft, fontSize = 13.sp)
                }
                list.forEach { email ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            email,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { save(list - email) }, enabled = !busy) {
                            Text("Remove", fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(120) },
                    label = { Text("Email address") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                val clean = cleanEmail(draft)
                Button(
                    onClick = { if (clean != null) save(list + clean) },
                    enabled = !busy && clean != null && clean !in list && list.size < MAX_SPECIAL,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Working…" else "Give special access")
                }
            }
        }
    }
}
