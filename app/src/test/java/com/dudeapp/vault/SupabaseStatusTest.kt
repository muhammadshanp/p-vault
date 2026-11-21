package com.dudeapp.vault

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server answers in codes; these tests pin the mapping a person actually reads. */
class SupabaseStatusTest {

    @Test
    fun readsAFreeName() {
        assertEquals(
            Supabase.NameCheck.Free,
            Supabase.parseCheck(JSONObject("""{"available":true,"reason":"ok"}""")),
        )
    }

    @Test
    fun readsATakenName() {
        assertEquals(
            Supabase.NameCheck.Taken,
            Supabase.parseCheck(JSONObject("""{"available":false,"reason":"taken"}""")),
        )
    }

    @Test
    fun readsARejectedName() {
        assertEquals(
            Supabase.NameCheck.Rejected("reserved"),
            Supabase.parseCheck(JSONObject("""{"available":false,"reason":"reserved"}""")),
        )
    }

    @Test
    fun successStatusesHaveNothingToSay() {
        assertEquals("", Supabase.claimProblem("ok"))
        assertEquals("", Supabase.claimProblem("already_yours"))
    }

    @Test
    fun everyFailureExplainsItself() {
        val statuses = listOf(
            "taken", "reserved", "short", "long", "chars", "empty", "bad_device", "whatever",
        )
        for (status in statuses) {
            val text = Supabase.claimProblem(status)
            assertTrue("$status should explain itself", text.length > 12)
        }
        // Offline is the one case with no status at all.
        assertTrue(Supabase.claimProblem(null).length > 12)
    }

    @Test
    fun thePassphraseTravelsAsAHash() {
        // The phrase itself is never stored in the app or sent; the database compares this.
        assertEquals(
            "5e26e54ec852b6f14d0d453082e8ed00bc07d5fa1adcedcab23a0554ab7482c0",
            Supabase.keyHash("prere-owner-willow-3db4"),
        )
        // Stray spaces or capitals still open the list.
        assertEquals(
            Supabase.keyHash("prere-owner-willow-3db4"),
            Supabase.keyHash("  PRERE-OWNER-WILLOW-3DB4  "),
        )
        assertEquals(64, Supabase.keyHash("anything").length)
    }

    @Test
    fun featureKeysRoundTrip() {
        assertEquals(AccessFeature.BACKUP, AccessFeature.of("backup"))
        assertEquals(AccessFeature.CHATS, AccessFeature.of("CHATS"))
        assertEquals(null, AccessFeature.of("nonsense"))
    }

    @Test
    fun levelsComeBackFromTheServerAsTheAppKnowsThem() {
        assertEquals(AccessLevel.ADVANCED, AccessLevel.of("ADVANCED"))
        assertEquals(AccessLevel.EVERYTHING, AccessLevel.of("everything"))
        assertEquals(AccessLevel.NONE, AccessLevel.of("whatever"))
        assertEquals(AccessLevel.NONE, AccessLevel.of(null))
        val access = MyAccess(level = AccessLevel.THEME, features = setOf(AccessFeature.BACKUP))
        assertTrue(access.theme)
        assertFalse(access.advanced)
        assertTrue(access.has(AccessFeature.BACKUP))
        assertFalse(access.has(AccessFeature.CHATS))
    }

    @Test
    fun takenIsNotReportedAsSuccess() {
        assertFalse(Supabase.claimProblem("taken").isEmpty())
    }
}
