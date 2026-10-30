package com.dudeapp.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun comparesVersionParts() {
        assertTrue(isNewerVersion("1.9", "1.8"))
        assertTrue(isNewerVersion("1.10", "1.9"))
        assertTrue(isNewerVersion("2.0.1", "1.99.9"))
        assertFalse(isNewerVersion("1.8", "1.8"))
        assertFalse(isNewerVersion("1.8", "1.9"))
    }
}
