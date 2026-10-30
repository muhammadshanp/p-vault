package com.dudeapp.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderTest {
    private val week = 7L * 24 * 60 * 60 * 1000
    private val now = 1_000_000_000L

    @Test
    fun firstBackupIsDue() {
        assertTrue(backupReminderDue(0, 0, now))
    }

    @Test
    fun recentBackupIsNotDue() {
        assertFalse(backupReminderDue(now - week + 1, 0, now))
    }

    @Test
    fun weekOldBackupIsDue() {
        assertTrue(backupReminderDue(now - week, 0, now))
    }

    @Test
    fun recentSnoozeIsNotDue() {
        assertFalse(backupReminderDue(now - week * 2, now - week + 1, now))
    }

    @Test
    fun snoozeExpiresAfterAWeek() {
        assertTrue(backupReminderDue(now - week * 2, now - week, now))
    }

    @Test
    fun manualBackupClearsReminder() {
        assertFalse(backupReminderDue(now - 1, now - week, now))
    }

    @Test
    fun customReminderIntervalIsUsed() {
        val day = 24L * 60 * 60 * 1000
        assertFalse(backupReminderDue(now - 2 * day, 0, now, 3 * day))
        assertTrue(backupReminderDue(now - 3 * day, 0, now, 3 * day))
    }

    @Test
    fun keepsNewestTwoVaultBackups() {
        val names = listOf(
            "P_Vault_20261029_100000_000.zip",
            "P_Vault_20261029_110000_000.zip",
            "P_Vault_20261029_120000_000.zip",
            "Dude_20260101_120000_000.zip",
            "notes.txt",
        )
        assertEquals(
            setOf("P_Vault_20261029_100000_000.zip"),
            backupNamesToDelete(names),
        )
    }

    @Test
    fun keepsConfiguredBackupCount() {
        val names = listOf(
            "P_Vault_20261029_100000_000.zip",
            "P_Vault_20261029_110000_000.zip",
            "P_Vault_20261029_120000_000.zip",
        )
        assertEquals(
            setOf(
                "P_Vault_20261029_100000_000.zip",
                "P_Vault_20261029_110000_000.zip",
            ),
            backupNamesToDelete(names, keep = 1),
        )
    }
}
