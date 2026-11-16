package com.dudeapp.vault

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
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
 * The looks P Vault can wear. Light and Dark are free; the rest need special access.
 * The coral accent matches the logo; the light theme uses a deeper coral so white button
 * text stays readable, and errors are a real red instead of the accent.
 */
data class ThemePreset(
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
    /** A look that only people with special access can choose. */
    val special: Boolean = false,
    /** Set when the look came from the theme catalogue, not from this build. */
    val catalogId: String? = null,
    /** A look only the admin may use, on top of special access. */
    val adminOnly: Boolean = false,
    /** The vault card and the home strip; two colours, darker last. */
    val gradient: List<Color>? = null,
    /** Tints the shadow behind the floating bar's selected pill. */
    val glow: Color? = null,
    val backdrop: BackdropStyle = BackdropStyle.Plain,
    /** The picture also sits inside the hero card on Home. */
    val heroArt: Boolean = false,
    /** Cards are slightly see-through, so the backdrop shows through them. */
    val glass: Boolean = false,
    /** The four Home tiles take a wash of their own colour. */
    val tileTints: Boolean = false,
    /**
     * A picture inside each kind of card, on top of the backdrop picture: a look whose
     * background and cards are designed separately.
     */
    val cardPictures: Map<CardPicture, Int> = emptyMap(),
) {

    companion object {

        /** Every look inside this build, in the order the theme page shows them. */
        val all: List<ThemePreset>
            get() = listOf(WHITE, DARK, NIGHTFALL, MOONLIT, MIDNIGHT, RACING, GRAND_LINE, SHINOBI, SAKURA, FOREST, GOLD, MINIMAL, NEON, OCEAN)

        val WHITE = ThemePreset(
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
        )

        val DARK = ThemePreset(
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
        )

        val MIDNIGHT = ThemePreset(
        label = "Midnight",
        dark = true,
        primary = Color(0xFF7C6BFF),
        onPrimary = Color(0xFF0A0820),
        primaryContainer = Color(0xFF241F52),
        onPrimaryContainer = Color(0xFFCBC2FF),
        secondary = Color(0xFF5B7BFF),
        background = Color(0xFF08080F),
        surface = Color(0xFF12121C),
        surfaceVariant = Color(0xFF1D1D2A),
        onBackground = Color(0xFFF1F1F7),
        onSurfaceVariant = Color(0xFFA0A0B4),
        outline = Color(0xFF26263A),
        error = Color(0xFFFF6B6B),
        special = true,
        gradient = listOf(Color(0xFF6C4BFF), Color(0xFF1B1038)),
        glow = Color(0xFF4B33D6),
        backdrop = BackdropStyle.Picture(R.drawable.bg_animegirl, scrim = 0.45f),
        )

        val RACING = ThemePreset(
        label = "Racing",
        dark = true,
        primary = Color(0xFFE10600),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF3A0705),
        onPrimaryContainer = Color(0xFFFFB3AD),
        secondary = Color(0xFFFF3B30),
        background = Color(0xFF050505),
        surface = Color(0xFF101012),
        surfaceVariant = Color(0xFF1A1A1D),
        onBackground = Color(0xFFF4F4F6),
        onSurfaceVariant = Color(0xFFA2A2AA),
        outline = Color(0xFF26262B),
        error = Color(0xFFFF5A4D),
        special = true,
        gradient = listOf(Color(0xFFE10600), Color(0xFF1A0100)),
        glow = Color(0xFFE10600),
        backdrop = BackdropStyle.Picture(R.drawable.bg_grandprix, scrim = 0.35f),
        )

        val GRAND_LINE = ThemePreset(
        label = "Grand Line",
        dark = true,
        primary = Color(0xFFFFC53D),
        onPrimary = Color(0xFF241A00),
        primaryContainer = Color(0xFF3B2A05),
        onPrimaryContainer = Color(0xFFFFE1A0),
        secondary = Color(0xFFE5484D),
        background = Color(0xFF0B0806),
        surface = Color(0xFF171210),
        surfaceVariant = Color(0xFF221B17),
        onBackground = Color(0xFFF8F2EA),
        onSurfaceVariant = Color(0xFFAF9F92),
        outline = Color(0xFF302520),
        error = Color(0xFFFF6B5E),
        special = true,
        gradient = listOf(Color(0xFFE5484D), Color(0xFF2A0A06)),
        glow = Color(0xFFB3261E),
        backdrop = BackdropStyle.Picture(R.drawable.bg_strawhat, scrim = 0.4f),
        )

        val SHINOBI = ThemePreset(
        label = "Shinobi",
        dark = true,
        primary = Color(0xFFFF8A1F),
        onPrimary = Color(0xFF201000),
        primaryContainer = Color(0xFF3D2008),
        onPrimaryContainer = Color(0xFFFFC98A),
        secondary = Color(0xFF4C7DFF),
        background = Color(0xFF08090C),
        surface = Color(0xFF121418),
        surfaceVariant = Color(0xFF1C1F25),
        onBackground = Color(0xFFF3F4F7),
        onSurfaceVariant = Color(0xFF9CA1AC),
        outline = Color(0xFF272B33),
        error = Color(0xFFFF6B5E),
        special = true,
        gradient = listOf(Color(0xFFFF8A1F), Color(0xFF2A1400)),
        glow = Color(0xFFC25A00),
        backdrop = BackdropStyle.Picture(R.drawable.bg_ninja, scrim = 0.4f),
        )

        val SAKURA = ThemePreset(
        label = "Sakura",
        dark = false,
        primary = Color(0xFFD6336C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFE3EC),
        onPrimaryContainer = Color(0xFF7A1738),
        secondary = Color(0xFFB02A5B),
        background = Color(0xFFFDF5F8),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF7E8EE),
        onBackground = Color(0xFF1D1418),
        onSurfaceVariant = Color(0xFF7A6670),
        outline = Color(0xFFEFD9E2),
        error = Color(0xFFC0392B),
        special = true,
        gradient = listOf(Color(0xFFF06A9B), Color(0xFFE8457A)),
        glow = Color(0xFFF2A7C3),
        )

        val FOREST = ThemePreset(
        label = "Forest",
        dark = true,
        primary = Color(0xFF34C77B),
        onPrimary = Color(0xFF04180D),
        primaryContainer = Color(0xFF0E3521),
        onPrimaryContainer = Color(0xFFB6F2D0),
        secondary = Color(0xFF7BD389),
        background = Color(0xFF07100B),
        surface = Color(0xFF0F1A13),
        surfaceVariant = Color(0xFF17251B),
        onBackground = Color(0xFFEFF6F1),
        onSurfaceVariant = Color(0xFF97AC9D),
        outline = Color(0xFF1F2E24),
        error = Color(0xFFFF6B6B),
        special = true,
        gradient = listOf(Color(0xFF2FB86F), Color(0xFF0B2A18)),
        glow = Color(0xFF1E7A47),
        )

        val GOLD = ThemePreset(
        label = "Gold",
        dark = true,
        primary = Color(0xFFE3B341),
        onPrimary = Color(0xFF1C1500),
        primaryContainer = Color(0xFF3A2E08),
        onPrimaryContainer = Color(0xFFFFE7A3),
        secondary = Color(0xFFD4A017),
        background = Color(0xFF0A0A0A),
        surface = Color(0xFF141414),
        surfaceVariant = Color(0xFF1E1C17),
        onBackground = Color(0xFFF6F4EF),
        onSurfaceVariant = Color(0xFFA9A395),
        outline = Color(0xFF2A2722),
        error = Color(0xFFFF6B5E),
        special = true,
        gradient = listOf(Color(0xFFD9A83A), Color(0xFF6B4E10)),
        glow = Color(0xFF8A6A1E),
        )

        val MINIMAL = ThemePreset(
        label = "Minimal",
        dark = false,
        primary = Color(0xFF141414),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEDEDED),
        onPrimaryContainer = Color(0xFF141414),
        secondary = Color(0xFF3D3D3D),
        background = Color(0xFFFBFBFB),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF1F1F1),
        onBackground = Color(0xFF171717),
        onSurfaceVariant = Color(0xFF767676),
        outline = Color(0xFFE8E8E8),
        error = Color(0xFFB3261E),
        special = true,
        gradient = listOf(Color(0xFF2B2B2B), Color(0xFF0E0E0E)),
        glow = Color(0xFF9E9E9E),
        )

        val NEON = ThemePreset(
        label = "Neon",
        dark = true,
        primary = Color(0xFF00E5FF),
        onPrimary = Color(0xFF001418),
        primaryContainer = Color(0xFF04323A),
        onPrimaryContainer = Color(0xFFA6F4FF),
        secondary = Color(0xFFFF2BD6),
        background = Color(0xFF07030E),
        surface = Color(0xFF120A1E),
        surfaceVariant = Color(0xFF1C1030),
        onBackground = Color(0xFFF6F0FF),
        onSurfaceVariant = Color(0xFFAF9ECC),
        outline = Color(0xFF2A1B45),
        error = Color(0xFFFF5C7A),
        special = true,
        gradient = listOf(Color(0xFFFF2BD6), Color(0xFF1B0B3A)),
        glow = Color(0xFFCE00AE),
        )

        val OCEAN = ThemePreset(
        label = "Ocean",
        dark = true,
        primary = Color(0xFF2EC4D6),
        onPrimary = Color(0xFF00171A),
        primaryContainer = Color(0xFF073038),
        onPrimaryContainer = Color(0xFFA8EEF6),
        secondary = Color(0xFF3B82F6),
        background = Color(0xFF050C12),
        surface = Color(0xFF0E1720),
        surfaceVariant = Color(0xFF14222E),
        onBackground = Color(0xFFEEF5F9),
        onSurfaceVariant = Color(0xFF93A8B6),
        outline = Color(0xFF1B2A36),
        error = Color(0xFFFF6B6B),
        special = true,
        gradient = listOf(Color(0xFF1C9EC9), Color(0xFF062A45)),
        glow = Color(0xFF0F6E8C),
        )

        /** The look of the reference picture: violet night, glassy cards, artwork in the hero. */
        val MOONLIT = ThemePreset(
        label = "Moonlit",
        adminOnly = true,
        dark = true,
        primary = Color(0xFF8B5CF6),
        onPrimary = Color(0xFF120B2A),
        primaryContainer = Color(0xFF2A1B52),
        onPrimaryContainer = Color(0xFFD5C8FF),
        secondary = Color(0xFFA78BFA),
        background = Color(0xFF08060F),
        surface = Color(0xFF121020),
        surfaceVariant = Color(0xFF1D1A2E),
        onBackground = Color(0xFFF2EFFA),
        onSurfaceVariant = Color(0xFFA79FBF),
        outline = Color(0xFF2A2540),
        error = Color(0xFFFF6B6B),
        special = true,
        gradient = listOf(Color(0xFF6D28D9), Color(0xFF2A1052)),
        glow = Color(0xFF7C3AED),
        backdrop = BackdropStyle.Picture(R.drawable.bg_moonlit, scrim = 0.5f),
        heroArt = true,
        glass = true,
        tileTints = true,
        )

        /**
         * The first advanced look: a moon over pines behind the screen, and a separate
         * picture inside every card. The admin gives it to an address by hand, one at a time.
         */
        val NIGHTFALL = ThemePreset(
        label = "Nightfall",
        dark = true,
        primary = Color(0xFF9F7BFF),
        onPrimary = Color(0xFF14092E),
        primaryContainer = Color(0xFF2E1B57),
        onPrimaryContainer = Color(0xFFDCCFFF),
        secondary = Color(0xFFB79CFF),
        background = Color(0xFF09060F),
        surface = Color(0xFF14101F),
        surfaceVariant = Color(0xFF1E1830),
        onBackground = Color(0xFFF3EFFA),
        onSurfaceVariant = Color(0xFFA9A0C0),
        outline = Color(0xFF2B2440),
        error = Color(0xFFFF6B6B),
        special = true,
        gradient = listOf(Color(0xFF7C3AED), Color(0xFF3B1069)),
        glow = Color(0xFF8B5CF6),
        backdrop = BackdropStyle.Picture(R.drawable.adv_bg, scrim = 0.42f),
        glass = true,
        cardPictures = mapOf(
        CardPicture.HERO to R.drawable.adv_hero,
        CardPicture.PHOTOS to R.drawable.adv_photos,
        CardPicture.PDFS to R.drawable.adv_pdfs,
        CardPicture.NOTES to R.drawable.adv_notes,
        CardPicture.REELS to R.drawable.adv_reels,
        ),
        )
    }
}

