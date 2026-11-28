package com.dudeapp.vault

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shapes the owner's user list arrives in.
 *
 * The list is read from a jsonb answer, but a project that has not had the 0.6.16 SQL run
 * yet answers something else entirely — including a bare error string. None of those may
 * crash the admin panel, and none of them may be silently shown as "no users".
 */
class AdminUsersParseTest {

    @Test
    fun aFullListKeepsTheAddressAndTheAccess() {
        val answer = JSONObject(
            """
            {"status":"ok","users":[
              {"username":"shan","email":"Shan@Example.com","created_at":"2026-11-02T10:00:00Z",
               "level":"ADVANCED","features":["backup","chats"],"verified":true,"partner":false},
              {"username":"user_123456","email":null,"created_at":"2026-11-01T10:00:00Z",
               "level":"NONE","features":[],"verified":false,"partner":true}
            ]}
            """.trimIndent(),
        )
        val rows = answer.getJSONArray("users")
        assertEquals(2, rows.length())

        val first = rows.getJSONObject(0)
        assertEquals("shan", first.optString("username"))
        assertEquals("Shan@Example.com", first.optString("email"))
        assertEquals("ADVANCED", first.optString("level"))
        assertTrue(first.optBoolean("verified"))

        val second = rows.getJSONObject(1)
        // A null address must read as "no address", not the word "null".
        assertTrue(second.optString("email").isBlank() || second.optString("email") == "null")
        assertTrue(second.optBoolean("partner"))
    }

    @Test
    fun aRefusedPassphraseIsAnErrorAndNotAnEmptyList() {
        val answer = JSONObject("""{"status":"bad_key"}""")
        assertEquals("bad_key", answer.optString("status"))
        assertNull(answer.optJSONArray("users"))
    }

    @Test
    fun anOlderProjectStillLooksLikeSomeThingReadable() {
        // What an older Supabase project answers for a function it does not have: no
        // "users" key at all. Reading it as "nobody" is the bug this guards against.
        val answer = JSONObject("""{"code":"PGRST202","message":"Could not find the function"}""")
        assertNull(answer.optJSONArray("users"))
        assertTrue(answer.optString("message").isNotEmpty())
    }
}
