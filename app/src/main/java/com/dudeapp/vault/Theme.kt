package com.dudeapp.vault

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/** White and Dark use the same warm greys and coral accent as the website and logo. */
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
        primary = Color(0xFFE8633A),
        secondary = Color(0xFFB23E17),
        background = Color(0xFFF3F4F2),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFECEEEA),
        onBackground = Color(0xFF16181D),
        onSurfaceVariant = Color(0xFF595C62),
        outline = Color(0xFFDCDEDA),
        error = Color(0xFFB23E17),
    ),
    DARK(
        label = "Dark",
        dark = true,
        primary = Color(0xFFE8633A),
        secondary = Color(0xFFF08A66),
        background = Color(0xFF111317),
        surface = Color(0xFF1A1D23),
        surfaceVariant = Color(0xFF242830),
        onBackground = Color(0xFFF3F4F2),
        onSurfaceVariant = Color(0xFFA3A7AE),
        outline = Color(0xFF2A2E36),
        error = Color(0xFFF08A66),
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
    val onPrimary = Color(0xFF16181D)
    val onSecondary = if (preset.dark) Color(0xFF16181D) else Color.White
    val onError = if (preset.dark) Color(0xFF16181D) else Color.White
    val base = if (preset.dark) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        primary = preset.primary,
        onPrimary = onPrimary,
        primaryContainer = preset.surfaceVariant,
        onPrimaryContainer = preset.primary,
        secondary = preset.secondary,
        onSecondary = onSecondary,
        secondaryContainer = preset.surfaceVariant,
        onSecondaryContainer = preset.secondary,
        tertiary = preset.secondary,
        onTertiary = onSecondary,
        tertiaryContainer = preset.surfaceVariant,
        onTertiaryContainer = preset.secondary,
        background = preset.background,
        onBackground = preset.onBackground,
        surface = preset.surface,
        onSurface = preset.onBackground,
        surfaceVariant = preset.surfaceVariant,
        onSurfaceVariant = preset.onSurfaceVariant,
        surfaceTint = preset.primary,
        inverseSurface = if (preset.dark) preset.onBackground else Color(0xFF16181D),
        inverseOnSurface = if (preset.dark) preset.background else Color.White,
        error = preset.error,
        onError = onError,
        errorContainer = if (preset.dark) Color(0xFF3A211B) else Color(0xFFF7E4DE),
        onErrorContainer = preset.error,
        outline = preset.outline,
        outlineVariant = preset.outline,
        scrim = Color.Black,
        surfaceBright = if (preset.dark) preset.surfaceVariant else Color.White,
        surfaceDim = if (preset.dark) preset.background else Color(0xFFE8EAE5),
        surfaceContainer = if (preset.dark) Color(0xFF1E2128) else preset.background,
        surfaceContainerHigh = preset.surfaceVariant,
        surfaceContainerHighest = if (preset.dark) preset.outline else Color(0xFFE4E6E1),
        surfaceContainerLow = if (preset.dark) preset.surface else Color.White,
        surfaceContainerLowest = if (preset.dark) preset.background else Color.White,
    )
    MaterialTheme(colorScheme = colors, typography = Typography()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background,
            contentColor = colors.onBackground,
        ) {
            content()
        }
    }
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
