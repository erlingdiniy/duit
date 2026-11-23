package com.diniy.duit.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max

data class StoredReceiptAttachment(
    val relativePath: String,
    val mimeType: String,
    val originalFileName: String,
    val fileSizeBytes: Long
)

data class PdfPagePreview(
    val bitmap: Bitmap,
    val pageIndex: Int,
    val pageCount: Int
)

/**
 * Manages app-private receipt files under `filesDir/receipts/`.
 * Never writes to shared external storage or MediaStore, enforces safe filenames
 * against path traversal, compresses imported images, and caps raw PDF sizes.
 */
class ReceiptFileStorage(private val rootDir: File) {

    constructor(context: Context) : this(File(context.filesDir, RECEIPTS_DIR_NAME))

    private fun ensureDir(): File {
        recoverInterruptedSwap()
        return rootDir.apply { mkdirs() }
    }

    /**
     * Completes or rolls back any interrupted swap:
     * - If commit marker exists and staging dir is intact, Room already committed -> finish moving staging to live.
     * - Else if live dir is missing and old backup exists, restore old -> live (pre-commit abort).
     */
    private fun recoverInterruptedSwap() {
        val commitMarker = siblingDir(COMMIT_MARKER_SUFFIX)
        val staging = siblingDir(STAGING_SUFFIX)
        val old = siblingDir(OLD_SUFFIX)
        if (commitMarker.exists()) {
            if (staging.isDirectory) {
                if (rootDir.exists()) {
                    old.deleteRecursively()
                    if (!rootDir.renameTo(old)) return
                }
                if (staging.renameTo(rootDir)) {
                    commitMarker.deleteRecursively()
                    old.deleteRecursively()
                }
            } else {
                commitMarker.deleteRecursively()
                old.deleteRecursively()
            }
            return
        }
        if (!rootDir.exists() && old.isDirectory) {
            old.renameTo(rootDir)
        }
    }

