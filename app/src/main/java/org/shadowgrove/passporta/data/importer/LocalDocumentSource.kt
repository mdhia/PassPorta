package org.shadowgrove.passporta.data.importer

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import org.shadowgrove.passporta.util.Bitmaps

/**
 * Reads files selected by the user (`content://`, `file://`).
 *
 * Bundles ContentResolver access in one place, so ViewModels and importers don't need to know
 * their own Android details.
 */
class LocalDocumentSource(context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * @param maxBytes upper limit; larger files are discarded so an accidentally selected video
     *   file doesn't fill up the app's storage.
     * @return content or `null` if the source is unreadable or too large.
     */
    fun readBytes(uri: Uri, maxBytes: Long = MAX_BYTES): ByteArray? = runCatching {
        val size = sizeOf(uri)
        if (size != null && size > maxBytes) {
            Log.w(TAG, "File is too large at $size bytes")
            return null
        }

        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            // Even without a reliable size, reading must not be unbounded.
            val bytes = stream.readBytes()
            if (bytes.size > maxBytes) null else bytes
        }
    }.onFailure { error ->
        Log.w(TAG, "Source could not be read", error)
    }.getOrNull()

    /** Display name of the file, e.g. `ticket.pdf`. */
    fun displayName(uri: Uri): String? = runCatching {
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> cursor.firstString() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
    }.getOrNull()

    fun mimeType(uri: Uri): String? =
        runCatching { appContext.contentResolver.getType(uri) }.getOrNull()

    /** Loads an image already downscaled - a small edge length is enough for logos. */
    fun decodeImage(uri: Uri, maxSize: Int = Bitmaps.MAX_LOGO_SIZE_PX): Bitmap? =
        readBytes(uri, MAX_IMAGE_BYTES)?.let { bytes -> Bitmaps.decodeSampled(bytes, maxSize) }

    private fun sizeOf(uri: Uri): Long? = runCatching {
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
    }.getOrNull()

    private fun Cursor.firstString(): String? =
        if (moveToFirst() && !isNull(0)) getString(0)?.takeIf { it.isNotBlank() } else null

    private companion object {
        const val TAG = "LocalDocumentSource"

        /** Original documents: PDFs with many pages can well be a few megabytes. */
        const val MAX_BYTES = 32L * 1024 * 1024

        const val MAX_IMAGE_BYTES = 24L * 1024 * 1024
    }
}
