package org.shadowgrove.passporta.data.importer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Copies a PDF handed to [org.shadowgrove.passporta.ImportActivity] by another app into
 * PassPorta's own cache, before it is handed off to [org.shadowgrove.passporta.MainActivity] for
 * preview.
 *
 * The read permission the calling app grants for its `content://` URI is only valid for the
 * activity that actually received the intent - it would not reliably survive the handoff to a
 * second activity. A local copy sidesteps that entirely and keeps the flow fully offline.
 */
object PdfImportStaging {

    private const val TAG = "PdfImportStaging"
    private const val DIRECTORY = "pdf_preview"
    private const val FILE_PREFIX = "preview-"
    private const val FILE_SUFFIX = ".pdf"

    /** Staged copies older than this are cleaned up on the next import. */
    private const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000

    /**
     * @return a `content://` URI (via [FileProvider]) for the staged copy, or `null` if [source]
     *   could not be read (unreadable, revoked permission, or too large).
     */
    fun stage(context: Context, source: Uri, documentSource: LocalDocumentSource): Uri? {
        val bytes = documentSource.readBytes(source) ?: return null

        return runCatching {
            val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
            deleteStaleFiles(directory)

            val target = File(directory, "$FILE_PREFIX${System.currentTimeMillis()}$FILE_SUFFIX")
            target.writeBytes(bytes)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        }.onFailure { error ->
            Log.w(TAG, "PDF could not be staged for preview", error)
        }.getOrNull()
    }

    /** Removes staged copies from past sessions that were never imported. */
    private fun deleteStaleFiles(directory: File) {
        val threshold = System.currentTimeMillis() - MAX_AGE_MILLIS
        directory.listFiles()
            ?.filter { it.isFile && it.lastModified() < threshold }
            ?.forEach { it.delete() }
    }
}

