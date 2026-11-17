package com.dudeapp.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportTest {
    @Test
    fun everySwitchHasAStableKey() {
        // The keys travel to Firestore, so they must not change by accident.
        assertEquals("suggestions", AccessFeature.SUGGESTIONS.key)
        assertEquals("support", AccessFeature.SUPPORT.key)
        assertEquals(AccessFeature.SUPPORT, AccessFeature.of("support"))
        assertEquals(AccessFeature.SUPPORT, AccessFeature.of("SUPPORT"))
    }

    @Test
    fun supportIsGivenOneAddressAtATime() {
        val someone = MyAccess(level = AccessLevel.EVERYTHING)
        assertTrue(someone.theme)
        assertTrue(someone.advanced)
        // The top level is not the admin panel, and does not include a switch nobody gave.
        assertFalse(someone.has(AccessFeature.SUPPORT))
        assertTrue(someone.copy(features = setOf(AccessFeature.SUPPORT)).has(AccessFeature.SUPPORT))
        // The admin keeps it whatever the list says.
        assertTrue(MyAccess(admin = true).has(AccessFeature.SUPPORT))
    }

    @Test
    fun aThemeCanSitInTwoBundles() {
        val theme = OwnTheme(id = "t1", name = "Night")
        val one = ThemeBundle(id = "b1", name = "Night pack", themeIds = listOf(theme.id))
        val two = ThemeBundle(id = "b2", name = "Favourites", themeIds = listOf(theme.id))
        assertEquals(1, one.themeIds.count { it == theme.id })
        assertEquals(1, two.themeIds.count { it == theme.id })
        // Deleting a bundle leaves the theme in the other one.
        val afterRemoval = listOf(one, two).filterNot { it.id == "b1" }
        assertEquals(listOf("t1"), afterRemoval.single().themeIds)
    }

    @Test
    fun advancedModeOffLeavesOnlyTheOrdinaryLooks() {
        // What the Theme page is given when advanced mode is switched off.
        val full = MyAccess(level = AccessLevel.ADVANCED, looks = setOf("nightfall"))
        assertTrue(full.advanced)
        val plain = full.copy(level = AccessLevel.THEME)
        assertTrue(plain.theme)
        assertFalse(plain.advanced)
        val advancedLook = ThemePreset.NIGHTFALL
        // Without the advanced level the shared look is out of reach, photos or not.
        assertEquals(ThemePreset.DARK, advancedLook.forAccess(plain.copy(looks = setOf("nightfall"))))
    }
}
