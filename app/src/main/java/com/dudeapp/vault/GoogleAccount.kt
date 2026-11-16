package com.dudeapp.vault

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What Google tells the app about the account that signed in. */
data class GoogleUser(val email: String, val name: String, val picture: String)

/** Reads Google's answer to "who is this". Null when it holds no email address. */
internal fun parseGoogleUser(json: String): GoogleUser? = runCatching {
    val data = JSONObject(json)
    val email = data.optString("email").trim()
    if (email.isEmpty()) {
        null
    } else {
        GoogleUser(email, data.optString("name").trim(), data.optString("picture").trim())
    }
}.getOrNull()

/**
 * Remembers on this phone which Google account signed in. Signing in is optional and
 * only identifies the user; no password or long-lived pass is kept.
 */
class GoogleAccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("dude_google", Context.MODE_PRIVATE)

    fun email(): String? = prefs.getString("email", null)

    fun save(user: GoogleUser) {
        prefs.edit().putString("email", user.email).putString("name", user.name).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

/** The signed-in account, or null when there is no connection or Google refuses. */
private suspend fun fetchGoogleUser(token: String): GoogleUser? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Authorization", "Bearer $token")
        try {
            if (connection.responseCode != 200) {
                null
            } else {
                parseGoogleUser(connection.inputStream.bufferedReader().use { it.readText() })
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/** Downloads the Google profile picture to a temporary file, or null when that fails. */
suspend fun downloadGooglePicture(context: Context, url: String): File? = withContext(Dispatchers.IO) {
    if (!url.startsWith("https://")) return@withContext null
    val target = File(context.cacheDir, "google_picture.tmp")
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            if (connection.responseCode != 200) return@runCatching null
            connection.inputStream.use { source -> target.outputStream().use { sink -> source.copyTo(sink) } }
        } finally {
            connection.disconnect()
        }
        target.takeIf { it.length() > 0 }
    }.getOrNull()
}

private tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}

/** The optional "Sign in with Google" card on the profile sheet. */
@Composable
fun GoogleAccountSection(
    onExternal: () -> Unit,
    onSignedIn: (GoogleUser) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.hostActivity() }
    val scope = rememberCoroutineScope()
    val account = remember { GoogleAccountStore(context) }
    var email by remember { mutableStateOf(account.email()) }
    val access = remember { AccessStore(context) }
    var special by remember { mutableStateOf(access.isSpecial()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun fail(message: String) {
        busy = false
        error = message
    }

    fun finish(token: String) {
        scope.launch {
            val user = fetchGoogleUser(token)
            if (user == null) {
                fail("Could not read the Google account. Check your connection and try again.")
            } else {
                account.save(user)
                email = user.email
                busy = false
                onSignedIn(user)
                special = access.refresh().theme
            }
        }
    }

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val token = runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data).accessToken
        }.getOrNull()
        if (token != null) finish(token) else fail("Google sign-in was cancelled.")
    }

    fun signIn() {
        val host = activity
        if (host == null) {
            fail("Could not open Google sign-in.")
            return
        }
        busy = true
        error = null
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
                    token != null -> finish(token)
                    else -> fail("Google did not return a sign-in.")
                }
            }
            .addOnFailureListener { problem ->
                fail("Google sign-in failed: ${problem.message ?: "unknown error"}")
            }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(14.dp),
    ) {
        Text(
            text = "Google account",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "Optional. P Vault works the same without it. Signing in only fills in your name and picture; your files stay on this phone.",
            color = TextSoft,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        val signedInAs = email
        if (signedInAs != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Signed in", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = signedInAs,
                        color = TextSoft,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = {
                        account.clear()
                        access.clear()
                        special = access.isSpecial()
                        email = null
                        error = null
                    },
                    enabled = !busy,
                ) {
                    Text("Sign out", fontSize = 12.sp)
                }
            }
        } else {
            Button(
                onClick = { signIn() },
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Signing in...")
                } else {
                    Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (special) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Special access is on for this account.",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        val problem = error
        if (problem != null) {
            Spacer(Modifier.height(8.dp))
            Text(problem, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}
