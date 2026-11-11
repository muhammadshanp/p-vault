package com.dudeapp.vault

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val TextMain: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurface

val TextSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * Two presets. The coral accent matches the logo; the light theme uses a deeper coral so
 * white button text stays readable, and errors are a real red instead of the accent.
 */
enum class ThemePreset(
    val label: String,
    val dark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
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
        label = "Light",
        dark = false,
        primary = Color(0xFFD9512A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFE6DC),
        onPrimaryContainer = Color(0xFF8A2C0E),
        secondary = Color(0xFFB23E17),
        background = Color(0xFFF4F5F7),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFECEEF2),
        onBackground = Color(0xFF12141A),
        onSurfaceVariant = Color(0xFF5B616E),
        outline = Color(0xFFE1E4EA),
        error = Color(0xFFC62828),
    ),
    DARK(
        label = "Dark",
        dark = true,
        primary = Color(0xFFFF7A4D),
        onPrimary = Color(0xFF1F0D06),
        primaryContainer = Color(0xFF3B2016),
        onPrimaryContainer = Color(0xFFFFB59B),
        secondary = Color(0xFFF08A66),
        background = Color(0xFF0C0D10),
        surface = Color(0xFF17191E),
        surfaceVariant = Color(0xFF23262D),
        onBackground = Color(0xFFF2F3F5),
        onSurfaceVariant = Color(0xFF9EA4B0),
        outline = Color(0xFF2B2F38),
        error = Color(0xFFFF6B6B),
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
    val onError = if (preset.dark) Color(0xFF2A0A0A) else Color.White
    val base = if (preset.dark) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        primary = preset.primary,
        onPrimary = preset.onPrimary,
        primaryContainer = preset.primaryContainer,
        onPrimaryContainer = preset.onPrimaryContainer,
        secondary = preset.secondary,
        onSecondary = if (preset.dark) Color(0xFF1F0D06) else Color.White,
        secondaryContainer = preset.primaryContainer,
        onSecondaryContainer = preset.onPrimaryContainer,
        tertiary = preset.secondary,
        onTertiary = if (preset.dark) Color(0xFF1F0D06) else Color.White,
        tertiaryContainer = preset.primaryContainer,
        onTertiaryContainer = preset.onPrimaryContainer,
        background = preset.background,
        onBackground = preset.onBackground,
        surface = preset.surface,
        onSurface = preset.onBackground,
        surfaceVariant = preset.surfaceVariant,
        onSurfaceVariant = preset.onSurfaceVariant,
        surfaceTint = preset.surface,
        inverseSurface = if (preset.dark) preset.onBackground else Color(0xFF12141A),
        inverseOnSurface = if (preset.dark) preset.background else Color.White,
        error = preset.error,
        onError = onError,
        errorContainer = if (preset.dark) Color(0xFF3A1717) else Color(0xFFFCE4E4),
        onErrorContainer = preset.error,
        outline = preset.outline,
        outlineVariant = preset.outline,
        scrim = Color.Black,
        surfaceBright = if (preset.dark) preset.surfaceVariant else Color.White,
        surfaceDim = if (preset.dark) preset.background else Color(0xFFE6E8EC),
        surfaceContainer = if (preset.dark) Color(0xFF1B1D23) else preset.background,
        surfaceContainerHigh = preset.surfaceVariant,
        surfaceContainerHighest = if (preset.dark) preset.outline else Color(0xFFE4E6EB),
        surfaceContainerLow = preset.surface,
        surfaceContainerLowest = if (preset.dark) preset.background else Color.White,
    )
    MaterialTheme(colorScheme = colors, typography = VaultTypography, shapes = VaultShapes) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background,
            contentColor = colors.onBackground,
        ) {
            content()
        }
    }
}

private val BaseType = Typography()

/** Tighter, bolder headings and slightly roomier body text than the Material defaults. */
private val VaultTypography = Typography(
    headlineLarge = BaseType.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = BaseType.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = BaseType.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseType.bodyLarge.copy(letterSpacing = 0.1.sp),
    bodyMedium = BaseType.bodyMedium.copy(letterSpacing = 0.1.sp),
    labelLarge = BaseType.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Rounder corners everywhere: text fields, chips, menus, dialogs and sheets. */
private val VaultShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** A flat app background; colour comes from the active theme. */
@Composable
fun Backdrop() {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    )
}
