package com.dudeapp.vault

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/*
 * Suggestions: a member with special access writes one to the Firebase database, the
 * admin reads them in the admin panel. A screenshot or short video travels with it,
 * cut into parts because one database document holds at most 1 MB:
 *   suggestions/{id}            - who, when, the text, and how many parts
 *   suggestions/{id}/parts/{n}  - one slice of the attachment
 * The database rules let only members create these; only the admin's Google account
 * can read or delete them. A suggestion lives for two days.
 */

internal const val SUGGESTION_LIFE_MS = 2L * 24 * 60 * 60 * 1000
internal const val PART_BYTES = 600_000
internal const val MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024
private const val MAX_TEXT = 1000

// The certificate every P Vault release is signed with, for keys limited to this app.
private const val APP_CERT_SHA1 = "1035431F7A4F256275D668BC9E672487D64D9914"

data class Suggestion(
    val id: String,
    val email: String,
    val name: String,
    val text: String,
    val sent: Long,
    val kind: String = "",
    val mime: String = "",
    val parts: Int = 0,
    val size: Long = 0,
)

internal fun partCount(size: Long): Int = ((size + PART_BYTES - 1) / PART_BYTES).toInt()

internal fun isExpired(suggestion: Suggestion, now: Long): Boolean = now - suggestion.sent >= SUGGESTION_LIFE_MS

private fun textValue(value: String) = JSONObject().put("stringValue", value)

private fun numberValue(value: Long) = JSONObject().put("integerValue", value.toString())

private fun expiry(sent: Long) =
    JSONObject().put("timestampValue", Instant.ofEpochMilli(sent + SUGGESTION_LIFE_MS).toString())

internal fun suggestionJson(suggestion: Suggestion): String = JSONObject().put(
    "fields",
    JSONObject()
        .put("email", textValue(suggestion.email))
        .put("name", textValue(suggestion.name))
        .put("text", textValue(suggestion.text))
        .put("sent", numberValue(suggestion.sent))
        .put("expires", expiry(suggestion.sent))
        .put("kind", textValue(suggestion.kind))
        .put("mime", textValue(suggestion.mime))
        .put("parts", numberValue(suggestion.parts.toLong()))
        .put("size", numberValue(suggestion.size)),
).toString()

internal fun partJson(bytes: ByteArray, sent: Long): String = JSONObject().put(
    "fields",
    JSONObject()
        .put("data", JSONObject().put("bytesValue", Base64.getEncoder().encodeToString(bytes)))
        .put("expires", expiry(sent)),
).toString()

internal fun parsePart(json: String): ByteArray? = runCatching {
    val data = JSONObject(json).getJSONObject("fields").getJSONObject("data").getString("bytesValue")
    Base64.getDecoder().decode(data)
}.getOrNull()

/** Reads the admin's list. Entries without an id or a time are dropped. */
internal fun parseSuggestions(json: String): List<Suggestion> = runCatching {
    val documents = JSONObject(json).optJSONArray("documents") ?: return@runCatching emptyList()
    (0 until documents.length()).mapNotNull { index ->
        val document = documents.optJSONObject(index) ?: return@mapNotNull null
        val fields = document.optJSONObject("fields") ?: return@mapNotNull null
        fun string(name: String) = fields.optJSONObject(name)?.optString("stringValue").orEmpty()
        fun long(name: String) = fields.optJSONObject(name)?.optString("integerValue")?.toLongOrNull()
        val id = document.optString("name").substringAfterLast('/')
        val sent = long("sent") ?: return@mapNotNull null
        if (id.isEmpty()) return@mapNotNull null
        Suggestion(
            id = id,
            email = string("email"),
            name = string("name"),
            text = string("text"),
            sent = sent,
            kind = string("kind"),
            mime = string("mime"),
            parts = (long("parts") ?: 0L).toInt().coerceIn(0, 100),
            size = long("size") ?: 0L,
        )
    }
}.getOrDefault(emptyList())

