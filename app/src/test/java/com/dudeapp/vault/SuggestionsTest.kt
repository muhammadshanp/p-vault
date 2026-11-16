package com.dudeapp.vault

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private val sample = Suggestion("abc", "a@b.co", "Shan", "Add videos", 1_000L, "video", "video/mp4", 3, 1_500_000L)

    @Test
    fun partsCoverTheWholeFile() {
        assertEquals(0, partCount(0))
        assertEquals(1, partCount(1))
        assertEquals(1, partCount(PART_BYTES.toLong()))
        assertEquals(2, partCount(PART_BYTES + 1L))
        assertEquals(18, partCount(MAX_ATTACHMENT_BYTES))
    }

    @Test
    fun expiresAfterTwoDays() {
        assertFalse(isExpired(sample, sample.sent + SUGGESTION_LIFE_MS - 1))
        assertTrue(isExpired(sample, sample.sent + SUGGESTION_LIFE_MS))
    }

    @Test
    fun savedSuggestionReadsBack() {
        val document = JSONObject(suggestionJson(sample)).put("name", "projects/p/databases/(default)/documents/suggestions/abc")
        val list = JSONObject().put("documents", JSONArray().put(document)).toString()
        assertEquals(listOf(sample), parseSuggestions(list))
        assertEquals(
            "1970-01-03T00:00:01Z",
            document.getJSONObject("fields").getJSONObject("expires").getString("timestampValue"),
        )
    }

    @Test
    fun emptyAndBrokenListsReadAsEmpty() {
        assertEquals(emptyList<Suggestion>(), parseSuggestions("{}"))
        assertEquals(emptyList<Suggestion>(), parseSuggestions("oops"))
    }

    @Test
    fun partReadsBack() {
        val bytes = byteArrayOf(0, 1, 2, -1, 127, -128)
        assertArrayEquals(bytes, parsePart(partJson(bytes, 0L)))
        assertNull(parsePart("{}"))
    }

    @Test
    fun deleteRemovesEveryPartAndTheSuggestion() {
        val writes = JSONObject(deleteSuggestionJson(sample)).getJSONArray("writes")
        assertEquals(4, writes.length())
        val root = "projects/p-vault-app/databases/(default)/documents/suggestions/abc"
        assertEquals("$root/parts/0", writes.getJSONObject(0).getString("delete"))
        assertEquals("$root/parts/2", writes.getJSONObject(2).getString("delete"))
        assertEquals(root, writes.getJSONObject(3).getString("delete"))
    }
}
