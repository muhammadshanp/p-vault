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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
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
internal const val DOCS_ROOT = "projects/p-vault-app/databases/(default)/documents"
internal const val FIRESTORE = "https://firestore.googleapis.com/v1/$DOCS_ROOT"
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

/**
 * What one address may do. The admin gives each address a level and, look by look, which
 * of the shared looks it may use.
 *
 * The order matters: a higher level includes everything the lower one allows.
 */
enum class AccessLevel(val label: String, val detail: String) {
    NONE("No access", "Everything special stays locked."),
    THEME("Theme", "The Theme menu and the picture looks."),
    ADVANCED("Theme + Advanced", "The Theme menu, the Advanced page and the advanced looks."),
    EVERYTHING("Everything", "Every look and every switch, but not the admin panel."),
    ;

    /** Everyone above NONE is on the list at all. */
    val member: Boolean get() = this != NONE

    fun allows(other: AccessLevel): Boolean = ordinal >= other.ordinal

    companion object {
        fun of(name: String?): AccessLevel =
            entries.firstOrNull { it.name.equals(name.orEmpty(), ignoreCase = true) } ?: NONE
    }
}

/** The switches that can be given to one address; more can be added over time. */
enum class AccessFeature(val label: String, val detail: String) {
    SUGGESTIONS("Give suggestion", "The \"Give suggestion\" item in the menu."),
    ASSISTANT("Assistant", "The Assistant page in the menu."),
    ;

    /** The key it is stored under: short, stable, and what an older phone still reads. */
    val key: String get() = name.lowercase()

    companion object {
        fun of(key: String): AccessFeature? = entries.firstOrNull { it.key == key.lowercase() }
    }
}

/**
 * The id a look is shared under: the catalogue id, or its label for a look built into the
 * app. It is what the admin ticks in the access list.
 */
fun ThemePreset.shareId(): String = catalogId ?: label.trim().lowercase()

/** The field a shared look is stored under, e.g. `look_nightfall`. */
internal fun lookField(lookId: String): String = "look_" + lookId.trim().lowercase()
internal fun featureField(feature: AccessFeature): String = "f_" + feature.key

/**
 * The whole list, as it is stored in the public Firestore document. Every list holds SHA-256
 * fingerprints, never an address. `hashes` is everybody on the list, which is what an older
 * build of the app reads; the other lists are the levels, the switches and the shared looks.
 */
