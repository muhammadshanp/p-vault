package com.dudeapp.vault

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun commitWritesBothDocumentsAndReadsBack() {
        val emails = listOf("a@b.co", "c@d.co")
        val writes = JSONObject(specialCommitJson(emails)).getJSONArray("writes")
        assertEquals(2, writes.length())
        val hidden = writes.getJSONObject(0).getJSONObject("update")
        val open = writes.getJSONObject(1).getJSONObject("update")
        assertEquals("projects/p-vault-app/databases/(default)/documents/access/emails", hidden.getString("name"))
        assertEquals("projects/p-vault-app/databases/(default)/documents/access/special", open.getString("name"))
        assertEquals(emails, parseStringList(hidden.toString(), "emails"))
        assertEquals(emails.map(::emailHash), parseStringList(open.toString(), "hashes"))
        // The public document never carries an address.
        assertEquals(false, open.toString().contains("@"))
    }

    @Test
    fun emptyAndBrokenListsReadAsEmpty() {
        assertEquals(emptyList<String>(), parseStringList(specialCommitJson(emptyList()), "emails"))
        assertEquals(emptyList<String>(), parseStringList("{}", "hashes"))
        assertEquals(emptyList<String>(), parseStringList("oops", "hashes"))
    }
}
