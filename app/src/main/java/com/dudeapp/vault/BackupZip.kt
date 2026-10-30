package com.dudeapp.vault

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupZipResult(
    val ok: Boolean,
    val error: String? = null,
    val profileName: String? = null,
)

/** The zip format used by Drive backup: ordinary files plus a small manifest. */
internal object BackupZip {
    private const val MANIFEST = "dude_backup.txt"

    fun write(root: File, output: File, profileName: String) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            val name = profileName.replace("\n", " ").replace("\r", " ")
            zip.write("version=1\ncreated=${stamp()}\nprofileName=$name\n".toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            root.walkTopDown().forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                if (relative.isEmpty()) return@forEach
                if (file.isDirectory) {
                    zip.putNextEntry(ZipEntry("$relative/"))
                    zip.closeEntry()
                } else if (!file.name.endsWith(".tmp")) {
                    zip.putNextEntry(ZipEntry(relative))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    fun read(input: InputStream, staging: File): BackupZipResult {
        var hasManifest = false
        var profileName: String? = null
        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = (entry.name ?: continue).replace('\\', '/')
                    if (name == MANIFEST) {
                        hasManifest = true
                        profileName = readManifest(zip)
                        zip.closeEntry()
                        continue
                    }
                    if (!allowed(name)) {
                        return BackupZipResult(false, "That file is not a P Vault backup")
                    }
                    val target = File(staging, name)
                    if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                        return BackupZipResult(false, "That backup contains an unsafe path")
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output -> zip.copyTo(output) }
                    }
                    zip.closeEntry()
                }
            }
        } catch (_: Exception) {
            return BackupZipResult(false, "That backup is not readable")
        }

        if (!hasManifest) return BackupZipResult(false, "That file is not a P Vault backup")
        listOf("vault", "reels", "chats").forEach { root ->
            val file = File(staging, root)
            if (file.exists() && !file.isDirectory) {
                return BackupZipResult(false, "That backup is damaged")
            }
        }
        val avatar = File(staging, "profile_avatar")
        if (avatar.exists() && !avatar.isFile) {
            return BackupZipResult(false, "That backup is damaged")
        }
        return BackupZipResult(true, profileName = profileName)
    }

    private fun readManifest(zip: ZipInputStream): String? {
        val buffer = ByteArray(16 * 1024)
        val read = zip.read(buffer)
        if (read <= 0) return null
        return String(buffer, 0, read, Charsets.UTF_8)
            .lineSequence()
            .firstOrNull { it.startsWith("profileName=") }
            ?.substringAfter('=')
            ?.replace("\n", " ")
            ?.replace("\r", " ")
            ?.take(40)
    }

    private fun allowed(name: String): Boolean {
        val clean = name.trimEnd('/')
        if (clean.isEmpty() || clean.startsWith("/")) return false
        val parts = clean.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return false
        return when (parts.first()) {
            "vault", "reels", "chats" -> true
            else -> clean == "profile_avatar"
        }
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
}