data class AccessDocument(
    val hashes: Set<String> = emptySet(),
    val theme: Set<String> = emptySet(),
    val advanced: Set<String> = emptySet(),
    val everything: Set<String> = emptySet(),
    val features: Map<AccessFeature, Set<String>> = emptyMap(),
    val looks: Map<String, Set<String>> = emptyMap(),
) {
    fun level(hash: String): AccessLevel = when {
        hash in everything -> AccessLevel.EVERYTHING
        hash in advanced -> AccessLevel.ADVANCED
        hash in theme -> AccessLevel.THEME
        // An older build only stored who was on the list at all; they had the theme.
        hash in hashes -> AccessLevel.THEME
        else -> AccessLevel.NONE
    }

    /** The looks given to this address by id; the top level has every look. */
    fun looksOf(hash: String, level: AccessLevel): Set<String> =
        if (level == AccessLevel.EVERYTHING) looks.keys else looks.filterValues { hash in it }.keys

    fun featuresOf(hash: String): Set<AccessFeature> =
        features.filterValues { hash in it }.keys

    /** The same list with one address at a new level, with new switches and shared looks. */
    fun with(
        hash: String,
        level: AccessLevel,
        features: Set<AccessFeature> = emptySet(),
        looks: Set<String> = emptySet(),
    ): AccessDocument {
        fun atLeast(target: AccessLevel) = level.allows(target)
        fun keep(from: Set<String>, allowed: Boolean) = if (allowed) from + hash else from - hash
        fun <K> keepGroup(from: Map<K, Set<String>>, given: Set<K>) =
            (from.keys + given).associateWith { key -> keep(from[key].orEmpty(), key in given) }
                .filterValues { it.isNotEmpty() }
        return AccessDocument(
            hashes = keep(hashes, level.member),
            theme = keep(theme, atLeast(AccessLevel.THEME)),
            advanced = keep(advanced, atLeast(AccessLevel.ADVANCED)),
            everything = keep(everything, atLeast(AccessLevel.EVERYTHING)),
            // Only what is passed stays: the stored map is reached through `this`.
            features = keepGroup(this.features, features.filter { level.member }.toSet()),
            looks = keepGroup(this.looks, looks.map { it.trim().lowercase() }.toSet()),
        )
    }

    /** Every field of the public document, including empty ones, so removals stick. */
    internal fun fieldsJson(): JSONObject {
        val fields = JSONObject()
            .put("hashes", arrayField(hashes.sorted()))
            .put("theme", arrayField(theme.sorted()))
            .put("advanced", arrayField(advanced.sorted()))
            .put("everything", arrayField(everything.sorted()))
        features.forEach { (feature, hashes) -> fields.put(featureField(feature), arrayField(hashes.sorted())) }
        looks.forEach { (id, hashes) -> fields.put(lookField(id), arrayField(hashes.sorted())) }
        return fields
    }

    /**
     * The request that saves the public document in one step. The whole document is written,
     * empty lists included, so a switch or a look taken away really disappears.
     */
    fun commitJson(): String = JSONObject().put(
        "writes",
        JSONArray().put(
            JSONObject().put(
                "update",
                JSONObject()
                    .put("name", "$DOCS_ROOT/access/special")
                    .put("fields", fieldsJson()),
            ),
        ),
    ).toString()

    companion object {
        fun parse(json: String): AccessDocument = runCatching {
            val fields = JSONObject(json).optJSONObject("fields") ?: JSONObject()
            fun list(field: String): Set<String> = parseStringList(json, field).toSet()
            fun pairs(prefix: String): Map<String, Set<String>> =
                fields.keys().asSequence()
                    .filter { it.startsWith(prefix) }
                    .map { it.removePrefix(prefix) to list(it) }
                    .filter { it.second.isNotEmpty() }
                    .toMap()
            AccessDocument(
                hashes = list("hashes"),
                theme = list("theme"),
                advanced = list("advanced"),
                everything = list("everything"),
                features = pairs("f_").mapNotNull { (key, set) -> AccessFeature.of(key)?.let { it to set } }.toMap(),
                looks = pairs("look_").filterKeys { it.isNotBlank() },
            )
        }.getOrDefault(AccessDocument())

    }
}

/** What this phone knows about its own account: its level, its switches, its looks. */
data class MyAccess(
    val level: AccessLevel = AccessLevel.NONE,
    val features: Set<AccessFeature> = emptySet(),
    /** The ids of the shared looks given to this account. */
    val looks: Set<String> = emptySet(),
    /** The admin keeps everything, whatever the list says. */
    val admin: Boolean = false,
) {
    val theme: Boolean get() = admin || level.allows(AccessLevel.THEME)
    val advanced: Boolean get() = admin || level.allows(AccessLevel.ADVANCED)

    fun has(feature: AccessFeature): Boolean = admin || feature in features

    /** True when this account may use a look; a build-in look has no catalogue id. */
    fun allowsLook(lookId: String?): Boolean = admin || lookId == null || lookId in looks
}