/** One request that removes a suggestion and every part of its attachment. */
internal fun deleteSuggestionJson(suggestion: Suggestion): String {
    val writes = JSONArray()
    val root = "$DOCS_ROOT/suggestions/${suggestion.id}"
    for (part in 0 until suggestion.parts) writes.put(JSONObject().put("delete", "$root/parts/$part"))
    writes.put(JSONObject().put("delete", root))
    return JSONObject().put("writes", writes).toString()
}

/** Status code and response text, or null when there is no connection. */
private suspend fun call(
    url: String,
    bearer: String?,
    body: String?,
    headers: Map<String, String> = emptyMap(),
): Pair<Int, String>? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        try {
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private fun errorText(response: String): String =
    runCatching { JSONObject(response).getJSONObject("error").getString("message") }.getOrDefault("")

private class FirebaseUser(val token: String, val email: String, val name: String)

/**
 * Swaps the Google pass for a Firebase one, which is what the database rules look at.
 * Returns the user, or a message saying why Firebase refused.
 */
private suspend fun firebaseSignIn(context: Context, googleToken: String): Pair<FirebaseUser?, String> {
    val keyId = context.resources.getIdentifier("google_api_key", "string", context.packageName)
    if (keyId == 0) return null to "This build has no Firebase key."
    val body = JSONObject()
        .put("postBody", "access_token=${URLEncoder.encode(googleToken, "UTF-8")}&providerId=google.com")
        .put("requestUri", "http://localhost")
        .put("returnSecureToken", true)
        .toString()
    val response = call(
        "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=${context.getString(keyId)}",
        null,
        body,
        mapOf("X-Android-Package" to context.packageName, "X-Android-Cert" to APP_CERT_SHA1),
    ) ?: return null to "No connection."
    if (response.first != 200) {
        return null to "Firebase sign-in failed (${response.first} ${errorText(response.second)})."
    }
    return runCatching {
        val data = JSONObject(response.second)
        FirebaseUser(data.getString("idToken"), data.getString("email"), data.optString("displayName"))
    }.getOrNull()?.let { it to "" } ?: (null to "Firebase sign-in gave no account.")
}

private class Attachment(val file: File, val kind: String, val mime: String)

private fun suggestionDir(context: Context): File = File(context.cacheDir, "suggestions").apply { mkdirs() }

/** Copies a picked video, or a shrunk copy of a picked picture, into the cache. */
private suspend fun prepareAttachment(context: Context, uri: Uri): Pair<Attachment?, String> =
    withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val target = File(suggestionDir(context), "outgoing.tmp")
        target.delete()
        runCatching<Pair<Attachment?, String>> {
            if (mime.startsWith("video/")) {
                var total = 0L
                val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null to "Could not read the video."
                input.use { source ->
                    target.outputStream().use { sink ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_ATTACHMENT_BYTES) {
                                return@runCatching null to "That video is larger than 10 MB. Pick a shorter one."
                            }
                            sink.write(buffer, 0, read)
                        }
                    }
                }
                if (total == 0L) null to "Could not read the video." else Attachment(target, "video", mime) to ""
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return@runCatching null to "Could not read the picture."
                target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                bitmap.recycle()
                Attachment(target, "image", "image/jpeg") to ""
            }
        }.getOrElse {
            target.delete()
            null to "Could not read the attachment."
        }
    }

private tailrec fun Context.ownerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.ownerActivity()
    else -> null
}

private fun refusal(code: Int, response: String): String = when (code) {
    403 -> "Firebase refused it. Only accounts with special access can send suggestions."
    404 -> "The suggestions database is not set up yet."
    else -> "Firebase refused the request ($code ${errorText(response)})."
}

