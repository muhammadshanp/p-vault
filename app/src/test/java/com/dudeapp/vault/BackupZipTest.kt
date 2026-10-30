package com.dudeapp.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupZipTest {
    @Test
    fun roundTripKeepsVaultAndProfileName() {
        val base = Files.createTempDirectory("dude-backup").toFile()
        try {
            val root = File(base, "files").apply { mkdirs() }
            File(root, "vault").mkdirs()
            File(root, "vault/1_Hello.txt").writeText("hello")
            File(root, "vault/1_Ignore.tmp").writeText("tmp")
            File(root, "reels/00_Pending").mkdirs()
            File(root, "chats/01_Anime").mkdirs()
            File(root, "chats/01_Anime/1_Hi.txt").writeText("hi")
            File(root, "profile_avatar").writeBytes(byteArrayOf(1, 2, 3))

            val zip = File(base, "backup.zip")
            BackupZip.write(root, zip, "Muhammad")

            val staging = File(base, "staging").apply { mkdirs() }
            val result = zip.inputStream().use { BackupZip.read(it, staging) }

            assertTrue(result.error, result.ok)
            assertEquals("Muhammad", result.profileName)
            assertEquals("hello", File(staging, "vault/1_Hello.txt").readText())
            assertEquals("hi", File(staging, "chats/01_Anime/1_Hi.txt").readText())
            assertFalse(File(staging, "vault/1_Ignore.tmp").exists())
            assertTrue(File(staging, "reels/00_Pending").isDirectory)
            assertTrue(File(staging, "profile_avatar").isFile)
            assertFalse(File(staging, "dude_backup.txt").exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun rejectsTraversalEntry() {
        val base = Files.createTempDirectory("dude-backup-traversal").toFile()
        try {
            val zip = File(base, "bad.zip")
            ZipOutputStream(FileOutputStream(zip)).use { output ->
                output.putNextEntry(ZipEntry("dude_backup.txt"))
                output.write("version=1\nprofileName=X\n".toByteArray())
                output.closeEntry()
                output.putNextEntry(ZipEntry("vault/../../escape.txt"))
                output.write("bad".toByteArray())
                output.closeEntry()
            }

            val staging = File(base, "staging").apply { mkdirs() }
            val result = zip.inputStream().use { BackupZip.read(it, staging) }

            assertFalse(result.ok)
            assertFalse(File(base, "escape.txt").exists())
        } finally {
            base.deleteRecursively()
        }
    }
}
