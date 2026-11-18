package com.dudeapp.vault

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/*
 * Usernames: one name per person, the same name everywhere they sign in.
 *
 * The list lives in Firestore beside the admin's address list:
 *   usernames/<name>  - { name: <name>, owner: <sha-256 of the address that took it> }
 *
 * Two people typing the same name produce the same document, and Firestore only accepts a
 * write whose precondition holds (the document must not exist, or must be owned by the same
 * address). So the phone that gets there second is told "taken" by the database, not by a
 * race in this code. The owner stored is a fingerprint, never the address itself.
 *
 * With no connection the name is checked on this phone only and remembered as not yet
 * confirmed; a later check reports honestly if someone else had taken it.
 */

/** Why a name was turned down, in words the user can act on. */
enum class NameProblem { EMPTY, SHORT, LONG, CHARS, OFFENSIVE, TAKEN }

/** The rule for a username: lower-case letters, numbers, dot, dash, underscore. */
internal fun normalizeUsername(text: String): String =
    text.trim().lowercase().replace(' ', '.')

internal fun usernameProblem(name: String): NameProblem? = when {
    name.isEmpty() -> NameProblem.EMPTY
    name.length < 3 -> NameProblem.SHORT
    name.length > 20 -> NameProblem.LONG
    !Regex("^[a-z0-9._-]+$").matches(name) -> NameProblem.CHARS
    isOffensive(name) -> NameProblem.OFFENSIVE
    else -> null
}

/** The sentence shown under the field, or null when the name is fine. */
fun nameProblemText(problem: NameProblem?): String? = when (problem) {
    null -> null
    NameProblem.EMPTY -> "Write a name first."
    NameProblem.SHORT -> "At least 3 characters."
    NameProblem.LONG -> "Keep it to 20 characters."
    NameProblem.CHARS -> "Only letters, numbers, dots, dashes and underscores."
    NameProblem.OFFENSIVE -> "That name is not allowed. Pick another."
    NameProblem.TAKEN -> "That name is already taken. Try another."
}

/** Where a chosen name stands. */
sealed interface NameCheck {
    /** Nobody has it; the name is kept. */
    data class Free(val name: String) : NameCheck

    /** The name is kept on this phone, but could not be reserved yet. */
    data class Unconfirmed(val name: String, val reason: String) : NameCheck

    /** Not usable, with the reason in words. */
    data class Refused(val name: String, val problem: NameProblem) : NameCheck
}

/**
 * The words a name may not contain. Matched against the name with dots, dashes and
 * underscores taken out, so "sh1t" style punctuation games do not slip through as separate
 * words. Deliberately short and blunt: it catches insults, slurs and impersonation of the
 * people who run the app, without turning into a general swear detector that fails in
 * languages nobody tested.
 */
private val BLOCKED_WORDS = listOf(
    // Insults and slurs.
    "fuck", "fuk", "fck", "shit", "sh1t", "bitch", "bastard", "asshole", "arsehole",
    "cunt", "dick", "cock", "pussy", "whore", "slut", "rape", "rapist", "pedo",
    "nigger", "nigga", "faggot", "retard", "kike", "spic", "chink", "paki",
    // Threats and harm.
    "kill", "kys", "murder", "terrorist", "isis", "nazi", "hitler",
    // Impersonation: support, staff and the app's own name.
    "admin", "administrator", "support", "official", "moderator", "pvault", "p.vault",
)

/** True when [name] is one of the words nobody may take. */
internal fun isOffensive(name: String): Boolean {
    val squeezed = name.lowercase().filter { it.isLetterOrDigit() }
    if (squeezed.isEmpty()) return false
    return BLOCKED_WORDS.any { word ->
        val flat = word.filter { it.isLetterOrDigit() }
        flat.isNotEmpty() && squeezed.contains(flat)
    }
}

/** The document id Google's database accepts: the name itself, per cent-encoded. */
internal fun usernameDocumentId(name: String): String =
    java.net.URLEncoder.encode(name, "UTF-8")

