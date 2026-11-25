package com.dudeapp.vault

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The Supabase half of P Vault: the only data that leaves the phone is a display name.
 *
 * Vault contents — photos, PDFs, notes, reels, chat messages — never go anywhere near this
 * file. The table it talks to holds two columns, a name and the install that claimed it, and
 * is closed to direct reads and writes: everything goes through three SECURITY DEFINER
 * functions, so the publishable key cannot list other people's names.
 *
 *   pre_re_check(name)            -> {"available": bool, "reason": "ok"|"taken"|...}
 *   pre_re_claim(name, device)    -> {"status": "ok"|"taken"|"already_yours"|...}
 *   pre_re_release(name, device)  -> {"released": bool}   (kept server-side; the app never
 *                                                          gives a name back)
 *
 * The key below is a publishable key: it is meant to ship inside the app and is useless
 * without the row-level security it is subject to.
 */
internal object Supabase {
    const val PROJECT_URL = "https://bjhluvplbozefgmjzefw.supabase.co"

    /** Publishable (anon) key — public by design, protected by RLS. */
    private const val PUBLISHABLE_KEY = "sb_publishable_uW3Qc4Ykym7E1X6_MkV8_g_XdbxRxOy"

    /** Legacy anon key, sent as the bearer so the request lands in the `anon` role. */
    private const val ANON_JWT =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJqaGx1dnBs" +
            "Ym96ZWZnbWp6ZWZ3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTUxODc3NDIsImV4cCI6MjExMDc2Mzc0Mn0" +
            ".y9-YPrQj5agmnUvLaULJ1JilbMPqIl55FGkY_W-oyz0"

    private const val PREF = "pre_re_supabase"
    private const val PREF_DEVICE = "device_id"

