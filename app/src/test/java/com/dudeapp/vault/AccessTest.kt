package com.dudeapp.vault

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessTest {
    @Test
    fun cleansAddresses() {
        assertEquals("shan@gmail.com", cleanEmail("  Shan@Gmail.com "))
        assertNull(cleanEmail("shan"))
        assertNull(cleanEmail("shan@gmail"))
        assertNull(cleanEmail("a b@gmail.com"))
    }

    @Test
    fun hashIgnoresCaseAndSpaces() {
        assertEquals(emailHash("shan@gmail.com"), emailHash(" Shan@Gmail.com "))
        // SHA-256 of "a@b.co"
        assertEquals(64, emailHash("a@b.co").length)
    }

    @Test
    fun commitWritesTheLevelsOfEveryAddress() {
        val people = listOf(
            AccessPerson("a@b.co", AccessLevel.THEME),
            AccessPerson("c@d.co", AccessLevel.ADVANCED, looks = setOf("nightfall")),
        )
        val writes = JSONObject(accessCommitJson(people)).getJSONArray("writes")
        assertEquals(2, writes.length())
        val hidden = writes.getJSONObject(0).getJSONObject("update")
        val open = writes.getJSONObject(1).getJSONObject("update")
        assertEquals("projects/p-vault-app/databases/(default)/documents/access/emails", hidden.getString("name"))
        assertEquals("projects/p-vault-app/databases/(default)/documents/access/special", open.getString("name"))
        assertEquals(listOf("a@b.co", "c@d.co"), parseStringList(hidden.toString(), "emails"))
        // The public document holds fingerprints, and never an address.
        assertEquals(false, open.toString().contains("@"))
        val document = AccessDocument.parse(open.toString())
        assertEquals(AccessLevel.THEME, document.level(emailHash("a@b.co")))
        assertEquals(AccessLevel.ADVANCED, document.level(emailHash("c@d.co")))
        assertEquals(AccessLevel.NONE, document.level(emailHash("e@f.co")))
        // No switches are given here; the list of switches is empty for now.
        assertEquals(emptySet<AccessFeature>(), document.featuresOf(emailHash("c@d.co")))
        assertEquals(setOf("nightfall"), document.looksOf(emailHash("c@d.co"), AccessLevel.ADVANCED))
        // The theme level alone does not reach the advanced page.
        assertTrue(document.level(emailHash("a@b.co")).allows(AccessLevel.THEME))
        assertFalse(document.level(emailHash("a@b.co")).allows(AccessLevel.ADVANCED))
    }

    @Test
    fun takingSomethingAwayRemovesItFromTheDocument() {
        val given = AccessDocument().with(
            emailHash("a@b.co"),
            AccessLevel.ADVANCED,
            looks = setOf("nightfall"),
        )
        assertTrue(emailHash("a@b.co") in given.advanced)
        val taken = given.with(emailHash("a@b.co"), AccessLevel.THEME)
        val hash = emailHash("a@b.co")
        assertFalse(hash in taken.advanced)
        assertTrue(hash in taken.theme)
        assertTrue(hash in taken.hashes)
        assertTrue(taken.features.values.none { hash in it })
        assertTrue(taken.looks.values.none { hash in it })
        // And nothing at all leaves the list.
        val gone = taken.with(hash, AccessLevel.NONE)
        assertFalse(hash in gone.hashes)
        assertEquals(AccessLevel.NONE, gone.level(hash))
    }

    @Test
    fun emptyAndBrokenListsReadAsEmpty() {
        assertEquals(emptyList<String>(), parseStringList(accessCommitJson(emptyList()), "emails"))
        assertEquals(emptyList<String>(), parseStringList("{}", "hashes"))
        assertEquals(emptyList<String>(), parseStringList("oops", "hashes"))
        assertEquals(AccessLevel.NONE, AccessDocument.parse("oops").level(emailHash("a@b.co")))
    }

    @Test
    fun theAdminKeepsEverything() {
        val admin = MyAccess(level = AccessLevel.NONE, admin = true)
        assertTrue(admin.theme)
        assertTrue(admin.advanced)
        assertTrue(admin.allowsLook("anything"))
        // With no switches in the model there is nothing to grant, and nothing to check.
        AccessFeature.entries.firstOrNull()?.let { assertTrue(admin.has(it)) }
        val plain = MyAccess(level = AccessLevel.THEME)
        assertTrue(plain.theme)
        assertFalse(plain.advanced)
        assertFalse(plain.allowsLook("nightfall"))
        assertTrue(plain.allowsLook(null))
        assertFalse(plain.has(SUPPORT_AI))
    }
}
