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
                // Only the vault itself goes in. Android libraries also keep small files
                // in this folder, and a backup containing those could not be restored.
                if (!isVaultPath(relative)) return@forEach
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
                    val cleanName = (entry.name ?: continue).replace('\\', '/')
                    val strippedName = cleanName.trimStart('/')
                    if (strippedName.equals(MANIFEST, ignoreCase = true)) {
                        hasManifest = true
                        profileName = readManifest(zip)
                        zip.closeEntry()
                        continue
                    }
                    if (!isSafePath(strippedName)) {
                        return BackupZipResult(false, "That backup contains an unsafe path")
                    }
                    if (!isVaultPath(strippedName)) {
                        // Not part of the vault (older versions could include stray app
                        // files). Skip it instead of refusing the whole backup.
                        zip.closeEntry()
                        continue
                    }
                    val target = File(staging, strippedName)
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

    /** No empty, "." or ".." segments, so the entry cannot escape the restore folder. */
    private fun isSafePath(name: String): Boolean {
        val clean = name.trimEnd('/')
        if (clean.isEmpty() || clean.startsWith("/")) return false
        return clean.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    /** True for the folders and the one file that make up a vault. */
    private fun isVaultPath(name: String): Boolean {
        val clean = name.trimEnd('/')
        return when (clean.substringBefore('/')) {
            "vault", "reels", "chats" -> true
            else -> clean == "profile_avatar"
        }
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
}