/** The kinds of card that can carry their own picture. */
enum class CardPicture { HERO, PHOTOS, PDFS, NOTES, REELS }

/** What sits behind the app: a flat colour, a picture, or a picture over a gradient. */
sealed class BackdropStyle {
    data object Plain : BackdropStyle()

    /** A picture that ships inside the app. */
    class Picture(@DrawableRes val image: Int, val scrim: Float = 0.35f) : BackdropStyle()

    /** A picture downloaded from the theme catalogue and kept in the app's files. */
    class Remote(val url: String, val scrim: Float = 0.4f) : BackdropStyle()

    /** A photo this phone chose for a theme of its own. */
    class LocalFile(val file: java.io.File, val scrim: Float = 0.4f) : BackdropStyle()
}

class ThemeStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("dude_theme", Context.MODE_PRIVATE)

    /**
     * The saved look, or Light when there is none or the stored name is unknown. A look
     * from the catalogue is rebuilt from [downloaded], because it is not a build-in name.
     */
    fun load(downloaded: List<DownloadedTheme> = emptyList()): ThemePreset {
        val stored = prefs.getString("preset", "").orEmpty()
        val fromCatalogue = prefs.getString("downloaded", "").orEmpty()
        if (fromCatalogue.isNotEmpty()) {
            // Gone from the catalogue, or no longer allowed for this account: fall back
            // to Dark, but keep the id, so the look returns if it comes back.
            val remote = downloaded.firstOrNull { it.id == fromCatalogue }?.toPreset()
            return if (remote != null && AccessStore(context).isSpecial()) remote else ThemePreset.DARK
        }
        return ThemePreset.all.firstOrNull { it.label == stored } ?: ThemePreset.WHITE
    }

    fun save(preset: ThemePreset) {
        // A catalogue look is remembered by its id in the catalogue, not by its name,
        // so a theme that changes colour later still comes back.
        val id = preset.catalogId.orEmpty()
        prefs.edit()
            .putString("preset", if (id.isEmpty()) preset.label else "")
            .putString("downloaded", id)
            .apply()
    }
}

