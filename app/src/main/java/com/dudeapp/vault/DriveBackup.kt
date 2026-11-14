package com.dudeapp.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DriveBackupState(
    val connected: Boolean,
    val account: String,
    val busy: Boolean = false,
    val message: String? = null,
)

data class BackupResult(
    val ok: Boolean,
    val message: String,
    /** The backup is password-protected and a (different) password must be typed. */
    val passwordNeeded: Boolean = false,
)

private const val REMINDER_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
private const val KEEP_BACKUPS = 2

/** True when the backup is old and no reminder was shown during the same interval. */
internal fun backupReminderDue(
    lastBackup: Long,
    lastReminder: Long,
    now: Long,
    intervalMs: Long = REMINDER_INTERVAL_MS,
): Boolean {
    fun expired(stamp: Long) = stamp == 0L || now - stamp >= intervalMs
    return expired(lastBackup) && expired(lastReminder)
}

/** Names of old P Vault zips to remove; unrelated files and old Dude_ backups are ignored. */
internal fun backupNamesToDelete(names: List<String>, keep: Int = KEEP_BACKUPS): Set<String> =
    names.filter(::isBackupName)
        .sortedDescending()
        .drop(keep)
        .toSet()

/** The "connect Google Drive" suggestion comes back at most once a week. */
internal fun connectPromptDue(lastAsked: Long, now: Long, intervalMs: Long = REMINDER_INTERVAL_MS): Boolean =
    lastAsked <= 0L || now - lastAsked >= intervalMs || now < lastAsked

private fun isBackupName(name: String): Boolean = isPlainBackupName(name) || isProtectedBackupName(name)

private fun isPlainBackupName(name: String): Boolean =
    (name == "P_Vault.zip" || name.startsWith("P_Vault_")) && name.endsWith(".zip")

private fun isProtectedBackupName(name: String): Boolean =
    (name == "P_Vault.${BackupCrypto.EXTENSION}" || name.startsWith("P_Vault_")) &&
        name.endsWith(".${BackupCrypto.EXTENSION}")

/** One P Vault zip found in the backup folder. */
internal data class BackupFile(val id: String, val name: String, val modified: Long)

/**
 * The backups to remove so that only the newest [keep] remain. Works on files, not names,
 * because Drive allows several files with the same name: deleting "by name" would remove
 * every copy at once. The file that was just written ([protectId]) is never returned.
 *
 * With [dropPlain] (a backup password is set) the unprotected zips go as well: leaving
 * them next to the protected backup would leave the vault readable without the password.
 */
internal fun backupsToDelete(
    files: List<BackupFile>,
    keep: Int = KEEP_BACKUPS,
    protectId: String? = null,
    dropPlain: Boolean = false,
): List<BackupFile> {
    val candidates = files.filter {
        if (dropPlain) isProtectedBackupName(it.name) else isBackupName(it.name)
    }
    val old = candidates
        // Timestamped names sort by age; the legacy "P_Vault.zip" sorts as oldest.
        .sortedWith(compareByDescending<BackupFile> { it.name }.thenByDescending { it.modified })
        .drop(keep.coerceAtLeast(1))
    val plain = if (dropPlain) files.filter { isPlainBackupName(it.name) } else emptyList()
    return (old + plain).filter { it.id != protectId }
}

/**
 * Writes one ordinary zip into a folder picked through Android's system file picker.
 * The Google Drive app handles the upload; P Vault only writes through the picker.
 */
class DriveBackupStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("dude_drive", Context.MODE_PRIVATE)
    private val resolver = app.contentResolver

    fun state(): DriveBackupState {
        val tree = uri()
        val connected = tree != null && hasPermission(tree)
        return DriveBackupState(
            connected = connected,
            account = if (connected) {
                prefs.getString("primary_name", null).orEmpty().ifBlank { "Google Drive folder" }
            } else {
                ""
            },
        )
    }

    fun lastBackupAt(): Long = prefs.getLong("last_backup", 0L)

    fun reminderDue(days: Int = 7): Boolean = backupReminderDue(
        lastBackup = prefs.getLong("last_backup", 0L),
        lastReminder = prefs.getLong("last_reminder", 0L),
        now = System.currentTimeMillis(),
        intervalMs = days.coerceAtLeast(1) * 24L * 60 * 60 * 1000,
    )

    fun markReminderShown() {
        prefs.edit().putLong("last_reminder", System.currentTimeMillis()).apply()
    }

    /** True when Drive was never connected and the user has not been asked for a week. */
    fun connectPromptDue(now: Long = System.currentTimeMillis()): Boolean =
        connectPromptDue(prefs.getLong("connect_prompt", 0L), now)

    fun markConnectPromptShown() {
        prefs.edit().putLong("connect_prompt", System.currentTimeMillis()).apply()
    }

    // ---- Backup password ------------------------------------------------
    // Only the stretched key is kept, never the password itself. It sits in the app's
    // private storage, next to the vault it protects, so automatic backups can run
    // without asking each time.

    fun isEncrypted(): Boolean = storedKey() != null

    /** Slow (key stretching); call off the main thread. */
    fun setPassword(password: CharArray): Boolean = runCatching {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey(password, salt)
        prefs.edit()
            .putString("enc_key", Base64.encodeToString(key, Base64.NO_WRAP))
            .putString("enc_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putInt("enc_iterations", BackupCrypto.DEFAULT_ITERATIONS)
            .commit()
    }.getOrDefault(false)

    fun clearPassword() {
        prefs.edit().remove("enc_key").remove("enc_salt").remove("enc_iterations").commit()
    }

    private class StoredKey(val key: ByteArray, val salt: ByteArray, val iterations: Int)

    private fun storedKey(): StoredKey? = runCatching {
        val key = Base64.decode(prefs.getString("enc_key", null) ?: return null, Base64.NO_WRAP)
        val salt = Base64.decode(prefs.getString("enc_salt", null) ?: return null, Base64.NO_WRAP)
        if (key.size != 32 || salt.isEmpty()) return null
        StoredKey(key, salt, prefs.getInt("enc_iterations", BackupCrypto.DEFAULT_ITERATIONS))
    }.getOrNull()

    fun markPending() {
        prefs.edit()
            .putBoolean("pending", true)
            .putLong("pending_at", System.currentTimeMillis())
            .apply()
    }

    fun hasPending(): Boolean = prefs.getBoolean("pending", false)

    fun clearPending() {
        prefs.edit().remove("pending").remove("pending_at").apply()
    }

    fun connect(tree: Uri): Boolean = runCatching {
        val old = uri()
        resolver.takePersistableUriPermission(
            tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        if (old != null && old != tree) {
            runCatching {
                resolver.releasePersistableUriPermission(
                    old,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs.edit()
            .putString("primary_uri", tree.toString())
            .putString("primary_name", folderName(tree) ?: "Google Drive folder")
            .apply()
    }.isSuccess

    fun disconnect(): BackupResult = runCatching {
        uri()?.let {
            runCatching {
                resolver.releasePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs.edit().remove("primary_uri").remove("primary_name").apply()
        BackupResult(true, "Google Drive disconnected")
    }.getOrElse { BackupResult(false, "Could not remove that Drive") }

    // shortcut: if the Drive app queues an upload and only reports quota exhaustion later,
    // P Vault cannot see it. The user must free Drive space and back up again.
    fun backupNow(keepBackups: Int = KEEP_BACKUPS): BackupResult {
        val startedAt = System.currentTimeMillis()
        val keep = keepBackups.coerceIn(1, 10)
        val tree = uri()?.takeIf(::hasPermission)
            ?: return BackupResult(false, "Connect Google Drive first")

        val zip = runCatching { createZip() }.getOrElse {
            return BackupResult(false, "Could not create the backup file")
        }
        var written: Uri? = null
        val error = writeToTree(tree, zip) { written = it }
        zip.delete()
        if (error != null) return BackupResult(false, "Backup failed: $error")

        val pendingAt = prefs.getLong("pending_at", 0L)
        val editor = prefs.edit().putLong("last_backup", System.currentTimeMillis())
        if (pendingAt <= startedAt) editor.putBoolean("pending", false)
        editor.apply()
        pruneBackups(tree, keep, written, dropPlain = isProtectedBackupName(zip.name))
        return BackupResult(true, "Backup saved. The Drive app uploads it in the background.")
    }

    fun suggestedName(): String = if (isEncrypted()) "P_Vault.${BackupCrypto.EXTENSION}" else "P_Vault.zip"

    fun exportTo(target: Uri): BackupResult {
        val zip = runCatching { createZip() }.getOrElse {
            return BackupResult(false, "Could not create the backup file")
        }
        return try {
            val output = resolver.openOutputStream(target)
                ?: throw IOException("Could not open that file")
            output.use { sink -> zip.inputStream().use { it.copyTo(sink) } }
            BackupResult(true, "Vault exported")
        } catch (_: Exception) {
            BackupResult(false, "Could not export the vault")
        } finally {
            zip.delete()
        }
    }

    /**
     * Adds the backup to the vault. Everything already on the phone stays; see [BackupMerge].
     */
    fun restoreFromZip(source: Uri, password: CharArray? = null): BackupResult {
        val staging = File(app.noBackupFilesDir, "restore_staging")
        File(app.noBackupFilesDir, "restore_old").deleteRecursively()
        staging.deleteRecursively()
        staging.mkdirs()

        // A protected backup is opened into a private temporary zip first.
        val opened = File(app.noBackupFilesDir, "restore_opened.zip")
        opened.delete()
        val read = try {
            val stream = resolver.openInputStream(source)
            if (stream == null) {
                null
            } else {
                BufferedInputStream(stream).use { input ->
                    input.mark(BackupCrypto.headerPeekBytes)
                    val start = ByteArray(BackupCrypto.headerPeekBytes)
                    var filled = 0
                    while (filled < start.size) {
                        val got = input.read(start, filled, start.size - filled)
                        if (got < 0) break
                        filled += got
                    }
                    input.reset()
                    if (!BackupCrypto.isEncrypted(start.copyOf(filled))) {
                        BackupZip.read(input, staging)
                    } else {
                        val header = BackupCrypto.readHeader(input)
                        val saved = storedKey()
                        val key = when {
                            password != null -> BackupCrypto.deriveKey(password, header.salt, header.iterations)
                            // Made on this phone with the current password: no need to ask.
                            saved != null && saved.salt.contentEquals(header.salt) -> saved.key
                            else -> {
                                staging.deleteRecursively()
                                return BackupResult(false, "This backup needs its password", passwordNeeded = true)
                            }
                        }
                        opened.outputStream().buffered().use { BackupCrypto.decrypt(input, header, key, it) }
                        opened.inputStream().use { BackupZip.read(it, staging) }
                    }
                }
            }
        } catch (_: WrongPasswordException) {
            staging.deleteRecursively()
            return BackupResult(
                false,
                if (password != null) "Wrong password" else "This backup needs its password",
                passwordNeeded = true,
            )
        } catch (_: Exception) {
            null
        } finally {
            opened.delete()
        }
        if (read == null || !read.ok) {
            staging.deleteRecursively()
            return BackupResult(false, read?.error ?: "Could not open that file")
        }

        return try {
            val added = BackupMerge.merge(staging, app.filesDir)
            val profile = ProfileStore(app)
            if (read.profileName != null && profile.load().name.isBlank()) profile.setName(read.profileName)
            // The vault now holds more than the newest backup does.
            if (added > 0) markPending()
            BackupResult(
                true,
                when (added) {
                    0 -> "Nothing new in that backup. Your files are unchanged."
                    1 -> "Restored 1 file. Your existing files were kept."
                    else -> "Restored $added files. Your existing files were kept."
                },
            )
        } catch (_: Exception) {
            // Merging only ever adds files, so whatever was on the phone is still there.
            BackupResult(false, "The restore stopped part-way. Your existing files are safe; try again.")
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun uri(): Uri? = prefs.getString("primary_uri", null)?.let(Uri::parse)

    private fun hasPermission(tree: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    private fun folderName(tree: Uri): String? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        resolver.query(
            docUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    /** The backup file to hand out: a zip, or the zip sealed with the backup password. */
    private fun createZip(): File {
        val stamp = stamp()
        val backup = File(app.cacheDir, "P_Vault_$stamp.zip")
        val name = app.getSharedPreferences("dude_profile", Context.MODE_PRIVATE)
            .getString("name", "").orEmpty()
        try {
            BackupZip.write(app.filesDir, backup, name)
        } catch (error: Exception) {
            backup.delete()
            throw error
        }
        val saved = storedKey() ?: return backup
        val sealed = File(app.cacheDir, "P_Vault_$stamp.${BackupCrypto.EXTENSION}")
        try {
            backup.inputStream().buffered().use { input ->
                sealed.outputStream().buffered().use { output ->
                    BackupCrypto.encrypt(input, output, saved.key, saved.salt, saved.iterations)
                }
            }
        } catch (error: Exception) {
            // Never fall back to an unprotected backup when a password is set.
            sealed.delete()
            throw error
        } finally {
            backup.delete()
        }
        return sealed
    }

    /** Returns null on success, or a short reason on failure. */
    private fun writeToTree(tree: Uri, zip: File, onCreated: (Uri) -> Unit): String? {
        var created: Uri? = null
        return try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree),
            )
            created = DocumentsContract.createDocument(
                resolver,
                parent,
                if (isProtectedBackupName(zip.name)) "application/octet-stream" else "application/zip",
                // Each backup gets its own timestamped name (P_Vault_<stamp>.zip), so the
                // newest one is always identifiable and older ones can be pruned safely.
                zip.name,
            ) ?: return "Drive did not allow creating a file"
            val output = resolver.openOutputStream(created)
                ?: throw IOException("Could not open the Drive folder")
            val copied = output.use { sink -> zip.inputStream().use { it.copyTo(sink) } }
            if (copied != zip.length()) throw IOException("The backup was only partly written")
            onCreated(created)
            null
        } catch (error: Exception) {
            created?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            error.message ?: "Drive write failed"
        }
    }

    /** Keeps the newest P Vault zips; old Dude_ backups and other files are left alone. */
    private fun pruneBackups(tree: Uri, keep: Int, justWritten: Uri?, dropPlain: Boolean = false) {
        runCatching {
            val protectId = justWritten?.let { runCatching { DocumentsContract.getDocumentId(it) }.getOrNull() }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree),
            )
            val backups = mutableListOf<BackupFile>()
            resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val modified = if (cursor.isNull(2)) 0L else cursor.getLong(2)
                    backups += BackupFile(id, name, modified)
                }
            }
            // If the new backup is not listed yet, do not prune: without it in the list
            // the count would be off and one good older backup would be removed too early.
            if (protectId != null && backups.none { it.id == protectId }) return@runCatching
            backupsToDelete(backups, keep, protectId, dropPlain).forEach { file ->
                runCatching {
                    DocumentsContract.deleteDocument(
                        resolver,
                        DocumentsContract.buildDocumentUriUsingTree(tree, file.id),
                    )
                }
            }
        }
    }

    private fun moveTo(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (source.renameTo(target)) return
        source.copyRecursively(target, overwrite = true)
        source.deleteRecursively()
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
}