    /** A random id for this install, so a name can belong to it without an account. */
    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val existing = prefs.getString(PREF_DEVICE, null)
        if (existing != null && existing.length >= 8) return existing
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(PREF_DEVICE, fresh).apply()
        return fresh
    }

    /** Blocking POST to a PostgREST function. Returns null when offline or on any error. */
    private fun rpc(function: String, body: JSONObject): JSONObject? {
        val connection = runCatching {
            URL("$PROJECT_URL/rest/v1/rpc/$function").openConnection() as HttpURLConnection
        }.getOrNull() ?: return null

        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $ANON_JWT")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            if (connection.responseCode !in 200..299) return null
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            // PostgREST answers a scalar-returning function with a bare JSON value.
            if (text.trim().startsWith("{")) JSONObject(text) else JSONObject("{\"value\":$text}")
        } catch (e: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    // ---- what the screens call ------------------------------------------------------ //

    sealed interface NameCheck {
        data object Free : NameCheck
        data object Taken : NameCheck
        data class Rejected(val reason: String) : NameCheck
        data object Offline : NameCheck
    }

    suspend fun checkName(context: Context, name: String): NameCheck = withContext(Dispatchers.IO) {
        val json = rpc("pre_re_check", JSONObject().put("p_username", normalizeUsername(name)))
            ?: return@withContext NameCheck.Offline
        parseCheck(json)
    }

    /** Split out from the network call so the mapping itself can be tested. */
    internal fun parseCheck(json: JSONObject): NameCheck = when {
        json.optBoolean("available") -> NameCheck.Free
        json.optString("reason") == "taken" -> NameCheck.Taken
        else -> NameCheck.Rejected(json.optString("reason"))
    }

    suspend fun claimName(context: Context, name: String): String? = withContext(Dispatchers.IO) {
        val json = rpc(
            "pre_re_claim",
            JSONObject()
                .put("p_username", normalizeUsername(name))
                .put("p_device", deviceId(context)),
        ) ?: return@withContext null
        json.optString("status").takeIf { it.isNotEmpty() }
    }

    // ---- the owner's side ------------------------------------------------------------ //
    //
    // The passphrase is never stored in the app and never sent: what travels is its SHA-256,
    // which is what the database compares against. So the list of names is only readable by
    // someone who knows the phrase, not by anyone who unpacks the APK.

    /** SHA-256 of a typed passphrase, lower-cased and trimmed, as 64 hex characters. */
    fun keyHash(passphrase: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(passphrase.trim().lowercase().toByteArray(Charsets.UTF_8))
            // Byte is signed, so mask before formatting: without the mask a byte above 0x7f
            // would print as ffffff80 and the hash would never match the server's.
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    suspend fun isAdminKey(context: Context, passphrase: String): Boolean =
        withContext(Dispatchers.IO) {
            val json = rpc("pre_re_is_admin", JSONObject().put("p_key", keyHash(passphrase)))
                ?: return@withContext false
            json.optBoolean("value") || json.optBoolean("pre_re_is_admin")
        }

    /** One name as the owner sees it: when it was claimed, and what it may use. */
    data class RemoteName(
        val username: String,
        val claimedAt: String,
        val level: String,
        val features: List<String>,
        val verified: Boolean = false,
        val partner: Boolean = false,
    )

    /** Every claimed name. Null means the passphrase was wrong or the server was unreachable. */
    suspend fun adminNames(context: Context, passphrase: String): List<RemoteName>? =
        withContext(Dispatchers.IO) {
            val json = rpc("pre_re_admin_names", JSONObject().put("p_key", keyHash(passphrase)))
                ?: return@withContext null
            val rows = json.optJSONArray("value") ?: return@withContext emptyList()
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                RemoteName(
                    username = row.optString("username"),
                    claimedAt = row.optString("created_at"),
                    level = row.optString("level").ifBlank { "NONE" },
                    features = row.optJSONArray("features")?.let { list ->
                        (0 until list.length()).mapNotNull { list.optString(it).takeIf(String::isNotEmpty) }
                    }.orEmpty(),
                    verified = row.optBoolean("verified"),
                    partner = row.optBoolean("partner"),
                )
            }
        }

    /** Gives one name a level and its switches. False means the key was wrong. */
    suspend fun setAccess(
        context: Context,
        passphrase: String,
        username: String,
        level: String,
        features: List<String>,
    ): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_key", keyHash(passphrase))
            .put("p_username", username)
            .put("p_level", level)
            .put(
                "p_features",
                org.json.JSONArray().also { array -> features.forEach { array.put(it) } },
            )
        val json = rpc("pre_re_admin_set_access", body) ?: return@withContext false
        json.optBoolean("value") || json.optBoolean("pre_re_admin_set_access")
    }

    /** What this install's own name may use. Granted is false for any other device. */
    data class RemoteAccess(
        val level: String,
        val features: List<String>,
        val granted: Boolean,
        val verified: Boolean = false,
        val partner: Boolean = false,
    )

    suspend fun myAccess(context: Context, username: String): RemoteAccess? =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("p_username", normalizeUsername(username))
                .put("p_device", deviceId(context))
            val json = rpc("pre_re_my_access", body) ?: return@withContext null
            val row = json.optJSONArray("value")?.optJSONObject(0) ?: json
            RemoteAccess(
                level = row.optString("level").ifBlank { "NONE" },
                features = row.optJSONArray("features")?.let { list ->
                    (0 until list.length()).mapNotNull { list.optString(it).takeIf(String::isNotEmpty) }
                }.orEmpty(),
                granted = row.optBoolean("granted"),
                verified = row.optBoolean("verified"),
                partner = row.optBoolean("partner"),
            )
        }

    /** What the name this phone holds may do, with both ticks. One call, used at startup. */
    suspend fun myGrant(context: Context): RemoteAccess? = withContext(Dispatchers.IO) {
        val json = rpc("pre_re_my_grant", JSONObject().put("p_device", deviceId(context)))
            ?: return@withContext null
        val row = json.optJSONArray("value")?.optJSONObject(0) ?: json
        RemoteAccess(
            level = row.optString("level").ifBlank { "NONE" },
            features = row.optJSONArray("features")?.let { list ->
                (0 until list.length()).mapNotNull { list.optString(it).takeIf(String::isNotEmpty) }
            }.orEmpty(),
            granted = row.optBoolean("granted"),
            verified = row.optBoolean("verified"),
            partner = row.optBoolean("partner"),
        )
    }

    /** Blue for the admin role, black for a partner, none to clear both. */
    suspend fun adminSetTick(
        context: Context,
        passphrase: String,
        name: String,
        tick: String,
    ): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_key", keyHash(passphrase))
            .put("p_username", normalizeUsername(name))
            .put("p_tick", tick)
        rpc("pre_re_admin_set_tick", body)?.optString("status")?.takeIf { it.isNotEmpty() }
    }

    /**
     * Whether this address may open the admin panel. A plain yes/no, so the app needs no
     * passphrase to know, and the owner list is never handed out to a signed-in account.
     */
    suspend fun isOwner(context: Context, email: String): Boolean = withContext(Dispatchers.IO) {
        if (email.isBlank()) return@withContext false
        val json = rpc("pre_re_is_owner", JSONObject().put("p_email", email.trim().lowercase()))
            ?: return@withContext false
        json.optBoolean("value") || json.optBoolean("pre_re_is_owner")
    }

    data class Owner(val email: String, val note: String)

    /** The Google addresses that may open the admin panel. */
    suspend fun adminOwners(context: Context, passphrase: String): Pair<List<Owner>, Boolean> =
        withContext(Dispatchers.IO) {
            val json = rpc("pre_re_admin_owners", JSONObject().put("p_key", keyHash(passphrase)))
                ?: return@withContext emptyList<Owner>() to false
            if (json.optString("status") == "bad_key") return@withContext emptyList<Owner>() to false
            val rows = json.optJSONArray("owners") ?: org.json.JSONArray()
            val owners = (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                Owner(row.optString("email"), row.optString("note"))
            }
            owners to true
        }

    suspend fun adminAddOwner(context: Context, passphrase: String, email: String): String? =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("p_key", keyHash(passphrase))
                .put("p_email", email.trim().lowercase())
            rpc("pre_re_admin_owner_add", body)?.optString("status")?.takeIf { it.isNotEmpty() }
        }

    suspend fun adminRemoveOwner(context: Context, passphrase: String, email: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("p_key", keyHash(passphrase))
                .put("p_email", email.trim().lowercase())
            rpc("pre_re_admin_owner_remove", body)?.optString("status") == "ok"
        }

    /** The name this phone holds, as the server knows it. Null when it holds none. */
    suspend fun myName(context: Context): String? = withContext(Dispatchers.IO) {
        val json = rpc("pre_re_my_name", JSONObject().put("p_device", deviceId(context)))
            ?: return@withContext null
        if (json.isNull("value")) null else json.optString("value").takeIf { it.isNotBlank() }
    }

    data class ReservedName(val name: String, val note: String)

    /** The names the owner has set aside. Null when the key was refused or the phone is offline. */
    suspend fun adminReserved(context: Context, passphrase: String): List<ReservedName>? =
        withContext(Dispatchers.IO) {
            val json = rpc("pre_re_admin_reserved", JSONObject().put("p_key", keyHash(passphrase)))
                ?: return@withContext null
            val rows = json.optJSONArray("value") ?: return@withContext emptyList()
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                ReservedName(row.optString("name"), row.optString("note"))
            }
        }

    /** Sets a name aside so nobody may claim it. Returns the server's status code. */
    suspend fun adminReserve(
        context: Context,
        passphrase: String,
        name: String,
        note: String,
    ): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_key", keyHash(passphrase))
            .put("p_name", normalizeUsername(name))
            .put("p_note", note)
        rpc("pre_re_admin_reserve", body)?.optString("status")?.takeIf { it.isNotEmpty() }
    }

    suspend fun adminUnreserve(context: Context, passphrase: String, name: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("p_key", keyHash(passphrase))
                .put("p_name", normalizeUsername(name))
            rpc("pre_re_admin_unreserve", body)?.optBoolean("value") ?: false
        }

    /** Turns the blue tick on or off for one name. */
    suspend fun adminVerify(
        context: Context,
        passphrase: String,
        name: String,
        verified: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_key", keyHash(passphrase))
            .put("p_username", normalizeUsername(name))
            .put("p_verified", verified)
        rpc("pre_re_admin_verify", body)?.optBoolean("value") ?: false
    }

    data class RenameResult(val status: String, val name: String?)

    /**
     * Changes a name on the owner's behalf. The person's access moves with the name, because
     * the grants row is renamed by the database itself, not by the app.
     */
    suspend fun adminRename(
        context: Context,
        passphrase: String,
        old: String,
        new: String,
    ): RenameResult? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_key", keyHash(passphrase))
            .put("p_old", normalizeUsername(old))
            .put("p_new", normalizeUsername(new))
        val json = rpc("pre_re_admin_rename", body) ?: return@withContext null
        RenameResult(
            status = json.optString("status"),
            name = json.optString("name").takeIf { it.isNotBlank() },
        )
    }

    /** Turns the server's terse codes into something a person can act on. */
    fun claimProblem(status: String?): String = when (status) {
        null -> "Could not reach the name service. Check your connection and try again."
        "ok", "already_yours" -> "" // success
        "taken" -> "That name is already taken. Try another."
        "reserved" -> "That name is kept by P Vault. Pick another."
        "short" -> "Use at least 3 characters."
        "long" -> "Keep it to 24 characters or fewer."
        "chars" -> "Letters, numbers, dots and underscores only, and it must start and end with a letter or number."
        "empty" -> "Type a name first."
        "bad_device" -> "This install could not be identified. Restart P Vault and try again."
        "bad_key" -> "The owner passphrase was not accepted."
        "no_such_name" -> "That name is not on the server any more. Reload the list."
        "same" -> "That is already the name."
        "length" -> "Keep it between 2 and 24 characters."
        else -> "That name could not be saved ($status)."
    }
}
