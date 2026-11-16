package com.dudeapp.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/*
 * Themes that arrive without a new app build.
 *
 * A small catalogue (themes.json) lists each look: a name, whether it is dark, colours as
 * "#RRGGBB" text, an optional picture address and the name of the admin list it belongs to.
 * The app downloads the catalogue, keeps a copy so it works offline, and downloads each
 * picture once. The catalogue lives in the public website repository, so publishing a new
 * look is editing one file and adding one picture; no release is needed.
 */

internal const val THEME_CATALOGUE_URL =
    "https://muhammadshanp.github.io/p-vault-website/themes.json"

/** A look that came from the catalogue rather than from this build. */
data class DownloadedTheme(
    val id: String,
    val label: String,
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val error: Color,
    /** Where the background picture is, or null for a plain colour look. */
    val pictureUrl: String?,
    val scrim: Float,
    /** Only these addresses may use it; empty means everyone with special access. */
    val allowed: List<String>,
    /** Only the admin may use it, on top of special access. */
    val adminOnly: Boolean = false,
    /** The extras of the "Moonlit" style look: artwork in the hero, glassy, tinted tiles. */
    val heroArt: Boolean = false,
    val glass: Boolean = false,
    val tileTints: Boolean = false,
) {
    fun toPreset(): ThemePreset = ThemePreset(
        catalogId = id,
        label = label,
        dark = dark,
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        onBackground = onBackground,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        error = error,
        special = true,
        gradient = listOf(primary, background),
        glow = primary,
        backdrop = pictureUrl?.let { BackdropStyle.Remote(it, scrim) } ?: BackdropStyle.Plain,
        heroArt = heroArt,
        glass = glass,
        tileTints = tileTints,
        adminOnly = adminOnly,
    )
}

/** "#RRGGBB" (or "#AARRGGBB") to a colour; near-black when the text is wrong. */
internal fun parseColor(text: String?, fallback: Long = 0xFF101014): Color {
    val hex = text?.trim()?.removePrefix("#").orEmpty()
    val value = hex.toLongOrNull(16) ?: return Color(fallback)
    return when (hex.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> Color(fallback)
    }
}

private fun JSONObject.color(name: String, fallback: Long): Color =
    if (has(name)) parseColor(optString(name), fallback) else Color(fallback)

private fun themeAt(entry: JSONObject?): DownloadedTheme? {
    if (entry == null) return null
    val id = entry.optString("id").trim()
    val label = entry.optString("label").trim()
    if (id.isEmpty() || label.isEmpty()) return null
    val dark = entry.optBoolean("dark", true)
    val background = entry.color("background", 0xFF0A0A0F)
    val surface = entry.color("surface", 0xFF15151C)
    val onBackground = entry.color("onBackground", if (dark) 0xFFF4F4F8 else 0xFF141414)
    val onSurfaceVariant = entry.color("onSurfaceVariant", if (dark) 0xFF9EA3AE else 0xFF5F6570)
    val allowed = entry.optJSONArray("allowed")?.let { array ->
        (0 until array.length()).mapNotNull { array.optString(it)?.trim()?.lowercase()?.takeIf { it.contains("@") } }
    }.orEmpty()
    return DownloadedTheme(
        id = id,
        label = label,
        dark = dark,
        background = background,
        surface = surface,
        surfaceVariant = entry.color("surfaceVariant", if (dark) 0xFF20202A else 0xFFECEEF2),
        onBackground = onBackground,
        onSurfaceVariant = onSurfaceVariant,
        outline = entry.color("outline", if (dark) 0xFF282833 else 0xFFE2E4E9),
        primary = entry.color("primary", 0xFF7C6BFF),
        onPrimary = entry.color("onPrimary", if (dark) 0xFF0C0820 else 0xFFFFFFFF),
        primaryContainer = entry.color("primaryContainer", if (dark) 0xFF262052 else 0xFFE7E4FF),
        onPrimaryContainer = entry.color("onPrimaryContainer", if (dark) 0xFFD2CBFF else 0xFF2A1D6B),
        secondary = entry.color("secondary", 0xFF5B7BFF),
        error = entry.color("error", 0xFFFF6B6B),
        pictureUrl = entry.optString("picture").trim().takeIf { it.startsWith("http") },
        scrim = entry.optDouble("scrim", 0.4).toFloat().coerceIn(0.1f, 0.9f),
        allowed = allowed,
        adminOnly = entry.optBoolean("adminOnly", false),
        heroArt = entry.optBoolean("heroArt", false),
        glass = entry.optBoolean("glass", false),
        tileTints = entry.optBoolean("tileTints", false),
    )
}