private fun arrayField(values: List<String>): JSONObject {
    val items = JSONArray()
    values.forEach { items.put(JSONObject().put("stringValue", it)) }
    return JSONObject().put("arrayValue", JSONObject().put("values", items))
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
/** One address on the admin's list, with what it has been given. */
data class AccessPerson(
    val email: String,
    val level: AccessLevel = AccessLevel.NONE,
    val features: Set<AccessFeature> = emptySet(),
    val looks: Set<String> = emptySet(),
)

/** The list as it should be stored, built from what the admin sees on screen. */
internal fun accessDocumentOf(people: List<AccessPerson>): AccessDocument =
    people.fold(AccessDocument()) { document, person ->
        document.with(emailHash(person.email), person.level, person.features, person.looks)
    }

/** The addresses and what each may do, read back from the two documents. */
internal fun accessPeople(emails: List<String>, document: AccessDocument): List<AccessPerson> =
    emails.map { email ->
        val hash = emailHash(email)
        val level = document.level(hash)
        AccessPerson(
            email = email,
            level = level,
            features = document.featuresOf(hash),
            looks = document.looksOf(hash, level),
        )
    }

/** The request that saves the address list and every level, switch and look in one step. */
internal fun accessCommitJson(people: List<AccessPerson>): String {
    val document = accessDocumentOf(people)
    fun write(doc: String, fields: JSONObject) = JSONObject().put(
        "update",
        JSONObject().put("name", "$DOCS_ROOT/access/$doc").put("fields", fields),
    )
    return JSONObject().put(
        "writes",
        JSONArray()
            .put(write("emails", JSONObject().put("emails", arrayField(people.map { it.email }.sorted()))))
            .put(write("special", document.fieldsJson())),
    ).toString()
}

class AccessStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("dude_access", Context.MODE_PRIVATE)

    /** What the signed-in address may do, as last read from the admin's list. */
    fun access(): MyAccess {
        val admin = AdminStore(context).isAdmin()
        val level = AccessLevel.of(prefs.getString("level", null))
        val features = prefs.getStringSet("features", emptySet()).orEmpty()
            .mapNotNull(AccessFeature::of).toSet()
        val looks = prefs.getStringSet("looks", emptySet()).orEmpty()
        return MyAccess(level = level, features = features, looks = looks, admin = admin)
    }

    /** True for the admin and for a signed-in address on the admin's list. */
    fun isSpecial(): Boolean = access().theme

    /** True only for a signed-in address on the admin's list; this is what Firebase checks too. */
    fun isMember(): Boolean = access().level.member

    fun clear() {
        prefs.edit().clear().apply()
    }

    /** Looks the signed-in address up in the public list. Keeps the last answer when offline. */
    suspend fun refresh(): MyAccess {
        val email = GoogleAccountStore(context).email()
        if (email == null) {
            clear()
            return access()
        }
        val document = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL("$FIRESTORE/access/special").openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                try {
                    when (connection.responseCode) {
                        200 -> AccessDocument.parse(connection.inputStream.bufferedReader().use { it.readText() })
                        404 -> AccessDocument()
                        else -> null
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
        if (document != null) {
            val hash = emailHash(email)
            val level = document.level(hash)
            prefs.edit()
                .putString("level", level.name)
                .putStringSet("features", document.featuresOf(hash).map { it.key }.toSet())
                .putStringSet("looks", document.looksOf(hash, level))
                .apply()
        }
        return access()
    }
}

private fun databaseProblem(code: Int): String = when (code) {
    401 -> "Google sign-in expired. Try again."
    403 -> "This Google account may not change the list, or the Firestore database is not turned on."
    404 -> "The Firestore database is not created yet in the Firebase console."
    else -> "Firebase refused the request ($code)."
}

/**
 * The admin's list of addresses, and what each one may do: a level (Theme, Theme + Advanced,
 * Everything), each switch, and each shared look. Everything is saved in one step.
 */
@Composable
fun SpecialAccessCard(
    busy: Boolean,
    /** The looks that can be shared, by id and name; the admin decides which are open. */
    shareableLooks: List<Pair<String, String>>,
    withGoogle: ((String) -> Unit) -> Unit,
    onDone: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var people by remember { mutableStateOf<List<AccessPerson>?>(null) }
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }

    fun load() = withGoogle { token ->
        scope.launch {
            val emails = googleRequest("$FIRESTORE/access/emails", token, null)
            val special = googleRequest("$FIRESTORE/access/special", token, null)
            when {
                emails == null || special == null -> onDone("No connection.")
                emails.first == 401 || emails.first == 403 -> onDone(databaseProblem(emails.first))
                emails.first == 200 -> {
                    val document = if (special.first == 200) AccessDocument.parse(special.second) else AccessDocument()
                    people = accessPeople(parseStringList(emails.second, "emails"), document)
                    onDone(null)
                }
                emails.first == 404 -> {
                    people = emptyList()
                    onDone(null)
                }
                else -> onDone(databaseProblem(emails.first))
            }
        }
    }

    fun save(next: List<AccessPerson>) = withGoogle { token ->
        scope.launch {
            val response = googleRequest("$FIRESTORE:commit", token, accessCommitJson(next))
            when {
                response == null -> onDone("No connection. The list was not changed.")
                response.first == 200 -> {
                    people = next
                    draft = ""
                    editing = null
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
            Text("Access per address", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Each Google address gets what you give it: Theme, Theme + Advanced, or Everything. " +
                    "The Advanced page then opens only for the addresses you allow." +
                    "It reaches their phone the next time they open the app online.",
                color = TextSoft,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            val list = people
            if (list == null) {
                Button(onClick = { load() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Working…" else "Show the list")
                }
            } else {
                if (list.isEmpty()) {
                    Text("No addresses yet.", color = TextSoft, fontSize = 13.sp)
                }
                list.sortedBy { it.email }.forEach { person ->
                    val open = editing == person.email
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                person.email,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                person.level.label +
                                    if (person.looks.isEmpty()) "" else " · ${person.looks.size} look(s)",
                                color = TextSoft,
                                fontSize = 11.sp,
                            )
                        }
                        TextButton(onClick = { editing = if (open) null else person.email }) {
                            Text(if (open) "Done" else "Change", fontSize = 12.sp)
                        }
                        TextButton(onClick = { save(list - person) }, enabled = !busy) {
                            Text("Remove", fontSize = 12.sp)
                        }
                    }
                    if (open) {
                        // The level: one tap, and it applies to everything below it.
                        Column(Modifier.padding(start = 2.dp, bottom = 8.dp)) {
                            AccessLevel.entries.forEach { level ->
                                val chosen = person.level == level
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = chosen,
                                        onClick = {
                                            people = list.map {
                                                if (it.email == person.email) it.copy(level = level) else it
                                            }
                                        },
                                        enabled = !busy,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(level.label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text(level.detail, color = TextSoft, fontSize = 11.sp)
                                    }
                                }
                            }
                            // The switches, each on its own.
                            AccessFeature.entries.forEach { feature ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = feature in person.features,
                                        onCheckedChange = { on ->
                                            people = list.map {
                                                if (it.email != person.email) {
                                                    it
                                                } else if (on) {
                                                    it.copy(features = it.features + feature)
                                                } else {
                                                    it.copy(features = it.features - feature)
                                                }
                                            }
                                        },
                                        enabled = !busy && person.level.member,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(feature.label, fontSize = 13.sp)
                                        Text(feature.detail, color = TextSoft, fontSize = 11.sp)
                                    }
                                }
                            }
                            // The shared looks, one by one, as their ids are known here.
                            if (shareableLooks.isNotEmpty() && person.level.allows(AccessLevel.ADVANCED)) {
                                Spacer(Modifier.height(4.dp))
                                Text("Looks shared with this address", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                shareableLooks.forEach { (id, label) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = id in person.looks,
                                            onCheckedChange = { on ->
                                                people = list.map {
                                                    if (it.email != person.email) {
                                                        it
                                                    } else if (on) {
                                                        it.copy(looks = it.looks + id)
                                                    } else {
                                                        it.copy(looks = it.looks - id)
                                                    }
                                                }
                                            },
                                            enabled = !busy,
                                        )
                                        Text(label, fontSize = 13.sp)
                                    }
                                }
                            }
                            Button(
                                onClick = { save(list.map { if (it.email == person.email) person else it }) },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (busy) "Saving…" else "Save ${person.email}")
                            }
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
                    onClick = { if (clean != null) save(list + AccessPerson(clean, AccessLevel.THEME)) },
                    enabled = !busy && clean != null && list.none { it.email == clean } && list.size < MAX_SPECIAL,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Working…" else "Add with Theme access")
                }
            }
        }
    }
}
