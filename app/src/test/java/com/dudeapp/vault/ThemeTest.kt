package com.dudeapp.vault

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun theAppShipsColourThemesOnly() {
        // Colour-only for now: no shipped look carries a picture backdrop or card pictures.
        // Nightfall and the picture looks are deliberately out of the list; they come back
        // through the Theme Store when the owner says so.
        ThemePreset.all.forEach { preset ->
            assertTrue(preset.label, preset.backdrop is BackdropStyle.Plain)
            assertTrue(preset.label, preset.cardPictures.isEmpty())
        }
        assertEquals(9, ThemePreset.all.size)
        assertTrue(ThemePreset.all.none { it.label == "Nightfall" })
        assertTrue(ThemePreset.all.none { it.special })
    }

    @Test
    fun everyLookIsAColourTheme() {
        ThemePreset.all.forEach { preset ->
            assertEquals(preset.label, 2, preset.gradient?.size)
        }
    }

    @Test
    fun moonlitIsOnlyForTheAdmin() {
        assertTrue(ThemePreset.MOONLIT.adminOnly)
        val owner = MyAccess(level = AccessLevel.EVERYTHING)
        // Being on the list is not enough for a look marked admin-only...
        assertEquals(ThemePreset.DARK, ThemePreset.MOONLIT.forAccess(owner))
        // ...the owner's own sign-in is needed too.
        assertEquals(ThemePreset.MOONLIT, ThemePreset.MOONLIT.forAccess(owner, admin = true))
        assertEquals(ThemePreset.MOONLIT, ThemePreset.MOONLIT.forAccess(MyAccess(admin = true)))
        // An owner with no level at all still has everything.
        assertEquals(ThemePreset.MOONLIT, ThemePreset.MOONLIT.forAccess(MyAccess(), admin = true))
        // Exactly these two are admin-only, and both also need special access.
        assertEquals(
            listOf("Moonlit"),
            ThemePreset.all.filter { it.adminOnly }.map { it.label }.sorted(),
        )
        assertTrue(ThemePreset.all.filter { it.adminOnly }.all { it.special })
    }

    /** Nightfall is out of the shipped list but stays as the base an own theme is built on. */
    @Test
    fun nightfallStillKeepsBackgroundAndCardsApart() {
        val look = ThemePreset.NIGHTFALL
        assertTrue(look.backdrop is BackdropStyle.Picture)
        assertEquals(CardPicture.entries.toSet(), look.cardPictures.keys)
        assertTrue(look.cardPictures.values.all { it != 0 })
        assertFalse(look.adminOnly)
        assertEquals("nightfall", look.shareId())
        // Nothing that ships carries card pictures, so no visible look can change by accident.
        ThemePreset.all.forEach { assertTrue(it.label, it.cardPictures.isEmpty()) }
    }

    @Test
    fun anAdvancedLookIsOnlyShownWhenItIsShared() {
        val look = ThemePreset.NIGHTFALL
        val advanced = MyAccess(level = AccessLevel.ADVANCED)
        // The level alone opens the page, not the look inside it.
        assertTrue(advanced.advanced)
        assertFalse(advanced.allowsLook(look.shareId()))
        assertEquals(ThemePreset.DARK, look.forAccess(advanced))
        val shared = advanced.copy(looks = setOf("nightfall"))
        assertTrue(shared.allowsLook(look.shareId()))
        assertEquals(look, look.forAccess(shared))
        // Anybody above the theme level may be given it; nothing needs the admin.
        assertEquals(look, look.forAccess(shared.copy(level = AccessLevel.EVERYTHING)))
        assertFalse(shared.copy(level = AccessLevel.THEME).advanced)
        // Moonlit is still the owner's own look, whatever is shared.
        assertTrue(ThemePreset.MOONLIT.adminOnly)
    }

    @Test
    fun noShippedLookIsGlassy() {
        // Glass cards belong to the picture looks, and none of those ship for now.
        assertTrue(ThemePreset.all.none { it.glass })
        assertTrue(ThemePreset.all.none { it.tileTints || it.heroArt })
        // The extras still belong together on the look that has them.
        assertTrue(ThemePreset.MOONLIT.glass)
        assertTrue(ThemePreset.MOONLIT.tileTints)
        assertTrue(ThemePreset.MOONLIT.heroArt)
    }

    @Test
    fun moonlitHasItsOwnPicture() {
        val style = ThemePreset.MOONLIT.backdrop
        assertTrue(style is BackdropStyle.Picture)
    }

    @Test
    fun pictureLooksKeepTheirBackdropsForWhenTheyReturn() {
        ThemePreset.GRAND_LINE.backdrop.let { assertTrue(it is BackdropStyle.Picture) }
        ThemePreset.RACING.backdrop.let { assertTrue(it is BackdropStyle.Picture) }
        ThemePreset.SHINOBI.backdrop.let { assertTrue(it is BackdropStyle.Picture) }
        // Midnight is a colour look now: its picture was what made it special.
        assertTrue(ThemePreset.MIDNIGHT.backdrop is BackdropStyle.Plain)
        assertTrue(ThemePreset.DARK.backdrop is BackdropStyle.Plain)
    }

    @Test
    fun textStaysReadableOnEveryTheme() {
        // Dark looks need light text and the other way round; a mix means white-on-white.
        ThemePreset.all.forEach { preset ->
            val background = preset.background.luminance()
            val text = preset.onBackground.luminance()
            if (preset.dark) {
                assertTrue(preset.label, text > background * 3 + 0.2f)
            } else {
                assertTrue(preset.label, text < background)
            }
        }
    }

    private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
