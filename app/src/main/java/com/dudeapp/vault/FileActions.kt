package com.dudeapp.vault

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "Holiday.jpg": the title the user sees plus the real extension, without the stamp. */
internal fun exportName(title: String, extension: String): String {
    val clean = title.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").trim().ifBlank { "P Vault file" }
    return if (extension.isBlank()) clean else "$clean.${extension.lowercase()}"
}

private fun mimeOf(file: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"

/** Opens Android's share sheet for one vault file. Returns false if it could not be opened. */
fun shareVaultFile(context: Context, item: VaultItem): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.updates",
        item.file,
        exportName(item.title, item.file.extension),
    )
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeOf(item.file))
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, item.title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(
        Intent.createChooser(send, "Share ${item.title}").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
    true
}.getOrDefault(false)

/** True when a copy can be put straight into the phone's Downloads folder. */
fun canSaveToDownloads(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

/**
 * Copies a vault file to Downloads/P Vault on the phone (Android 10 and newer).
 * Returns true when the whole file was written.
 */
suspend fun saveToDownloads(context: Context, item: VaultItem): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, exportName(item.title, item.file.extension))
        put(MediaStore.Downloads.MIME_TYPE, mimeOf(item.file))
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/P Vault")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val target = runCatching {
        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }.getOrNull() ?: return@withContext false
    val written = copyTo(context, item.file, target)
    if (!written) {
        runCatching { resolver.delete(target, null, null) }
        return@withContext false
    }
    runCatching {
        resolver.update(target, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    }
    true
}

/** Copies a vault file to a place the user picked. Returns true when all of it was written. */
suspend fun saveToPicked(context: Context, item: VaultItem, target: Uri): Boolean =
    withContext(Dispatchers.IO) { copyTo(context, item.file, target) }

private fun copyTo(context: Context, source: File, target: Uri): Boolean = runCatching {
    val output = context.contentResolver.openOutputStream(target) ?: return@runCatching false
    val copied = output.use { sink -> source.inputStream().use { it.copyTo(sink) } }
    copied == source.length()
}.getOrDefault(false)
