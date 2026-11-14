package com.dudeapp.vault

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** "2026-11-15 14.30", for naming scans and photos taken inside P Vault. */
internal fun captureStamp(now: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.US).format(Date(now))

/** Where the camera app writes the photo before it is moved into the vault. */
internal fun cameraTempFile(context: Context): File =
    File(context.cacheDir, "camera").apply { mkdirs() }.resolve("capture.jpg")

internal fun cameraTempUri(context: Context): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.updates", cameraTempFile(context))

/**
 * Opens Google's document scanner (part of Google Play services): it finds the page
 * edges, straightens the page and returns one PDF. The camera runs inside the scanner,
 * so P Vault needs no camera permission.
 */
internal fun startDocumentScan(
    activity: Activity,
    onReady: (IntentSenderRequest) -> Unit,
    onUnavailable: () -> Unit,
) {
    runCatching {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(40)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                runCatching { onReady(IntentSenderRequest.Builder(sender).build()) }
                    .onFailure { onUnavailable() }
            }
            .addOnFailureListener { onUnavailable() }
    }.onFailure { onUnavailable() }
}

/** Turns one photo into a one-page PDF, the right way up. */
internal fun photoToPdf(photo: File, output: File): Boolean = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(photo.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
    // Camera photos are far larger than a page needs; keep the longer side near 2000 px.
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1800) sample *= 2
    val decoded = BitmapFactory.decodeFile(photo.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return false

    val degrees = when (
        ExifInterface(photo.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val upright = if (degrees == 0f) {
        decoded
    } else {
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true)
    }

    // An A4-wide page (595 points) with the photo's own proportions.
    val pageWidth = 595
    val pageHeight = (pageWidth.toFloat() * upright.height / upright.width).roundToInt().coerceAtLeast(1)
    val document = PdfDocument()
    try {
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        page.canvas.drawBitmap(upright, null, Rect(0, 0, pageWidth, pageHeight), Paint(Paint.FILTER_BITMAP_FLAG))
        document.finishPage(page)
        output.outputStream().use { document.writeTo(it) }
    } finally {
        document.close()
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
    }
    output.length() > 0
}.getOrDefault(false)
