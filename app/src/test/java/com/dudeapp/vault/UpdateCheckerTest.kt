package com.dudeapp.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun passesInstalledVersionToTheUpdatePage() {
        assertEquals(
            "https://muhammadshanp.github.io/p-vault-website/update.html?v=0.4.2",
            updatePageUrl("0.4.2"),
        )
    }

    @Test
    fun comparesVersionParts() {
        assertTrue(isNewerVersion("1.9", "1.8"))
        assertTrue(isNewerVersion("1.10", "1.9"))
        assertTrue(isNewerVersion("2.0.1", "1.99.9"))
        assertTrue(isNewerVersion("0.3.1", "0.3"))
        assertFalse(isNewerVersion("0.3", "0.3.1"))
        assertFalse(isNewerVersion("1.8", "1.8"))
        assertFalse(isNewerVersion("1.8", "1.9"))
    }
}
