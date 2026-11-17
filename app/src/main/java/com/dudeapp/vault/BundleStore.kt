package com.dudeapp.vault

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A theme made on this phone: a background photo, a photo for each card, and a name. It is a
 * normal look, so picking it works exactly like picking a built-in one.
 */
data class OwnTheme(
    val id: String,
    val name: String,
    /** The background photo, or null for the look's plain colour. */
    val background: File? = null,
    /** The photo behind each card. A card with no photo keeps the look's own picture. */
    val cards: Map<CardPicture, File> = emptyMap(),
)

/**
 * A bundle: a name and the themes inside it. A bundle only points at its themes, so the same
 * theme can sit in more than one bundle, and deleting a bundle never deletes a theme.
 */
data class ThemeBundle(
    val id: String,
    val name: String,
    val themeIds: List<String> = emptyList(),
)

/**
 * The bundles and the themes this phone made, saved in the app's own folder so they work
 * offline. Nothing leaves the phone.
 */
class BundleStore(private val context: Context) {
    private companion object {
        const val APPLIED = "applied"
    }

    private val prefs = context.getSharedPreferences("dude_bundles", Context.MODE_PRIVATE)
    private val settings = context.getSharedPreferences("dude_settings", Context.MODE_PRIVATE)

    private val themeDir: File
        get() = File(context.filesDir, "own_themes").apply { mkdirs() }

    fun bundles(): List<ThemeBundle> = read().first

    fun themes(): List<OwnTheme> = read().second

    fun theme(id: String): OwnTheme? = themes().firstOrNull { it.id == id }

    /**
     * The theme this phone was wearing when it was last used. Kept even while advanced mode is
     * off, so turning advanced back on puts the look back exactly as it was.
     */
    fun appliedId(): String? = prefs.getString(APPLIED, null)

    fun setApplied(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(APPLIED) else putString(APPLIED, id)
        }.apply()
    }

    /** A new, empty bundle. */
    fun addBundle(name: String): ThemeBundle {
        val (bundles, themes) = read()
        val bundle = ThemeBundle(id = UUID.randomUUID().toString(), name = name.trim())
        write(bundles + bundle, themes)
        return bundle
    }

    fun renameBundle(id: String, name: String) {
        val (bundles, themes) = read()
        write(bundles.map { if (it.id == id) it.copy(name = name.trim()) else it }, themes)
    }

    /** Forgets a bundle. The themes it held stay, ready for another bundle. */
    fun removeBundle(id: String) {
        val (bundles, themes) = read()
        write(bundles.filterNot { it.id == id }, themes)
    }

    /** Puts one theme into a bundle, once. */
    fun addToBundle(bundleId: String, themeId: String) {
        val (bundles, themes) = read()
        write(
            bundles.map {
                if (it.id == bundleId && themeId !in it.themeIds) it.copy(themeIds = it.themeIds + themeId) else it
            },
            themes,
        )
    }

    /** Takes a theme out of one bundle, leaving it in any other. */
    fun removeFromBundle(bundleId: String, themeId: String) {
        val (bundles, themes) = read()
        write(bundles.map { if (it.id == bundleId) it.copy(themeIds = it.themeIds - themeId) else it }, themes)
    }

    /** Deletes a theme outright, out of every bundle. */
    fun deleteTheme(themeId: String) {
        val (bundles, themes) = read()
        themes.firstOrNull { it.id == themeId }?.let { old ->
            old.background?.delete()
            old.cards.values.forEach { it.delete() }
        }
        write(bundles.map { it.copy(themeIds = it.themeIds - themeId) }, themes.filterNot { it.id == themeId })
    }

    /** Copies a picture into the app and returns it, or null when it could not be read. */
    suspend fun savePicture(uri: Uri, name: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(themeDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                true
            }?.takeIf { it && target.length() > 0 }?.let { target }
        }.getOrNull()
    }

    /** Saves a theme made on this phone; returns the saved theme. */
    fun saveTheme(theme: OwnTheme) {
        val (bundles, themes) = read()
        val others = themes.filterNot { it.id == theme.id }
        // Pictures that the new version no longer uses are removed.
        themes.firstOrNull { it.id == theme.id }?.let { old ->
            old.background?.takeIf { it != theme.background }?.delete()
            old.cards.forEach { (kind, file) -> if (theme.cards[kind] != file) file.delete() }
        }
        write(bundles, others + theme)
    }

    /** A fresh id for a new theme. */
    fun newThemeId(): String = UUID.randomUUID().toString()

    /** The array under [name] in a JSON object, or an empty list when it is missing or wrong. */
