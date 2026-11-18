package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.max

data class ProcessedBillDocument(
    val bitmapForOcr: Bitmap,
    val originalPreviewBase64: String,
    val fileHashSha256: String,
    val mimeType: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val protectedStorageRef: String
)

object BillDocumentHelper {

    fun createCameraOutputUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera_scans").apply { mkdirs() }
        pruneOldCameraTempFiles(dir)
        val file = File(dir, "bill_capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun processBillUri(context: Context, uri: Uri, billId: String): ProcessedBillDocument? {
        return try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
                ?: if (uri.toString().lowercase().endsWith(".pdf")) "application/pdf" else "image/jpeg"
            val fileName = queryFileName(context, uri) ?: "bill_${billId.takeLast(6)}"

            val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val fileSize = rawBytes.size.toLong()
            val sha256 = sha256Hex(rawBytes)

            // Persist private protected copy strictly inside app-private filesDir (never public storage)
            val protectedDir = File(context.filesDir, "protected_bills").apply { mkdirs() }
            val ext = if (mimeType.contains("pdf", ignoreCase = true)) "pdf" else "jpg"
            val protectedFile = File(protectedDir, "${billId}_orig.$ext")
            protectedFile.writeBytes(rawBytes)
            val storageRef = "protected://bill_originals/$billId/$sha256"

            val decoded = if (mimeType.contains("pdf", ignoreCase = true)) {
                renderPdfFirstPage(protectedFile)
            } else {
                val rawBmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                rawBmp?.let { applyExifOrientation(protectedFile, it) }
            } ?: return null

            val ocrBitmap = scaleBitmap(decoded, maxDim = 1400)
            val previewBase64 = compressToBase64(decoded, maxDim = 900, maxBytes = 240_000)
            if (decoded != ocrBitmap) {
                decoded.recycle()
            }

            // Remove temporary camera scan file once copied into protected app-private storage
            pruneOldCameraTempFiles(File(context.cacheDir, "camera_scans"), maxAgeMillis = 0L)

            ProcessedBillDocument(
                bitmapForOcr = ocrBitmap,
                originalPreviewBase64 = previewBase64,
                fileHashSha256 = sha256,
                mimeType = mimeType,
                fileName = fileName,
                fileSizeBytes = fileSize,
                protectedStorageRef = storageRef
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) {
                bitmap
            } else {
                val matrix = Matrix().apply { postRotate(degrees) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) bitmap.recycle()
                rotated
            }
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun pruneOldCameraTempFiles(dir: File, maxAgeMillis: Long = 10 * 60_000L) {
        if (!dir.exists()) return
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() <= cutoff) {
                file.delete()
            }
        }
    }

    private fun renderPdfFirstPage(pdfFile: File): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount <= 0) return null
            page = renderer.openPage(0)
            val width = (page.width * 2).coerceIn(600, 1800)
            val height = (page.height * 2).coerceIn(800, 2400)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } catch (_: Exception) {
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val ratio = maxDim.toFloat() / max(src.width, src.height).coerceAtLeast(1)
        if (ratio >= 1f) return src
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun compressToBase64(src: Bitmap, maxDim: Int, maxBytes: Int): String {
        val scaled = scaleBitmap(src, maxDim)
        var quality = 78
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 12
        } while (bytes.size > maxBytes && quality >= 25)
        if (scaled != src) scaled.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
