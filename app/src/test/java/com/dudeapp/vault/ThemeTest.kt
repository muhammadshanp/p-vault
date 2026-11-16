package com.dudeapp.vault

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun onlyLightAndDarkAreFree() {
        assertEquals(
            listOf(ThemePreset.WHITE, ThemePreset.DARK),
            ThemePreset.all.filter { !it.special },
        )
        assertTrue(ThemePreset.all.count { it.special } >= 4)
    }

    @Test
    fun everySpecialLookHasAGradient() {
        ThemePreset.all.filter { it.special }.forEach { preset ->
            assertEquals(2, preset.gradient?.size)
        }
    }

    @Test
    fun aThemeNeedingAccessFallsBackToDark() {
        val special = ThemePreset.all.first { it.special }
        assertEquals(special, special.forAccess(true))
        assertEquals(ThemePreset.DARK, special.forAccess(false))
        assertEquals(ThemePreset.DARK, special.withoutAccess())
        assertEquals(ThemePreset.WHITE, ThemePreset.WHITE.withoutAccess())
    }

    @Test
    fun onlyTheMoonlitLookIsGlassy() {
        val glassy = ThemePreset.all.filter { it.glass }
        assertEquals(listOf(ThemePreset.MOONLIT), glassy)
        // The extras belong together: glassy cards, tinted tiles and artwork in the hero.
        assertTrue(ThemePreset.MOONLIT.tileTints)
        assertTrue(ThemePreset.MOONLIT.heroArt)
        ThemePreset.all.filter { it != ThemePreset.MOONLIT }.forEach { preset ->
            assertFalse(preset.label, preset.glass || preset.tileTints || preset.heroArt)
        }
    }

    @Test
    fun moonlitHasItsOwnPicture() {
        val style = ThemePreset.MOONLIT.backdrop
        assertTrue(style is BackdropStyle.Picture)
    }

    @Test
    fun pictureLooksHaveABackdrop() {
        ThemePreset.GRAND_LINE.backdrop.let { assertTrue(it is BackdropStyle.Picture) }
        ThemePreset.RACING.backdrop.let { assertTrue(it is BackdropStyle.Picture) }
        ThemePreset.SHINOBI.backdrop.let { assertTrue(it is BackdropStyle.Picture) }
        ThemePreset.MIDNIGHT.backdrop.let { assertTrue(it is BackdropStyle.Picture) }
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