/** A look that needs access was chosen and that access is gone; go back to Dark. */
fun ThemePreset.withoutAccess(): ThemePreset =
    if (special) ThemePreset.DARK else this

/**
 * The look to show now. A picture look needs the theme level from [access], and a look the
 * admin shares one address at a time needs its id on that address's list. `adminOnly` is a
 * second lock that predates the list: Moonlit still belongs to the owner alone.
 */
fun ThemePreset.forAccess(access: MyAccess, admin: Boolean = false): ThemePreset = when {
    !special -> this
    admin || access.admin -> this
    adminOnly -> ThemePreset.DARK
    !access.theme -> ThemePreset.DARK
    !access.allowsLook(shareId()) -> ThemePreset.DARK
    else -> this
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
        CompositionLocalProvider(LocalThemePreset provides preset) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colors.background,
                contentColor = colors.onBackground,
            ) {
                content()
            }
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

/**
 * The app background. A plain colour for Light and Dark; a picture behind a dark wash for
 * the special looks, so text and cards stay readable on top of it.
 */
@Composable
fun Backdrop() {
    val preset = LocalThemePreset.current
    val downloadedThemes = LocalDownloadedThemes.current
    val context = LocalContext.current
    when (val style = preset.backdrop) {
        is BackdropStyle.Plain -> Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )

        is BackdropStyle.Remote -> {
            val theme = downloadedThemes.firstOrNull { it.pictureUrl == style.url }
            val file = theme?.let { ThemeCatalogue(context).picture(it) }
            // Only the top of the picture is washed out, so the hero card and the section
            // rows below it can be read; further down the cards are dark enough already.
            if (file != null) {
                Box(Modifier.fillMaxSize()) {
                    RemoteBackdrop(picture = file, scrimColor = Color.Transparent, scrim = 0f)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to preset.background.copy(alpha = style.scrim + 0.15f),
                                    0.32f to preset.background.copy(alpha = style.scrim * 0.6f),
                                    0.55f to preset.background.copy(alpha = 0.25f),
                                    0.75f to preset.background.copy(alpha = 0.45f),
                                    1f to preset.background.copy(alpha = 0.7f),
                                ),
                            ),
                    )
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
        }

        is BackdropStyle.LocalFile -> Box(Modifier.fillMaxSize()) {
            val picture = remember(style.file) {
                android.graphics.BitmapFactory.decodeFile(style.file.absolutePath)
                    ?.let { android.graphics.Bitmap.createScaledBitmap(it, it.width / 2, it.height / 2, true) }
                    ?.asImageBitmap()
            }
            if (picture != null) {
                Image(
                    bitmap = picture,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to preset.background.copy(alpha = style.scrim),
                            0.45f to preset.background.copy(alpha = style.scrim + 0.18f),
                            1f to preset.background,
                        ),
                    ),
            )
        }

        is BackdropStyle.Picture -> Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(style.image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to preset.background.copy(alpha = style.scrim),
                            0.45f to preset.background.copy(alpha = style.scrim + 0.18f),
                            1f to preset.background,
                        ),
                    ),
            )
        }
    }
}

/**
 * The card photos of a theme made on this phone, provided to every card the same way the
 * chosen card photos are. A theme from the catalogue uses resources instead, which need no
 * provider.
 */
val LocalThemeCards = compositionLocalOf<Map<CardPicture, java.io.File>> { emptyMap() }

/** The active look, for the few places that need more than the colours (backdrop, glow). */
val LocalThemePreset = compositionLocalOf { ThemePreset.WHITE }


/** Every look downloaded from the theme catalogue, so pictures can be found by address. */
val LocalDownloadedThemes = compositionLocalOf<List<DownloadedTheme>> { emptyList() }
