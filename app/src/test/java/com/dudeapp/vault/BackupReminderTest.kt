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

    @Test
    fun sameNamedBackupsAreNotAllDeleted() {
        // Drive allows several files with one name. Only the surplus copies may go.
        val files = listOf(
            BackupFile("a", "P_Vault.zip", 100),
            BackupFile("b", "P_Vault.zip", 200),
            BackupFile("c", "P_Vault.zip", 300),
        )
        assertEquals(listOf("a"), backupsToDelete(files, keep = 2).map { it.id })
    }

    @Test
    fun timestampedBackupsOutrankLegacyNames() {
        val files = listOf(
            BackupFile("old1", "P_Vault.zip", 900),
            BackupFile("new1", "P_Vault_20261113_100000_000.zip", 100),
            BackupFile("new2", "P_Vault_20261113_110000_000.zip", 200),
            BackupFile("other", "holiday.zip", 999),
        )
        assertEquals(listOf("old1"), backupsToDelete(files, keep = 2).map { it.id })
    }

    @Test
    fun theBackupJustWrittenIsNeverDeleted() {
        val files = listOf(
            BackupFile("a", "P_Vault_20261113_120000_000.zip", 0),
            BackupFile("just", "P_Vault_20261113_100000_000.zip", 0),
        )
        assertEquals(emptyList<String>(), backupsToDelete(files, keep = 1, protectId = "just").map { it.id })
    }

    @Test
    fun connectDriveSuggestionComesBackWeekly() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(connectPromptDue(lastAsked = 0L, now = 50 * day))
        assertFalse(connectPromptDue(lastAsked = 50 * day, now = 53 * day))
        assertTrue(connectPromptDue(lastAsked = 50 * day, now = 57 * day))
    }
}
