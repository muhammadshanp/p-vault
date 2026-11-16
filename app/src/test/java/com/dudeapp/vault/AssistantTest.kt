package com.dudeapp.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The assistant reads titles and file names, with no connection and nothing uploaded. These
 * tests cover the matching, which is the part that can be wrong without anyone noticing.
 */
class AssistantTest {
    @Test
    fun everySwitchHasAStableKey() {
        // The keys travel to Firestore, so they must not change by accident.
        assertEquals("suggestions", AccessFeature.SUGGESTIONS.key)
        assertEquals("assistant", AccessFeature.ASSISTANT.key)
        assertEquals(AccessFeature.ASSISTANT, AccessFeature.of("assistant"))
        assertEquals(AccessFeature.ASSISTANT, AccessFeature.of("ASSISTANT"))
    }

    @Test
    fun theAssistantIsGivenOneAddressAtATime() {
        val nobody = MyAccess(level = AccessLevel.EVERYTHING)
        assertTrue(nobody.theme)
        assertTrue(nobody.advanced)
        // Everything but the panel does not include a switch nobody turned on.
        assertTrue(nobody.admin.not())
        assertEquals(false, nobody.has(AccessFeature.ASSISTANT))
        assertEquals(true, nobody.copy(features = setOf(AccessFeature.ASSISTANT)).has(AccessFeature.ASSISTANT))
        // The admin keeps it whatever the list says.
        assertEquals(true, MyAccess(admin = true).has(AccessFeature.ASSISTANT))
    }

    @Test
    fun bundlesCanShareOneTheme() {
        // A theme belongs to bundles only by id, so the same theme sits in two of them.
        val theme = OwnTheme(id = "t1", name = "Night")
        val one = ThemeBundle(id = "b1", name = "Night pack", themeIds = listOf(theme.id))
        val two = ThemeBundle(id = "b2", name = "Favourites", themeIds = listOf(theme.id))
        assertEquals(1, one.themeIds.count { it == theme.id })
        assertEquals(1, two.themeIds.count { it == theme.id })
        // Deleting a bundle leaves the theme's id in the other one.
        val afterRemoval = listOf(one, two).filterNot { it.id == "b1" }
        assertEquals(listOf("t1"), afterRemoval.single().themeIds)
    }
}
