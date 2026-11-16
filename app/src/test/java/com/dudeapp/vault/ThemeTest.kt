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
        // A picture look that is open to everyone with the theme level (not one shared by hand).
        val special = ThemePreset.all.first { it.special && !it.adminOnly && it.cardPictures.isEmpty() }
        val member = MyAccess(level = AccessLevel.THEME, looks = setOf(special.shareId()))
        assertEquals(special, special.forAccess(member))
        assertEquals(ThemePreset.DARK, special.forAccess(MyAccess()))
        assertEquals(ThemePreset.DARK, special.forAccess(member.copy(level = AccessLevel.NONE)))
        assertEquals(ThemePreset.DARK, special.withoutAccess())
        assertEquals(ThemePreset.WHITE, ThemePreset.WHITE.withoutAccess())
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

    @Test
    fun nightfallKeepsBackgroundAndCardsApart() {
        val look = ThemePreset.NIGHTFALL
        // A background behind the screen...
        assertTrue(look.backdrop is BackdropStyle.Picture)
        // ...and a different picture inside every card.
        assertEquals(
            CardPicture.entries.toSet(),
            look.cardPictures.keys,
        )
        assertTrue(look.cardPictures.values.all { it != 0 })
        // Shared by hand, look by look, not admin-only any more.
        assertFalse(look.adminOnly)
        assertEquals("nightfall", look.shareId())
        // No other look has card pictures, so nothing else can change by accident.
        ThemePreset.all.filter { it != look }.forEach { assertTrue(it.label, it.cardPictures.isEmpty()) }
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
    fun onlyTheMoonlitLookIsGlassy() {
        val glassy = ThemePreset.all.filter { it.glass }.map { it.label }.sorted()
        assertEquals(listOf("Moonlit", "Nightfall"), glassy)
        // The extras belong together: glassy cards, tinted tiles and artwork in the hero.
        assertTrue(ThemePreset.MOONLIT.tileTints)
        assertTrue(ThemePreset.MOONLIT.heroArt)
        ThemePreset.all.filter { !it.glass }.forEach { preset ->
            assertFalse(preset.label, preset.tileTints || preset.heroArt)
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