/** The "Give suggestion" window for members with special access. */
@Composable
fun SuggestionDialog(
    onExternal: () -> Unit,
    onSent: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<Attachment?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun fail(message: String) {
        busy = false
        progress = ""
        error = message
    }

    suspend fun send(googleToken: String) {
        val (user, problem) = firebaseSignIn(context, googleToken)
        if (user == null) {
            fail(problem)
            return
        }
        val sent = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val attached = attachment
        val size = attached?.file?.length() ?: 0L
        val parts = partCount(size)
        if (attached != null) {
            val failure = withContext(Dispatchers.IO) {
                runCatching<String?> {
                    attached.file.inputStream().use { input ->
                        for (part in 0 until parts) {
                            withContext(Dispatchers.Main) { progress = "Sending part ${part + 1} of $parts…" }
                            val bytes = input.readNBytesCompat(PART_BYTES)
                            val response = call(
                                "$FIRESTORE/suggestions/$id/parts?documentId=$part",
                                user.token,
                                partJson(bytes, sent),
                            ) ?: return@runCatching "No connection. The suggestion was not sent."
                            if (response.first != 200) return@runCatching refusal(response.first, response.second)
                        }
                    }
                    null
                }.getOrElse { "Could not read the attachment." }
            }
            if (failure != null) {
                fail(failure)
                return
            }
        }
        progress = "Sending…"
        val suggestion = Suggestion(
            id = id,
            email = user.email,
            name = user.name,
            text = draft.trim(),
            sent = sent,
            kind = attached?.kind.orEmpty(),
            mime = attached?.mime.orEmpty(),
            parts = parts,
            size = size,
        )
        val response = call("$FIRESTORE/suggestions?documentId=$id", user.token, suggestionJson(suggestion))
        when {
            response == null -> fail("No connection. The suggestion was not sent.")
            response.first != 200 -> fail(refusal(response.first, response.second))
            else -> {
                attached?.file?.delete()
                busy = false
                onSent()
            }
        }
    }

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val token = runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data).accessToken
        }.getOrNull()
        if (token != null) scope.launch { send(token) } else fail("Google sign-in was cancelled.")
    }

    fun start() {
        val host = context.ownerActivity()
        if (host == null) {
            fail("Could not open Google sign-in.")
            return
        }
        busy = true
        error = null
        progress = "Signing in…"
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("email"), Scope("profile")))
            .build()
        Identity.getAuthorizationClient(host).authorize(request)
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                val token = result.accessToken
                when {
                    result.hasResolution() && pending != null -> {
                        onExternal()
                        runCatching {
                            consent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                        }.onFailure { fail("Could not open Google sign-in.") }
                    }
                    token != null -> scope.launch { send(token) }
                    else -> fail("Google did not return a sign-in.")
                }
            }
            .addOnFailureListener { problem ->
                fail("Google sign-in failed: ${problem.message ?: "unknown error"}")
            }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val (picked, problem) = prepareAttachment(context, uri)
                attachment = picked
                error = problem.ifEmpty { null }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Give a suggestion") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "It goes to the P Vault admin together with your Google address, and is deleted after 2 days.",
                    color = TextSoft,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(MAX_TEXT) },
                    label = { Text("Your suggestion") },
                    minLines = 4,
                    maxLines = 8,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                val attached = attachment
                if (attached == null) {
                    TextButton(
                        onClick = {
                            onExternal()
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                            )
                        },
                        enabled = !busy,
                    ) {
                        Text("Add a screenshot or short video")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${if (attached.kind == "video") "Video" else "Picture"} · ${sizeLabel(attached.file.length())}",
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                attached.file.delete()
                                attachment = null
                            },
                            enabled = !busy,
                        ) {
                            Text("Remove")
                        }
                    }
                }
                if (busy && progress.isNotEmpty()) {
                    Text(progress, color = TextSoft, fontSize = 12.sp)
                }
                val problem = error
                if (problem != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(problem, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { start() }, enabled = !busy && draft.isNotBlank()) {
                Text(if (busy) "Sending…" else "Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}

/** Reads up to [count] bytes; shorter only at the end of the file. */
private fun java.io.InputStream.readNBytesCompat(count: Int): ByteArray {
    val buffer = ByteArray(count)
    var filled = 0
    while (filled < count) {
        val read = read(buffer, filled, count - filled)
        if (read < 0) break
        filled += read
    }
    return if (filled == count) buffer else buffer.copyOf(filled)
}

/** The admin's list of suggestions, shown in the admin panel. */
@Composable
fun SuggestionsCard(
    busy: Boolean,
    withGoogle: ((String) -> Unit) -> Unit,
    onExternal: () -> Unit,
    onDone: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var suggestions by remember { mutableStateOf<List<Suggestion>?>(null) }
    var picture by remember { mutableStateOf<File?>(null) }
    val timeFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }

    fun load() = withGoogle { token ->
        scope.launch {
            val response = call("$FIRESTORE/suggestions?pageSize=300", token, null)
            when {
                response == null -> onDone("No connection.")
                response.first == 200 -> {
                    val now = System.currentTimeMillis()
                    val (old, fresh) = parseSuggestions(response.second).partition { isExpired(it, now) }
                    suggestions = fresh.sortedByDescending { it.sent }
                    // Anything older than two days is erased for good.
                    old.forEach { call("$FIRESTORE:commit", token, deleteSuggestionJson(it)) }
                    onDone(null)
                }
                response.first == 404 -> onDone("The Firestore database is not created yet in the Firebase console.")
                else -> onDone("Firebase refused the request (${response.first} ${errorText(response.second)}).")
            }
        }
    }

    fun delete(suggestion: Suggestion) = withGoogle { token ->
        scope.launch {
            val response = call("$FIRESTORE:commit", token, deleteSuggestionJson(suggestion))
            if (response != null && response.first == 200) {
                suggestions = suggestions?.filter { it.id != suggestion.id }
                onDone(null)
            } else {
                onDone("Could not delete the suggestion.")
            }
        }
    }

    fun open(suggestion: Suggestion) = withGoogle { token ->
        scope.launch {
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(suggestion.mime)
                ?: if (suggestion.kind == "video") "mp4" else "jpg"
            val target = File(suggestionDir(context), "${suggestion.id}.$extension")
            val complete = withContext(Dispatchers.IO) {
                runCatching {
                    target.outputStream().use { sink ->
                        for (part in 0 until suggestion.parts) {
                            val response = call("$FIRESTORE/suggestions/${suggestion.id}/parts/$part", token, null)
                            val bytes = response?.takeIf { it.first == 200 }?.let { parsePart(it.second) }
                                ?: return@runCatching false
                            sink.write(bytes)
                        }
                    }
                    true
                }.getOrDefault(false)
            }
            if (!complete) {
                target.delete()
                onDone("Could not download the attachment.")
            } else if (suggestion.kind == "video") {
                onDone(null)
                onExternal()
                val opened = runCatching {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", target)
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, suggestion.mime.ifEmpty { "video/*" })
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.isSuccess
                if (!opened) onDone("No app on this phone can play the video.")
            } else {
                picture = target
                onDone(null)
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
            Text("Suggestions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Sent by people with special access. Each one is deleted 2 days after it was sent.",
                color = TextSoft,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            val list = suggestions
            Button(onClick = { load() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        busy -> "Working…"
                        list == null -> "Show suggestions"
                        else -> "Refresh"
                    },
                )
            }
            if (list != null && list.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("No suggestions right now.", color = TextSoft, fontSize = 13.sp)
            }
            list.orEmpty().forEach { suggestion ->
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(Modifier.height(10.dp))
                Text(suggestion.email, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(timeFormat.format(Date(suggestion.sent)), color = TextSoft, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(suggestion.text, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (suggestion.parts > 0) {
                        TextButton(onClick = { open(suggestion) }, enabled = !busy) {
                            val label = if (suggestion.kind == "video") "Play video" else "Open picture"
                            Text("$label (${sizeLabel(suggestion.size)})", fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { delete(suggestion) }, enabled = !busy) {
                        Text("Delete", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    val shown = picture
    if (shown != null) {
        Dialog(onDismissRequest = { picture = null }) {
            AsyncImage(
                model = shown,
                contentDescription = "Suggestion picture",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