private fun ownerHash(email: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(email.trim().lowercase().toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

/** What this phone last reserved, so the sheet can say whether a name is settled. */
class UsernameStore(context: Context) {
    private val prefs = context.getSharedPreferences("dude_username", Context.MODE_PRIVATE)

    fun saved(): String? = prefs.getString("name", null)?.takeIf { it.isNotBlank() }

    fun confirmed(): Boolean = prefs.getBoolean("confirmed", false)

    fun save(name: String, confirmed: Boolean) {
        prefs.edit().putString("name", name).putBoolean("confirmed", confirmed).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

/** One name in the public list, with the fingerprint of whoever took it. */
internal data class UsernameEntry(val name: String, val owner: String)

internal fun parseUsernameEntry(json: String): UsernameEntry? = runCatching {
    val fields = JSONObject(json).optJSONObject("fields") ?: return@runCatching null
    val name = fields.optJSONObject("name")?.optString("stringValue").orEmpty()
    if (name.isEmpty()) return@runCatching null
    UsernameEntry(name, fields.optJSONObject("owner")?.optString("stringValue").orEmpty())
}.getOrNull()

/** The write that takes [name] for [email], refused by the database if someone else has it. */
internal fun usernameCommitJson(name: String, email: String): String {
    val doc = JSONObject()
        .put("name", "$DOCS_ROOT/usernames/${usernameDocumentId(name)}")
        .put(
            "fields",
            JSONObject()
                .put("name", JSONObject().put("stringValue", name))
                .put("owner", JSONObject().put("stringValue", ownerHash(email))),
        )
    // currentDocument.exists = false -> only writes when the name has never been taken.
    val precondition = JSONObject()
        .put("currentDocument", JSONObject().put("exists", false))
    return JSONObject().put("writes", JSONArray().put(JSONObject().put("update", doc).put("currentDocument", precondition)))
        .toString()
}

/**
 * Finds out whether [name] is free and, when it is, reserves it for the signed-in address.
 *
 * The token is obtained through Google's own consent screen, because the database only lets
 * a signed-in account write; a null token means sign-in was refused or there is no connection,
 * and the name is then kept on this phone with a warning rather than thrown away.
 */
internal suspend fun claimUsername(
    context: Context,
    name: String,
): NameCheck {
    // Cheaper checks first: nothing is asked of the network for a name the rules already refuse.
    usernameProblem(name)?.let { return NameCheck.Refused(name, it) }
    val email = GoogleAccountStore(context).email()
    if (email == null) {
        return NameCheck.Unconfirmed(name, "Sign in with Google to keep this name for yourself.")
    }

    // The public list can be read by anyone, so a name already taken is caught before Google
    // is even asked; only a free name needs the sign-in that lets this phone write.
    val existing = withContext(Dispatchers.IO) { readUsername(name) }
    if (existing != null) {
        return if (existing.owner == ownerHash(email)) {
            UsernameStore(context).save(name, true)
            NameCheck.Free(name)
        } else {
            NameCheck.Refused(name, NameProblem.TAKEN)
        }
    }
    val token = acquireToken(context)
        ?: return NameCheck.Unconfirmed(name, "Could not reach Google. The name is kept on this phone only.")

    val written = withContext(Dispatchers.IO) { writeUsername(name, email, token) }
    return when (written) {
        true -> {
            UsernameStore(context).save(name, true)
            NameCheck.Free(name)
        }
        // Someone wrote the name between the read and the write.
        false -> NameCheck.Refused(name, NameProblem.TAKEN)
        // The database refused for another reason (rules, or not created yet).
        null -> NameCheck.Unconfirmed(name, "The name could not be reserved right now. It is kept on this phone.")
    }
}

/** The name's document, or null when it is free or could not be read. */
private fun readUsername(name: String): UsernameEntry? = runCatching {
    val connection = URL("$FIRESTORE/usernames/${usernameDocumentId(name)}").openConnection() as HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    try {
        when (connection.responseCode) {
            200 -> parseUsernameEntry(connection.inputStream.bufferedReader().use { it.readText() })
            else -> null
        }
    } finally {
        connection.disconnect()
    }
}.getOrNull()

/** True when the write went through, false when the name was already taken, null on failure. */
private fun writeUsername(name: String, email: String, token: String): Boolean? = runCatching {
    val connection = URL("$FIRESTORE:commit").openConnection() as HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    connection.requestMethod = "POST"
    connection.doOutput = true
    connection.setRequestProperty("Authorization", "Bearer $token")
    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
    try {
        connection.outputStream.use { it.write(usernameCommitJson(name, email).toByteArray()) }
        when (connection.responseCode) {
            200 -> true
            // The precondition failed: the name exists already.
            409 -> false
            400 -> {
                // A failed precondition and a malformed request both answer 400; Firestore
                // says which in the body.
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (body.contains("FAILED_PRECONDITION") || body.contains("ALREADY_EXISTS")) false else null
            }
            else -> null
        }
    } finally {
        connection.disconnect()
    }
}.getOrNull()

/**
 * An access token for the database, through Google's own consent screen. The screen is
 * started from the running activity; Android shows it and the answer comes back to the same
 * object, so the token arrives once the user has tapped through.
 */
internal suspend fun acquireToken(context: Context): String? = withContext(Dispatchers.Main) {
    runCatching {
        val activity: android.app.Activity = context.findActivity() ?: return@runCatching null
        suspendCancellableCoroutine<String?> { continuation ->
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(SCOPE_DATASTORE), Scope("email")))
                .build()
            Identity.getAuthorizationClient(activity).authorize(request)
                .addOnSuccessListener { result ->
                    val pending = result.pendingIntent
                    if (result.hasResolution() && pending != null) {
                        // Nobody to hand the answer back to outside a launcher, so this is the
                        // one case that cannot be finished here: the name stays unconfirmed
                        // until the user signs in from the admin screen.
                        if (continuation.isActive) continuation.resume(null)
                    } else if (continuation.isActive) {
                        continuation.resume(result.accessToken)
                    }
                }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        }
    }.getOrNull()
}