/** True when this install may use the look: no list, or the signed-in address is on it. */
internal fun DownloadedTheme.isAllowedFor(email: String?, specialAccess: Boolean): Boolean {
    if (!specialAccess) return false
    if (allowed.isEmpty()) return true
    val who = email?.trim()?.lowercase() ?: return false
    return who in allowed
}

/** Remembers the catalogue and the downloaded pictures, so it all works offline. */
class ThemeCatalogue(private val context: Context) {
    private val prefs = context.getSharedPreferences("dude_themes", Context.MODE_PRIVATE)

    private val pictureDir: File
        get() = File(context.filesDir, "themes").apply { mkdirs() }

    fun cached(): List<DownloadedTheme> = parseDownloadedThemes(prefs.getString("catalogue", "").orEmpty(), version())

    /** Downloads the catalogue; keeps the last copy when there is no connection. */
    suspend fun refresh(): List<DownloadedTheme>? = withContext(Dispatchers.IO) {
        val body = runCatching {
            val connection = URL(THEME_CATALOGUE_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            try {
                if (connection.responseCode != 200) return@runCatching null
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull() ?: return@withContext null
        // Only keep a catalogue this build understands.
        val parsed = parseDownloadedThemes(body, version())
        if (parsed.isEmpty() && body.contains("\"themes\"")) return@withContext null
        prefs.edit().putString("catalogue", body).apply()
        parsed
    }

    fun pictureFile(theme: DownloadedTheme): File {
        val extension = theme.pictureUrl?.substringAfterLast('.', "jpg")?.take(4) ?: "jpg"
        return File(pictureDir, "${theme.id}.$extension")
    }

    /** The picture on disk, or null when it has not been downloaded yet. */
    fun picture(theme: DownloadedTheme): File? = pictureFile(theme).takeIf { it.length() > 0 }

    /** Downloads one picture; true when a picture is ready afterwards. */
    suspend fun downloadPicture(theme: DownloadedTheme): Boolean = withContext(Dispatchers.IO) {
        val url = theme.pictureUrl ?: return@withContext false
        val target = pictureFile(theme)
        if (target.length() > 0) return@withContext true
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            try {
                if (connection.responseCode != 200) return@runCatching false
                val temporary = File(target.parentFile, target.name + ".part")
                connection.inputStream.use { input -> temporary.outputStream().use { input.copyTo(it) } }
                temporary.renameTo(target)
                target.length() > 0
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }

    /** Deletes pictures no longer in the catalogue. */
    fun tidy(themes: List<DownloadedTheme>) {
        val keep = themes.map { pictureFile(it).name }.toSet()
        pictureDir.listFiles()?.forEach { file -> if (file.name !in keep) file.delete() }
    }

    private fun version(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}

/** Caches decoded pictures, so a list of themes does not re-read the disk while scrolling. */
object ThemePictures {
    private val cache = mutableMapOf<String, Bitmap>()

    fun get(file: File): Bitmap? = synchronized(cache) {
        val key = file.absolutePath + ":" + file.length()
        cache[key] ?: BitmapFactory.decodeFile(file.absolutePath)?.also { cache[key] = it }
    }
}

/** The picture of a downloaded look, scaled to fill the screen. */
@Composable
fun RemoteBackdrop(picture: File, scrimColor: Color, scrim: Float) {
    val bitmap = remember(picture.absolutePath, picture.length()) { ThemePictures.get(picture) }
    Box(Modifier.fillMaxSize()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (scrim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to scrimColor.copy(alpha = scrim),
                            0.45f to scrimColor.copy(alpha = scrim + 0.18f),
                            1f to scrimColor,
                        ),
                    ),
            )
        }
    }
}

/** The picture of a downloaded look, for the small preview cards. */
@Composable
fun themePicture(theme: DownloadedTheme): Bitmap? {
    val context = LocalContext.current
    val file = remember(theme.id) { ThemeCatalogue(context).picture(theme) }
    return remember(file?.absolutePath, file?.length()) { file?.let { ThemePictures.get(it) } }
}

/** The colour fields read from a catalogue entry. Missing ones are worked out from the rest. */
internal fun parseDownloadedThemes(json: String, installedVersion: String): List<DownloadedTheme> {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
    // A catalogue written for a newer app may use fields this build cannot show.
    val needed = root.optString("minVersion")
    if (needed.isNotEmpty() && isNewerVersion(needed, installedVersion)) return emptyList()
    val list = root.optJSONArray("themes") ?: return emptyList()
    return (0 until list.length()).mapNotNull { index -> themeAt(list.optJSONObject(index)) }
}