    fun sanitizeFileName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains("..")) {
            return null
        }
        val clean = File(trimmed).name
        if (clean.isEmpty() || clean == "." || clean == ".." || !SAFE_NAME_REGEX.matches(clean)) {
            return null
        }
        return clean
    }

    fun resolveFile(relativePath: String): File? {
        val safe = sanitizeFileName(relativePath) ?: return null
        val file = File(ensureDir(), safe)
        return file.takeIf { it.exists() && it.isFile }
    }

    fun importFromUri(context: Context, uri: Uri): StoredReceiptAttachment {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(context, uri) ?: "receipt"
        val detectedMime = (resolver.getType(uri) ?: guessMimeFromName(displayName)).lowercase()

        return when {
            detectedMime == MIME_PDF -> importPdf(context, uri, displayName)
            detectedMime.startsWith("image/") -> importImage(context, uri, displayName)
            else -> throw IOException("Unsupported file type. Choose an image or PDF receipt.")
        }
    }

    fun importCameraFile(tempFile: File): StoredReceiptAttachment {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            throw IOException("Captured photo is empty.")
        }
        try {
            val bytes = tempFile.readBytes()
            return storeCompressedImage(bytes, originalFileName = "camera-receipt.jpg")
        } finally {
            tempFile.delete()
        }
    }

    fun createTempCameraTarget(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, SHARE_CACHE_DIR).apply { mkdirs() }
        val file = File(dir, CAMERA_TEMP_NAME)
        if (file.exists()) file.delete()
        val uri = FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)
        return file to uri
    }

    fun deleteFile(relativePath: String) {
        val safe = sanitizeFileName(relativePath) ?: return
        val file = File(ensureDir(), safe)
        if (file.exists()) file.delete()
    }

    fun readFileBytes(relativePath: String): ByteArray? {
        val file = resolveFile(relativePath) ?: return null
        if (file.length() > MAX_FILE_BYTES) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    fun writeRestoredFile(relativePath: String, bytes: ByteArray): Boolean {
        val safe = sanitizeFileName(relativePath) ?: return false
        if (bytes.isEmpty() || bytes.size > MAX_FILE_BYTES) return false
        val target = File(ensureDir(), safe)
        target.writeBytes(bytes)
        return true
    }

    /** Replaces all stored receipt files during backup restore (stage, then swap). */
    fun replaceAllFiles(filesByName: Map<String, ByteArray>) {
        commitStagedRestore(stageRestoredFiles(filesByName))
    }

    /**
     * Writes incoming receipt files into a sibling staging dir without touching live files.
     * Throws on any write failure (staging is cleaned up); live receipts stay intact.
     */
    fun stageRestoredFiles(filesByName: Map<String, ByteArray>): File {
        val staging = siblingDir(STAGING_SUFFIX)
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("Could not prepare receipt restore folder.")
        try {
            filesByName.forEach { (name, bytes) ->
                val safe = sanitizeFileName(name)
                if (safe != null && bytes.isNotEmpty() && bytes.size <= MAX_FILE_BYTES) {
                    File(staging, safe).writeBytes(bytes)
                }
            }
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        }
        return staging
    }

    /** Swaps a staged dir into place. Records commit intent first so Room+files converge after crash. */
    fun commitStagedRestore(staging: File) {
        recoverInterruptedSwap()
        val old = siblingDir(OLD_SUFFIX)
        val commitMarker = siblingDir(COMMIT_MARKER_SUFFIX)
        old.deleteRecursively()
        commitMarker.deleteRecursively()
        if (!commitMarker.createNewFile()) {
            throw IOException("Could not record receipt restore commit marker.")
        }
        if (rootDir.exists() && !rootDir.renameTo(old)) {
            commitMarker.delete()
            throw IOException("Could not swap receipt folder.")
        }
        if (!staging.renameTo(rootDir)) {
            old.renameTo(rootDir) // roll back so existing receipts remain
            commitMarker.delete()
            throw IOException("Could not swap receipt folder.")
        }
        commitMarker.delete()
        old.deleteRecursively()
    }

    fun discardStagedRestore(staging: File) {
        staging.deleteRecursively()
    }

    fun storedFileSize(relativePath: String): Long = resolveFile(relativePath)?.length() ?: 0L

    private fun siblingDir(suffix: String): File =
        File(rootDir.absoluteFile.parentFile, rootDir.name + suffix)

    fun clearAllFiles() {
        rootDir.listFiles()?.forEach { if (it.isFile) it.delete() }
        siblingDir(STAGING_SUFFIX).deleteRecursively()
        siblingDir(OLD_SUFFIX).deleteRecursively()
        siblingDir(COMMIT_MARKER_SUFFIX).deleteRecursively()
    }

    fun decodeImagePreview(relativePath: String, maxDimensionPx: Int = 1400): Bitmap? {
        val file = resolveFile(relativePath) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun renderPdfPage(relativePath: String, pageIndex: Int = 0, maxWidthPx: Int = 1200): PdfPagePreview? {
        val file = resolveFile(relativePath) ?: return null
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return@runCatching null
                    val clamped = pageIndex.coerceIn(0, renderer.pageCount - 1)
                    renderer.openPage(clamped).use { page ->
                        val scale = (maxWidthPx.toFloat() / page.width.coerceAtLeast(1)).coerceAtMost(2f)
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        PdfPagePreview(bmp, clamped, renderer.pageCount)
                    }
                }
            }
        }.getOrNull()
    }

    private fun importPdf(context: Context, uri: Uri, displayName: String): StoredReceiptAttachment {
        val raw = readCappedBytes(context, uri, MAX_FILE_BYTES)
        if (raw.size < 5 || !raw.copyOfRange(0, 5).contentEquals(PDF_MAGIC)) {
            throw IOException("Selected file is not a valid PDF.")
        }
        val fileName = "receipt_${UUID.randomUUID().toString().replace("-", "")}.pdf"
        val out = File(ensureDir(), fileName)
        out.writeBytes(raw)
        return StoredReceiptAttachment(
            relativePath = fileName,
            mimeType = MIME_PDF,
            originalFileName = displayName,
            fileSizeBytes = out.length()
        )
    }

    private fun importImage(context: Context, uri: Uri, displayName: String): StoredReceiptAttachment {
        val raw = readCappedBytes(context, uri, MAX_RAW_IMAGE_BYTES)
        return storeCompressedImage(raw, displayName)
    }

    private fun storeCompressedImage(raw: ByteArray, originalFileName: String): StoredReceiptAttachment {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Could not read selected image.")
        }
        val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_DIMENSION_PX)
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Could not decode selected image.")
        val scaled = scaleDownIfNeeded(decoded, MAX_IMAGE_DIMENSION_PX)
        val compressed = try {
            ByteArrayOutputStream().use { out ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "Image compression failed." }
                out.toByteArray()
            }
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
        if (compressed.size > MAX_FILE_BYTES) {
            throw IOException("Receipt image is too large to store.")
        }
        val fileName = "receipt_${UUID.randomUUID().toString().replace("-", "")}.jpg"
        val target = File(ensureDir(), fileName)
        target.writeBytes(compressed)
        return StoredReceiptAttachment(
            relativePath = fileName,
            mimeType = MIME_JPEG,
            originalFileName = originalFileName,
            fileSizeBytes = target.length()
        )
    }

    private fun readCappedBytes(context: Context, uri: Uri, maxBytes: Long): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open selected file.")
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                total += read
                if (total > maxBytes) {
                    throw IOException("File exceeds ${maxBytes / (1024 * 1024)} MB limit.")
                }
                out.write(buf, 0, read)
            }
            out.toByteArray()
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun guessMimeFromName(name: String): String = when {
        name.endsWith(".pdf", ignoreCase = true) -> MIME_PDF
        name.endsWith(".png", ignoreCase = true) -> "image/png"
        name.endsWith(".webp", ignoreCase = true) -> "image/webp"
        else -> MIME_JPEG
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        val largest = max(width, height)
        while (largest / (sample * 2) >= maxDim) {
            sample *= 2
        }
        return sample
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / largest.toFloat()
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    companion object {
        const val RECEIPTS_DIR_NAME = "receipts"
        const val MIME_PDF = "application/pdf"
        const val MIME_JPEG = "image/jpeg"
        const val MAX_FILE_BYTES = 10L * 1024L * 1024L
        /** Total raw receipt bytes allowed in one in-memory JSON backup; encrypt copies the payload several times. */
        const val MAX_BACKUP_RECEIPT_BYTES = 12L * 1024L * 1024L
        private const val STAGING_SUFFIX = ".restore-staging"
        private const val OLD_SUFFIX = ".restore-old"
        private const val COMMIT_MARKER_SUFFIX = ".restore-commit"
        private const val MAX_RAW_IMAGE_BYTES = 20L * 1024L * 1024L
        private const val MAX_IMAGE_DIMENSION_PX = 1600
        private const val JPEG_QUALITY = 82
        private const val SHARE_CACHE_DIR = "shared"
        private const val CAMERA_TEMP_NAME = "duit-receipt-capture.jpg"
        private const val FILE_PROVIDER_SUFFIX = ".supportqr"
        private val SAFE_NAME_REGEX = Regex("^[A-Za-z0-9._-]+$")
        private val PDF_MAGIC = "%PDF-".encodeToByteArray()
    }
}
