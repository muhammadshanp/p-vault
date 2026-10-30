package com.dudeapp.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The stock OpenDocumentTree contract does not request write or persistable access.
 * Without these flags P Vault can create a file only for the current session, and
 * takePersistableUriPermission() fails, so the Drive folder would be forgotten on restart.
 */
class PersistentOpenDocumentTree : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        super.createIntent(context, input).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
        }
}

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
    names.filter { it.startsWith("P_Vault_") && it.endsWith(".zip") }
        .sortedDescending()
        .drop(keep)
        .toSet()

/**
 * Writes one ordinary zip into a folder picked through Android's system file picker.
 * P Vault itself has no Internet permission; the Google Drive app handles the upload.
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
        val error = writeToTree(tree, zip)
        zip.delete()
        if (error != null) return BackupResult(false, "Backup failed: $error")

        val pendingAt = prefs.getLong("pending_at", 0L)
        val editor = prefs.edit().putLong("last_backup", System.currentTimeMillis())
        if (pendingAt <= startedAt) editor.putBoolean("pending", false)
        editor.apply()
        pruneBackups(tree, keep)
        return BackupResult(true, "Backed up to Google Drive")
    }

    fun suggestedName(): String = "P_Vault_${stamp()}.zip"

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

    fun restoreFromZip(source: Uri): BackupResult {
        val staging = File(app.noBackupFilesDir, "restore_staging")
        val old = File(app.noBackupFilesDir, "restore_old")
        staging.deleteRecursively()
        old.deleteRecursively()
        staging.mkdirs()

        val read = runCatching {
            resolver.openInputStream(source)?.use { BackupZip.read(it, staging) }
        }.getOrNull()
        if (read == null || !read.ok) {
            staging.deleteRecursively()
            return BackupResult(false, read?.error ?: "Could not open that file")
        }

        val current = app.filesDir.listFiles().orEmpty()
        try {
            current.forEach { moveTo(it, File(old, it.name)) }
        } catch (_: Exception) {
            old.listFiles().orEmpty().forEach { runCatching { moveTo(it, File(app.filesDir, it.name)) } }
            staging.deleteRecursively()
            return BackupResult(false, "Could not prepare the restore")
        }

        return try {
            staging.listFiles().orEmpty().forEach { moveTo(it, File(app.filesDir, it.name)) }
            listOf("vault", "reels", "chats").forEach { File(app.filesDir, it).mkdirs() }
            if (File(app.filesDir, "reels").listFiles()?.any { it.isDirectory } != true) {
                File(app.filesDir, "reels/00_Pending").mkdirs()
            }
            if (read.profileName != null) ProfileStore(app).setName(read.profileName)
            prefs.edit().putBoolean("pending", false).apply()
            old.deleteRecursively()
            staging.deleteRecursively()
            BackupResult(true, "Backup restored")
        } catch (_: Exception) {
            app.filesDir.listFiles().orEmpty().forEach { runCatching { it.deleteRecursively() } }
            old.listFiles().orEmpty().forEach { runCatching { moveTo(it, File(app.filesDir, it.name)) } }
            staging.deleteRecursively()
            BackupResult(false, "Could not restore that backup")
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
    private fun writeToTree(tree: Uri, zip: File): String? {
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
                "P_Vault_${stamp()}.zip",
            ) ?: return "Drive did not allow creating a file"
            val output = resolver.openOutputStream(created)
                ?: throw IOException("Could not open the Drive folder")
            output.use { sink -> zip.inputStream().use { it.copyTo(sink) } }
            null
        } catch (error: Exception) {
            created?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            error.message ?: "Drive write failed"
        }
    }

    /** Keeps the newest P Vault zips; old Dude_ backups are left alone. */
    private fun pruneBackups(tree: Uri, keep: Int) {
        runCatching {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree),
            )
            val backups = mutableListOf<Pair<String, Uri>>()
            resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    if (!name.startsWith("P_Vault_") || !name.endsWith(".zip")) continue
                    val id = cursor.getString(0) ?: continue
                    backups += name to DocumentsContract.buildDocumentUriUsingTree(tree, id)
                }
            }
            val old = backupNamesToDelete(backups.map { it.first }, keep)
            backups.filter { it.first in old }.forEach { (_, uri) ->
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
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
