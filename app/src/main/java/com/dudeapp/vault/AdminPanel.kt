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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
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
/**
 * Which phone may open the admin panel.
 *
 * It is not a flag this app can set: an address is an admin only when it appears in the
 * owner list in Supabase (filled from Admin panel -> Access), and the panel then follows
 * the account that is signed in — sign out, and the panel is gone. The older Firestore
 * route is kept as the fallback for the first sign-in, before any owner list exists.
 */
class AdminStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("dude_admin", Context.MODE_PRIVATE)

    fun email(): String? = prefs.getString("email", null)

    /**
     * True when the signed-in address may open the panel. A cached yes is not enough on its
     * own: the moment the account signs out, this says no.
     */
    fun isAdmin(): Boolean {
        val signedIn = GoogleAccountStore(context).email() ?: return false
        val knownAs = prefs.getString("email", null)
        if (knownAs != null && !knownAs.equals(signedIn, ignoreCase = true)) return false
        return prefs.getBoolean("admin", false)
    }

    /** Remembers that this address was confirmed as an owner. */
    fun save(email: String) {
        prefs.edit().putBoolean("admin", true).putString("email", email.trim().lowercase()).apply()
    }

    /** Called on sign-out and on a different account: the panel is not inherited. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * What the app calls on start and after a sign-in: if the signed-in address is on the
     * owner list, the panel is on from that moment; if it is not, the panel is not offered.
     * Offline keeps the last answer, so an owner is not locked out in a tunnel.
     */
    suspend fun refreshFromServer(): Boolean {
        val signedIn = GoogleAccountStore(context).email() ?: run {
            clear()
            return false
        }
        // The address is the account, so the question is asked again on every launch and
        // after every sign-in. It used to be asked once, so a phone that first started
        // signed out — or before the address was added to the owner list — never saw the
        // panel until it signed in again. Now being on the list is enough, immediately.
        val answer = runCatching { Supabase.account(context, signedIn) }.getOrNull()
        val answered = answer?.admin
            ?: runCatching { Supabase.isOwner(context, signedIn) }.getOrNull()
            ?: return isAdmin()
        if (answered) save(signedIn) else clear()
        return answered
    }

    /** Asks the owner list whether this address is on it. Keeps the last answer offline. */
    suspend fun confirm(passphrase: String): Boolean {
        val signedIn = GoogleAccountStore(context).email() ?: return false
        val (owners, answered) = Supabase.adminOwners(context, passphrase)
        if (!answered) return isAdmin()
        val listed = owners.any { it.email.equals(signedIn, ignoreCase = true) }
        if (listed) save(signedIn) else clear()
        return listed
    }

    /**
     * The owner list, readable only with the passphrase. When no list has been created yet
     * this returns null so the caller can say so rather than claiming a wrong passphrase.
     */
    internal suspend fun owners(passphrase: String): List<Supabase.Owner>? =
        Supabase.adminOwners(context, passphrase).let { (owners, ok) -> if (ok) owners else null }

    suspend fun addOwner(passphrase: String, email: String): String? =
        Supabase.adminAddOwner(context, passphrase, email)

    suspend fun removeOwner(passphrase: String, email: String) =
        Supabase.adminRemoveOwner(context, passphrase, email)
}

/** Status code and response text, or null when there is no connection. */
internal suspend fun googleRequest(url: String, token: String, body: String?): Pair<Int, String>? =
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

internal tailrec fun Context.findActivity(): Activity? = when (this) {
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
    /** The catalogue looks the admin can share per address, by id and name. */
    shareableLooks: List<Pair<String, String>> = emptyList(),
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
    var page by remember { mutableStateOf(AdminPage.MESSAGE) }
    val adminPages = listOf(AdminPage.MESSAGE, AdminPage.ACCESS, AdminPage.PEOPLE, AdminPage.ACCOUNT)
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
    fun withGoogle(scopes: List<String> = listOf(SCOPE_MESSAGING, SCOPE_EMAIL), next: (String) -> Unit) {
        val host = activity
        if (host == null) {
            fail("Could not open Google sign-in.")
            return
        }
        busy = true
        error = null
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(scopes.map { Scope(it) })
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
                    .padding(horizontal = Spacing.Screen)
                    .padding(bottom = 32.dp),
            ) {
                Spacer(Modifier.height(10.dp))

                // The pages are always offered: Access is opened with the owner passphrase, not
                // with a Google account, so it has to be reachable before signing in.
                AdminPages(pages = adminPages, selected = page, onSelect = { page = it })
                Spacer(Modifier.height(12.dp))

                if (!signedIn && page != AdminPage.ACCESS) {
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
                    when (page) {
                        AdminPage.ACCESS -> AdminAccessCard()
                        AdminPage.PEOPLE -> {
                            SpecialAccessCard(
                                busy = busy,
                                shareableLooks = shareableLooks,
                                withGoogle = { next -> withGoogle(listOf(SCOPE_DATASTORE), next) },
                                onDone = { problem -> if (problem != null) fail(problem) else busy = false },
                            )
                        }
                        AdminPage.NAMES -> ReservedNamesCard(
                            busy = busy,
                            withGoogle = { next -> withGoogle(listOf(SCOPE_DATASTORE), next) },
                            onDone = { problem -> if (problem != null) fail(problem) else busy = false },
                        )
                        AdminPage.MESSAGE -> AdminMessageCard(
                            busy = busy,
                            title = title,
                            body = body,
                            onTitle = { title = it.take(60); error = null },
                            onBody = { body = it.take(240); error = null },
                            onSend = { confirmOpen = true },
                        )
                        AdminPage.ACCOUNT -> AdminAccountCard(
                            email = email,
                            busy = busy,
                            onSignOut = {
                                admin.clear()
                                signedIn = false
                                email = null
                                error = null
                                onAdminChanged()
                            },
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Signed in as ${email.orEmpty()}",
                        color = TextSoft,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
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

/** The parts of the admin panel, each on its own page. */
enum class AdminPage(val label: String) {
    MESSAGE("Message"),
    ACCESS("Access"),
    PEOPLE("People"),
    NAMES("Names"),
    ACCOUNT("Account"),
}

/** The page tabs: two columns, so a thumb reaches every one. */
@Composable
private fun AdminPages(
    pages: List<AdminPage>,
    selected: AdminPage,
    onSelect: (AdminPage) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        pages.forEach { page ->
            val active = page == selected
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(page) },
            ) {
                Text(
                    page.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 9.dp),
                )
            }
        }
    }
}

/** Sending one notification to everybody who has the app. */
@Composable
private fun AdminMessageCard(
    busy: Boolean,
    title: String,
    body: String,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onSend: () -> Unit,
) {
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
                onValueChange = onTitle,
                label = { Text("Title") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = body,
                onValueChange = onBody,
                label = { Text("Message") },
                minLines = 3,
                maxLines = 6,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onSend,
                enabled = !busy && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "Sending…" else "Send to all users")
            }
        }
    }
}

/** Whose sign-in this is, and how to leave it. */
@Composable
private fun AdminAccountCard(
    email: String?,
    busy: Boolean,
    onSignOut: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("This admin sign-in", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                email.orEmpty().ifBlank { "No address" },
                color = TextSoft,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "The panel is open while this account is signed in. Signing out leaves the phone " +
                    "without the panel until an admin signs in again; nothing else changes.",
                color = TextSoft,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out")
            }
        }
    }
}
