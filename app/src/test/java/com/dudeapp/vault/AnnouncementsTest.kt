package com.dudeapp.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementsTest {
    @Test
    fun onlyOpenIssuesFromTheOwnerAreShownNewestFirst() {
        val json = """[
            {"number":4,"title":"Old news","body":"first","state":"open","created_at":"2026-11-10T08:00:00Z","user":{"login":"muhammadshanp"}},
            {"number":9,"title":"Spam","body":"buy","state":"open","created_at":"2026-11-12T08:00:00Z","user":{"login":"stranger"}},
            {"number":7,"title":"Hello","body":null,"state":"open","created_at":"2026-11-11T08:00:00Z","user":{"login":"MuhammadShanP"}},
            {"number":8,"title":"A pull request","state":"open","pull_request":{},"user":{"login":"muhammadshanp"}},
            {"number":6,"title":"Taken down","state":"closed","user":{"login":"muhammadshanp"}}
        ]"""
        val items = parseAnnouncements(json, "muhammadshanp")
        assertEquals(listOf(7, 4), items.map { it.number })
        assertEquals("", items[0].body)
        assertEquals("first", items[1].body)
        assertTrue(items[0].time > items[1].time)
    }

    @Test
    fun unreadableResponsesGiveAnEmptyList() {
        assertEquals(emptyList<Announcement>(), parseAnnouncements("not json"))
        assertEquals(emptyList<Announcement>(), parseAnnouncements("{}"))
    }
}
