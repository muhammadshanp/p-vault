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
            ThemePreset.entries.filter { !it.special },
        )
        assertTrue(ThemePreset.entries.count { it.special } >= 4)
    }

    @Test
    fun everySpecialLookHasAGradient() {
        ThemePreset.entries.filter { it.special }.forEach { preset ->
            assertEquals(2, preset.gradient?.size)
        }
    }

    @Test
    fun aThemeNeedingAccessFallsBackToDark() {
        val special = ThemePreset.entries.first { it.special }
        assertEquals(special, special.forAccess(true))
        assertEquals(ThemePreset.DARK, special.forAccess(false))
        assertEquals(ThemePreset.DARK, special.withoutAccess())
        assertEquals(ThemePreset.WHITE, ThemePreset.WHITE.withoutAccess())
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
        ThemePreset.entries.forEach { preset ->
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
