package com.dudeapp.vault

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.AdminPanelSettings
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val FIREBASE_PROJECT = "p-vault-app"
private const val FCM_SEND_URL = "https://fcm.googleapis.com/v1/projects/$FIREBASE_PROJECT/messages:send"
private const val SCOPE_MESSAGING = "https://www.googleapis.com/auth/firebase.messaging"
private const val SCOPE_EMAIL = "email"

/**
 * The request Firebase expects for a push to every install. With [validateOnly] Google
 * checks that the signed-in account may send, without delivering anything.
 */
internal fun pushRequestJson(title: String, body: String, validateOnly: Boolean = false): String {
    val message = JSONObject()
        .put("topic", PUSH_TOPIC)
        // A "data" message: the app itself receives it, saves it for the bell list and
        // shows it. High priority lets it through while the phone is idle.
        .put("data", JSONObject().put("title", title.trim()).put("body", body.trim()))
        .put("android", JSONObject().put("priority", "HIGH"))
    return JSONObject().put("validate_only", validateOnly).put("message", message).toString()
}

/** Remembers on this phone that an admin signed in, so the menu can show the panel. */
class AdminStore(context: Context) {
    private val prefs = context.getSharedPreferences("dude_admin", Context.MODE_PRIVATE)

    fun email(): String? = prefs.getString("email", null)

    fun isAdmin(): Boolean = prefs.getBoolean("admin", false)

    fun save(email: String) {
        prefs.edit().putBoolean("admin", true).putString("email", email).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

/** Status code and response text, or null when there is no connection. */
private suspend fun googleRequest(url: String, token: String, body: String?): Pair<Int, String>? =
    withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Authorization", "Bearer $token")
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

/** Asks Firebase to send (or only to check). Returns an error message, or null on success. */
private suspend fun pushToEveryone(token: String, title: String, body: String, validateOnly: Boolean): String? {
    val response = googleRequest(FCM_SEND_URL, token, pushRequestJson(title, body, validateOnly))
        ?: return "No connection. Nothing was sent."
    return when (response.first) {
        200 -> null
        401 -> "Google sign-in expired. Try again."
        403 -> "This Google account is not an admin of P Vault."
        else -> "Firebase refused the request (${response.first})."
    }
}

private suspend fun googleEmail(token: String): String {
    val response = googleRequest("https://www.googleapis.com/oauth2/v3/userinfo", token, null)
    if (response == null || response.first != 200) return ""
    return runCatching { JSONObject(response.second).optString("email") }.getOrDefault("")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * For the owner of P Vault only. Signing in with Google gives the app a short-lived pass
 * for that Google account; Firebase itself decides whether the account may send, so
 * nothing secret is stored in the app and other accounts are turned away by Google.
 */
@Composable
fun AdminScreen(
    onExternal: () -> Unit,
    onAdminChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val admin = remember { AdminStore(context) }
    var email by remember { mutableStateOf(admin.email()) }
    var signedIn by remember { mutableStateOf(admin.isAdmin()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var confirmOpen by remember { mutableStateOf(false) }
    // What to do with the Google pass once it arrives.
    var afterToken by remember { mutableStateOf<((String) -> Unit)?>(null) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    fun fail(message: String) {
        busy = false
        error = message
    }

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val token = runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data).accessToken
        }.getOrNull()
        val next = afterToken
        afterToken = null
        if (token != null && next != null) next(token) else fail("Google sign-in was cancelled.")
    }

    /** Gets a Google pass for this account, showing Google's account screen when needed. */
    fun withGoogle(next: (String) -> Unit) {
        val host = activity
        if (host == null) {
            fail("Could not open Google sign-in.")
            return
        }
        busy = true
        error = null
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_MESSAGING), Scope(SCOPE_EMAIL)))
            .build()
        Identity.getAuthorizationClient(host).authorize(request)
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                val token = result.accessToken
                when {
                    result.hasResolution() && pending != null -> {
                        afterToken = next
                        onExternal()
                        runCatching {
                            consent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                        }.onFailure { fail("Could not open Google sign-in.") }
                    }
                    token != null -> next(token)
                    else -> fail("Google did not return a sign-in.")
                }
            }
            .addOnFailureListener { problem ->
                fail("Google sign-in failed: ${problem.message ?: "unknown error"}")
            }
    }

    fun signIn() = withGoogle { token ->
        scope.launch {
            // Ask Firebase whether this account may send, without sending anything.
            val problem = pushToEveryone(token, "Check", "Check", validateOnly = true)
            if (problem != null) {
                fail(problem)
            } else {
                val address = googleEmail(token).ifBlank { "Google account" }
                admin.save(address)
                email = address
                signedIn = true
                busy = false
                onAdminChanged()
                toast("Signed in as admin")
            }
        }
    }

    fun send() = withGoogle { token ->
        scope.launch {
            val problem = pushToEveryone(token, title, body, validateOnly = false)
            if (problem != null) {
                fail(problem)
            } else {
                busy = false
                title = ""
                body = ""
                toast("Notification sent to all users")
            }
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
                    .padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Admin panel", fontSize = 26.sp, fontWeight = FontWeight.Bold)
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

                if (!signedIn) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconBadge(
                            icon = Icons.Outlined.AdminPanelSettings,
                            tint = MaterialTheme.colorScheme.primary,
                            size = 64.dp,
                            iconSize = 30.dp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text("For the P Vault admin", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Sign in with the Google account that owns P Vault to send notifications to all users. Other accounts are refused.",
                            color = TextSoft,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { signIn() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (busy) "Signing in…" else "Sign in with Google")
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Send a notification", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "It goes to everyone who has P Vault 0.5.6 or newer, even when the app is closed.",
                                color = TextSoft,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = title,
                                onValueChange = {
                                    title = it.take(65)
                                    error = null
                                },
                                label = { Text("Title") },
                                singleLine = true,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = body,
                                onValueChange = {
                                    body = it.take(240)
                                    error = null
                                },
                                label = { Text("Message") },
                                minLines = 3,
                                maxLines = 6,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { confirmOpen = true },
                                enabled = !busy && title.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (busy) "Sending…" else "Send to all users")
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Signed in as ${email.orEmpty()}",
                        color = TextSoft,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    TextButton(
                        onClick = {
                            admin.clear()
                            signedIn = false
                            email = null
                            error = null
                            onAdminChanged()
                        },
                        enabled = !busy,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("Sign out")
                    }
                }

                val message = error
                if (message != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Send to all users?") },
            text = { Text("\"${title.trim()}\" will be sent now. A notification cannot be taken back once it is sent.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmOpen = false
                        send()
                    },
                ) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("Cancel") } },
        )
    }
}
