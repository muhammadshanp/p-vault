package com.dudeapp.vault

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

val TextMain: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurface

val TextSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

/** White is the normal look; Dark keeps the same layout without the glass effects. */
enum class ThemePreset(
    val label: String,
    val dark: Boolean,
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val error: Color,
) {
    WHITE(
        label = "White",
        dark = false,
        primary = Color(0xFF111827),
        secondary = Color(0xFF2563EB),
        background = Color(0xFFF6F7F9),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEFF1F4),
        onBackground = Color(0xFF171A1F),
        onSurfaceVariant = Color(0xFF6B7280),
        outline = Color(0xFFE2E5EA),
        error = Color(0xFFD93025),
    ),
    DARK(
        label = "Dark",
        dark = true,
        primary = Color(0xFFAFC3FF),
        secondary = Color(0xFF7EE0C8),
        background = Color(0xFF0C0E12),
        surface = Color(0xFF15181E),
        surfaceVariant = Color(0xFF1E222B),
        onBackground = Color(0xFFF2F4F7),
        onSurfaceVariant = Color(0xFFA4ABBA),
        outline = Color(0xFF2C323D),
        error = Color(0xFFFF8A9B),
    ),
}

class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("dude_theme", Context.MODE_PRIVATE)

    fun load(): ThemePreset =
        runCatching { ThemePreset.valueOf(prefs.getString("preset", "").orEmpty()) }
            .getOrDefault(ThemePreset.WHITE)

    fun save(preset: ThemePreset) {
        prefs.edit().putString("preset", preset.name).apply()
    }
}

@Composable
fun PVaultTheme(
    preset: ThemePreset = ThemePreset.WHITE,
    content: @Composable () -> Unit,
) {
    val onAccent = if (preset.dark) preset.background else Color.White
    val base = if (preset.dark) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        primary = preset.primary,
        onPrimary = onAccent,
        primaryContainer = preset.surfaceVariant,
        onPrimaryContainer = preset.primary,
        secondary = preset.secondary,
        onSecondary = onAccent,
        secondaryContainer = preset.surfaceVariant,
        onSecondaryContainer = preset.secondary,
        tertiary = preset.secondary,
        onTertiary = onAccent,
        tertiaryContainer = preset.surfaceVariant,
        onTertiaryContainer = preset.secondary,
        background = preset.background,
        onBackground = preset.onBackground,
        surface = preset.surface,
        onSurface = preset.onBackground,
        surfaceVariant = preset.surfaceVariant,
        onSurfaceVariant = preset.onSurfaceVariant,
        surfaceTint = preset.primary,
        inverseSurface = if (preset.dark) preset.onBackground else Color(0xFF1F2937),
        inverseOnSurface = if (preset.dark) preset.background else Color.White,
        error = preset.error,
        onError = onAccent,
        errorContainer = if (preset.dark) Color(0xFF4A1D24) else Color(0xFFFCE8E6),
        onErrorContainer = preset.error,
        outline = preset.outline,
        outlineVariant = preset.outline,
        scrim = Color.Black,
        surfaceBright = if (preset.dark) preset.surfaceVariant else Color.White,
        surfaceDim = if (preset.dark) preset.background else Color(0xFFE9EBEF),
        surfaceContainer = if (preset.dark) Color(0xFF191D24) else preset.background,
        surfaceContainerHigh = if (preset.dark) Color(0xFF1E232C) else preset.surfaceVariant,
        surfaceContainerHighest = if (preset.dark) Color(0xFF262C36) else Color(0xFFE7EAEF),
        surfaceContainerLow = if (preset.dark) preset.surface else Color.White,
        surfaceContainerLowest = if (preset.dark) preset.background else Color.White,
    )
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

/** A flat app background; colour comes from the active theme. */
@Composable
fun Backdrop() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    )
}