private fun JSONObject.array(name: String): List<Any> =
    optJSONArray(name)?.let { list -> (0 until list.length()).mapNotNull { list.opt(it) } }.orEmpty()

private fun read(): Pair<List<ThemeBundle>, List<OwnTheme>> = runCatching<Pair<List<ThemeBundle>, List<OwnTheme>>> {
        val root = JSONObject(prefs.getString("data", "{}").orEmpty())
        val bundles = root.array("bundles").mapNotNull { raw ->
            val item = raw as? JSONObject ?: return@mapNotNull null
            ThemeBundle(
                id = item.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
                name = item.optString("name"),
                themeIds = item.array("themes").mapNotNull { it as? String },
            )
        }
        val themes = root.array("themes").mapNotNull { raw ->
            val item = raw as? JSONObject ?: return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val cards = item.optJSONObject("cards") ?: JSONObject()
            OwnTheme(
                id = id,
                name = item.optString("name"),
                background = item.optString("background").takeIf { it.isNotEmpty() }?.let { File(it) }?.takeIf { it.isFile },
                cards = CardPicture.entries.mapNotNull { kind ->
                    cards.optString(kind.name).takeIf { it.isNotEmpty() }?.let { File(it) }?.takeIf { it.isFile }?.let { kind to it }
                }.toMap(),
            )
        }
        bundles to themes
    }.getOrDefault(emptyList<ThemeBundle>() to emptyList<OwnTheme>())

    private fun write(bundles: List<ThemeBundle>, themes: List<OwnTheme>) {
        val root = JSONObject()
            .put(
                "bundles",
                JSONArray().apply {
                    bundles.forEach { bundle ->
                        put(
                            JSONObject()
                                .put("id", bundle.id)
                                .put("name", bundle.name)
                                .put("themes", JSONArray(bundle.themeIds)),
                        )
                    }
                },
            )
            .put(
                "themes",
                JSONArray().apply {
                    themes.forEach { theme ->
                        put(
                            JSONObject()
                                .put("id", theme.id)
                                .put("name", theme.name)
                                .put("background", theme.background?.absolutePath.orEmpty())
                                .put(
                                    "cards",
                                    JSONObject().apply {
                                        theme.cards.forEach { (kind, file) -> put(kind.name, file.absolutePath) }
                                    },
                                ),
                        )
                    }
                },
            )
        prefs.edit().putString("data", root.toString()).apply()
    }
}

/** The bundles and themes, as the screens read them; a change redraws every list. */
object Bundles {
    val current = mutableStateOf<Pair<List<ThemeBundle>, List<OwnTheme>>>(emptyList<ThemeBundle>() to emptyList())

    /**
     * The theme in use on this phone, so the whole app can show it. It is only ever set while
     * advanced mode is on: advanced off means the plain look, with the theme's own background
     * and cards.
     */
    val applied = mutableStateOf<OwnTheme?>(null)

    /** True while advanced mode is on. Advanced off means no advanced look at all. */
    val advancedOn = mutableStateOf(true)

    /** The theme that should be showing, which is none at all while advanced mode is off. */
    val worn: OwnTheme?
        get() = if (advancedOn.value) applied.value else null

    fun publish(store: BundleStore) {
        current.value = store.bundles() to store.themes()
    }
}
