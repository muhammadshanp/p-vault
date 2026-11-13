package com.dudeapp.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BackupMergeTest {
    private fun withDirs(block: (backup: File, phone: File) -> Unit) {
        val base = Files.createTempDirectory("dude-merge").toFile()
        try {
            block(File(base, "backup").apply { mkdirs() }, File(base, "phone").apply { mkdirs() })
        } finally {
            base.deleteRecursively()
        }
    }

    private fun File.put(path: String, text: String) {
        File(this, path).apply { parentFile?.mkdirs() }.writeText(text)
    }

    @Test
    fun filesAlreadyOnThePhoneStay() = withDirs { backup, phone ->
        phone.put("vault/200_New.txt", "only on the phone")
        backup.put("vault/100_Old.txt", "only in the backup")

        assertEquals(1, BackupMerge.merge(backup, phone))
        assertEquals("only on the phone", File(phone, "vault/200_New.txt").readText())
        assertEquals("only in the backup", File(phone, "vault/100_Old.txt").readText())
    }

    @Test
    fun sameNameWithDifferentContentKeepsBoth() = withDirs { backup, phone ->
        phone.put("vault/100_Note.txt", "edited on the phone")
        backup.put("vault/100_Note.txt", "older text")

        assertEquals(1, BackupMerge.merge(backup, phone))
        assertEquals("edited on the phone", File(phone, "vault/100_Note.txt").readText())
        assertEquals("older text", File(phone, "vault/101_Note.txt").readText())
    }

    @Test
    fun restoringTheSameBackupTwiceAddsNothing() = withDirs { backup, phone ->
        phone.put("vault/100_Note.txt", "edited on the phone")
        backup.put("vault/100_Note.txt", "older text")
        backup.put("vault/50_Same.txt", "same")
        phone.put("vault/50_Same.txt", "same")
        backup.put("reels/01_Tech/7_Clip.reel", "https://example.com/r/1")
        backup.put("chats/01_Home/5.txt", "hi")

        assertEquals(3, BackupMerge.merge(backup, phone))
        assertEquals(0, BackupMerge.merge(backup, phone))
        assertEquals(3, File(phone, "vault").listFiles()!!.size)
    }

    @Test
    fun categoriesAndChatsAreMatchedByName() = withDirs { backup, phone ->
        phone.put("reels/00_Pending/1_A.reel", "https://example.com/a")
        phone.put("reels/03_Tech/2_B.reel", "https://example.com/b")
        backup.put("reels/01_tech/3_C.reel", "https://example.com/c")
        backup.put("reels/02_Music/4_D.reel", "https://example.com/d")
        // Moved to another category on the phone since the backup: not added again.
        backup.put("reels/02_Music/1_A.reel", "https://example.com/a")
        phone.put("chats/01_Home/10.txt", "phone version")
        backup.put("chats/04_Home/10.txt", "backup version")
        backup.put("chats/04_Home/11.txt", "\u0001photo:11_photo.jpg")
        backup.put("chats/04_Home/11_photo.jpg", "jpg")

        assertEquals(4, BackupMerge.merge(backup, phone))
        assertTrue(File(phone, "reels/03_Tech/3_C.reel").isFile)
        assertTrue(File(phone, "reels/04_Music/4_D.reel").isFile)
        assertFalse(File(phone, "reels/04_Music/1_A.reel").exists())
        assertFalse(File(phone, "reels/01_tech").exists())
        assertEquals("phone version", File(phone, "chats/01_Home/10.txt").readText())
        assertTrue(File(phone, "chats/01_Home/11_photo.jpg").isFile)
        assertFalse(File(phone, "chats/04_Home").exists())
    }

    @Test
    fun profilePictureOnThePhoneIsKept() = withDirs { backup, phone ->
        backup.put("profile_avatar", "from backup")
        BackupMerge.merge(backup, phone)
        assertEquals("from backup", File(phone, "profile_avatar").readText())

        backup.put("profile_avatar", "another")
        BackupMerge.merge(backup, phone)
        assertEquals("from backup", File(phone, "profile_avatar").readText())
    }
}
