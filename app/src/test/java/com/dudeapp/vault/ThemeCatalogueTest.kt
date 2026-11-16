package com.dudeapp.vault

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCatalogueTest {
    private val catalogue = """
        {
          "themes": [
            {
              "id": "wanted",
              "label": "Wanted",
              "dark": true,
              "primary": "#E2B04A",
              "background": "#1B1408",
              "onBackground": "#F7EEDC",
              "picture": "https://example.com/wanted.webp",
              "scrim": 0.5
            },
            {
              "id": "sunny",
              "label": "Sunny Sea",
              "dark": false,
              "primary": "#1E88E5",
              "background": "#EAF6FF",
              "allowed": ["Shan@Example.com"]
            },
            { "id": "", "label": "Broken" }
          ]
        }
    """.trimIndent()

    @Test
    fun readsTheLooksAndSkipsBrokenOnes() {
        val themes = parseDownloadedThemes(catalogue, "0.5.17")
        assertEquals(listOf("wanted", "sunny"), themes.map { it.id })
        val wanted = themes.first()
        assertEquals(Color(0xFFE2B04A), wanted.primary)
        assertEquals(Color(0xFF1B1408), wanted.background)
        assertEquals("https://example.com/wanted.webp", wanted.pictureUrl)
        assertEquals(0.5f, wanted.scrim, 0.001f)
        // The second look has no picture, so it is a plain colour look.
        assertNull(themes[1].pictureUrl)
    }

    @Test
    fun aCatalogueForANewerAppIsIgnored() {
        val newer = """{"minVersion":"0.6.0","themes":[{"id":"x","label":"X"}]}"""
        assertEquals(emptyList<DownloadedTheme>(), parseDownloadedThemes(newer, "0.5.17"))
        assertEquals(1, parseDownloadedThemes(newer, "0.6.0").size)
    }

    @Test
    fun brokenTextIsNotACatalogue() {
        assertEquals(emptyList<DownloadedTheme>(), parseDownloadedThemes("nope", "0.5.17"))
        assertEquals(emptyList<DownloadedTheme>(), parseDownloadedThemes("{}", "0.5.17"))
    }

    @Test
    fun coloursFallBackWhenWrong() {
        assertEquals(Color(0xFF101014), parseColor("purple"))
        assertEquals(Color(0xFF123456), parseColor("#123456"))
        assertEquals(Color(0x80123456), parseColor("80123456"))
    }

    @Test
    fun aLookWithAListIsOnlyForThoseAddresses() {
        val themes = parseDownloadedThemes(catalogue, "0.5.17")
        val sunny = themes[1]
        assertTrue(sunny.isAllowedFor("shan@example.com", true))
        assertFalse(sunny.isAllowedFor("other@example.com", true))
        assertFalse(sunny.isAllowedFor(null, true))
        // Without special access nobody gets a catalogue look.
        assertFalse(sunny.isAllowedFor("shan@example.com", false))
        // No list means everyone with special access.
        assertTrue(themes[0].isAllowedFor(null, true))
    }

    @Test
    fun aDownloadedLookBecomesAPreset() {
        val wanted = parseDownloadedThemes(catalogue, "0.5.17").first().toPreset()
        assertEquals("Wanted", wanted.label)
        assertTrue(wanted.special)
        assertEquals(2, wanted.gradient?.size)
        assertTrue(wanted.backdrop is BackdropStyle.Remote)
    }
}
