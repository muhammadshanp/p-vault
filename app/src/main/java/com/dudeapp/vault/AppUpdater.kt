package com.dudeapp.vault

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val APK_MIME = "application/vnd.android.package-archive"

/**
 * Downloads the release APK into the app's cache and reports progress as 0..100
 * (or -1 while the size is unknown). Returns the file, or null if the download failed.
 */
suspend fun downloadUpdate(
    context: Context,
    update: AppUpdate,
    onProgress: (Int) -> Unit,
): File? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val temp = File(dir, "download.tmp")
        val target = File(dir, "P-Vault-update.apk")

        val connection = URL(update.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "PVault")
        try {
            if (connection.responseCode != 200) return@runCatching null
            val total = connection.contentLengthLong
            var done = 0L
            var lastPercent = -2
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val percent = if (total > 0) (done * 100 / total).toInt() else -1
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
            if (done == 0L || (total > 0 && done != total)) return@runCatching null
            if (!temp.renameTo(target)) return@runCatching null
            target
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

/**
 * Checks a downloaded APK before handing it to Android: it must be P Vault, not older
 * than the installed version, and signed with the same key. Returns a message for the
 * user when it cannot be installed, or null when it looks right.
 */
@Suppress("DEPRECATION")
fun updateProblem(context: Context, apk: File): String? {
    val manager = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
    val archive = runCatching { manager.getPackageArchiveInfo(apk.absolutePath, flags) }.getOrNull()
        ?: return "The downloaded file is not a valid app. Try again."
    if (archive.packageName != context.packageName) {
        return "The downloaded file is not P Vault."
    }
    val installed = runCatching { manager.getPackageInfo(context.packageName, flags) }.getOrNull()
        ?: return null
    if (archive.versionCode < installed.versionCode) {
        return "The downloaded version is older than the one installed."
    }
    val newKeys = signerDigests(archive)
    val currentKeys = signerDigests(installed)
    if (newKeys != null && currentKeys != null && newKeys.intersect(currentKeys).isEmpty()) {
        return "This update is signed with a different key, so Android cannot install it over this app."
    }
    return null
}

/** SHA-256 of each signing certificate, or null when Android does not report them. */
@Suppress("DEPRECATION")
private fun signerDigests(info: PackageInfo): Set<String>? {
    val found = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val signing = info.signingInfo ?: return null
        if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
    } else {
        info.signatures
    }
    val signatures = found ?: return null
    val digests = signatures.map { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }.toSet()
    return digests.takeIf { it.isNotEmpty() }
}

/** Opens Android's own install screen for the APK. The user still confirms there. */
fun launchInstaller(context: Context, apk: File): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}.isSuccess
