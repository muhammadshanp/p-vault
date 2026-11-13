package com.dudeapp.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
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

data class BackupResult(val ok: Boolean, val message: String)

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
    names.filter { (it == "P_Vault.zip" || it.startsWith("P_Vault_")) && it.endsWith(".zip") }
        .sortedDescending()
        .drop(keep)
        .toSet()

private fun isBackupName(name: String): Boolean =
    (name == "P_Vault.zip" || name.startsWith("P_Vault_")) && name.endsWith(".zip")

/** One P Vault zip found in the backup folder. */
internal data class BackupFile(val id: String, val name: String, val modified: Long)

/**
 * The backups to remove so that only the newest [keep] remain. Works on files, not names,
 * because Drive allows several files with the same name: deleting "by name" would remove
 * every copy at once. The file that was just written ([protectId]) is never returned.
 */
internal fun backupsToDelete(
    files: List<BackupFile>,
    keep: Int = KEEP_BACKUPS,
    protectId: String? = null,
): List<BackupFile> =
    files.filter { isBackupName(it.name) }
        // Timestamped names sort by age; the legacy "P_Vault.zip" sorts as oldest.
        .sortedWith(compareByDescending<BackupFile> { it.name }.thenByDescending { it.modified })
        .drop(keep.coerceAtLeast(1))
        .filter { it.id != protectId }

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
        pruneBackups(tree, keep, written)
        return BackupResult(true, "Backup saved. The Drive app uploads it in the background.")
    }

    fun suggestedName(): String = "P_Vault.zip"

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
    fun restoreFromZip(source: Uri): BackupResult {
        val staging = File(app.noBackupFilesDir, "restore_staging")
        File(app.noBackupFilesDir, "restore_old").deleteRecursively()
        staging.deleteRecursively()
        staging.mkdirs()

        val read = runCatching {
            resolver.openInputStream(source)?.use { BackupZip.read(it, staging) }
        }.getOrNull()
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

    private fun createZip(): File {
        val backup = File(app.cacheDir, "P_Vault_${stamp()}.zip")
        val name = app.getSharedPreferences("dude_profile", Context.MODE_PRIVATE)
            .getString("name", "").orEmpty()
        try {
            BackupZip.write(app.filesDir, backup, name)
        } catch (error: Exception) {
            backup.delete()
            throw error
        }
        return backup
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
                "application/zip",
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
    private fun pruneBackups(tree: Uri, keep: Int, justWritten: Uri?) {
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
            backupsToDelete(backups, keep, protectId).forEach { file ->
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
