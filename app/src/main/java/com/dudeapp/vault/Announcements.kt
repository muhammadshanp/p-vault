package com.dudeapp.vault

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val NEWS_REPO = "muhammadshanp/p-vault"
private const val NEWS_OWNER = "muhammadshanp"
private const val NEWS_LABEL = "notification"
private const val NEWS_API = "https://api.github.com/repos/$NEWS_REPO/issues"

/** A message from the P Vault admin to everyone who uses the app. */
data class Announcement(val number: Int, val title: String, val body: String, val time: Long)

/**
 * Reads the list GitHub returns for the repository's issues. Only open issues written by
 * the repository owner count, so nobody else can put a message in front of users even
 * though anyone may open an issue on a public repository.
 */
internal fun parseAnnouncements(json: String, owner: String = NEWS_OWNER): List<Announcement> {
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val result = mutableListOf<Announcement>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        if (item.has("pull_request")) continue
        if (item.optString("state", "open") != "open") continue
        val author = item.optJSONObject("user")?.optString("login").orEmpty()
        if (!author.equals(owner, ignoreCase = true)) continue
        val number = item.optInt("number", 0)
        val title = item.optString("title").trim()
        if (number <= 0 || title.isEmpty()) continue
        val body = if (item.isNull("body")) "" else item.optString("body").trim()
        val time = runCatching { format.parse(item.optString("created_at"))?.time }.getOrNull() ?: 0L
        result += Announcement(number, title, body, time)
    }
    return result.sortedByDescending { it.number }
}

/**
 * Notifications are open issues labelled "notification" on the P Vault repository, written
 * by its owner. The app only reads them. The admin signs in with a GitHub token that is
 * kept on the admin's own phone; nothing secret is built into the app.
 */
class AnnouncementStore(context: Context) {
    private val prefs = context.getSharedPreferences("dude_announcements", Context.MODE_PRIVATE)
    // Outside Android's cloud backup, so the token never leaves this phone.
    private val tokenFile = File(context.noBackupFilesDir, "admin_token")

    fun cached(): List<Announcement> = parseAnnouncements(prefs.getString("cache", "[]").orEmpty())

    /** Number of the newest notification the user has already seen. */
    fun seenNumber(): Int = prefs.getInt("seen", 0)

    fun markSeen(number: Int) {
        if (number > seenNumber()) prefs.edit().putInt("seen", number).apply()
    }

    /** Newest notification already shown in the status bar. */
    fun notifiedNumber(): Int = prefs.getInt("notified", 0)

    fun markNotified(number: Int) {
        if (number > notifiedNumber()) prefs.edit().putInt("notified", number).apply()
    }

    fun isAdmin(): Boolean = token() != null

    fun signOut() {
        tokenFile.delete()
    }

    /** Latest list from GitHub, or null when offline. The result is kept for offline use. */
    suspend fun fetch(): List<Announcement>? = withContext(Dispatchers.IO) {
        val url = "$NEWS_API?state=open&labels=$NEWS_LABEL&creator=$NEWS_OWNER&per_page=30"
        // The admin's own request is not served from GitHub's shared cache, so a message
        // that was just sent shows up straight away. Everyone else reads without a token.
        var response = request("GET", url, token(), null) ?: return@withContext null
        if (response.first != 200 && token() != null) {
            response = request("GET", url, null, null) ?: return@withContext null
        }
        if (response.first != 200) return@withContext null
        prefs.edit().putString("cache", response.second).apply()
        parseAnnouncements(response.second)
    }

    /** Checks the token belongs to the repository owner and remembers it. Returns an error or null. */
    suspend fun signIn(rawToken: String): String? = withContext(Dispatchers.IO) {
        val candidate = rawToken.trim()
        if (candidate.isEmpty()) return@withContext "Paste your GitHub token"
        val response = request("GET", "https://api.github.com/user", candidate, null)
            ?: return@withContext "No connection. Try again."
        if (response.first != 200) return@withContext "GitHub did not accept that token"
        val login = runCatching { JSONObject(response.second).optString("login") }.getOrDefault("")
        if (!login.equals(NEWS_OWNER, ignoreCase = true)) {
            return@withContext "That token is not the P Vault admin account"
        }
        val saved = runCatching { tokenFile.writeText(candidate) }.isSuccess
        if (saved) null else "Could not save the token on this phone"
    }

    /** Publishes a notification to every user. Returns an error or null. */
    suspend fun send(title: String, body: String): String? = withContext(Dispatchers.IO) {
        val admin = token() ?: return@withContext "Sign in as admin first"
        val payload = JSONObject()
            .put("title", title.trim())
            .put("body", body.trim())
            .put("labels", JSONArray().put(NEWS_LABEL))
            .toString()
        val response = request("POST", NEWS_API, admin, payload)
            ?: return@withContext "No connection. Nothing was sent."
        adminError(response.first, 201)
    }

    /** Takes a notification down for everyone. Returns an error or null. */
    suspend fun remove(number: Int): String? = withContext(Dispatchers.IO) {
        val admin = token() ?: return@withContext "Sign in as admin first"
        val payload = JSONObject().put("state", "closed").toString()
        val response = request("PATCH", "$NEWS_API/$number", admin, payload)
            ?: return@withContext "No connection. Nothing was removed."
        adminError(response.first, 200)
    }

    private fun adminError(code: Int, expected: Int): String? = when (code) {
        expected -> null
        401 -> "The admin token has expired. Sign in again."
        403, 404 -> "This token may not change issues. Give it \"Issues: Read and write\" on p-vault."
        else -> "GitHub refused the request ($code)"
    }

    private fun token(): String? =
        runCatching { tokenFile.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** Returns the status code and response text, or null when the request could not be made. */
    private fun request(method: String, url: String, token: String?, body: String?): Pair<Int, String>? =
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "PVault")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            try {
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
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
