package org.shadowgrove.passporta.data.importer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Target storage for photos taken with the camera app.
 *
 * PassPorta deliberately uses the system camera via `ACTION_IMAGE_CAPTURE` instead of its own
 * preview: as long as the app does **not** declare the `CAMERA` permission, Android doesn't
 * require it for this intent either - the app therefore stays fully permission-free. (If the
 * permission were in the manifest, it would suddenly have to be requested at runtime.)
 */
object CameraCapture {

    private const val TAG = "CameraCapture"
    private const val DIRECTORY = "camera"
    private const val FILE_PREFIX = "capture-"
    private const val FILE_SUFFIX = ".jpg"

    /** Captures older than this span are cleaned up on the next startup. */
    private const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000

    /**
     * Creates an empty target file and returns its content URI.
     *
     * The camera app needs a `content://` address: a direct file path would trigger a
     * `FileUriExposedException` from Android 7 onward.
     */
    fun createTargetUri(context: Context): Uri? = runCatching {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        deleteStaleFiles(directory)

        val target = File(directory, "$FILE_PREFIX${System.currentTimeMillis()}$FILE_SUFFIX")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }.onFailure { error ->
        Log.w(TAG, "Target file for the camera could not be created", error)
    }.getOrNull()

    /**
     * Checks whether the camera app actually left behind image data.
     *
     * Several manufacturer cameras report success but write nothing to the given file - e.g.
     * because they ignore `EXTRA_OUTPUT` and only want to return a thumbnail. Without this
     * check, the user would end up with an empty form without knowing why.
     */
    fun hasContent(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize > 0 } == true
    }.getOrDefault(false)

    /**
     * Removes captures from past sessions.
     *
     * Aborted captures and already saved passes would otherwise permanently take up space in
     * the cache.
     */
    private fun deleteStaleFiles(directory: File) {
        val threshold = System.currentTimeMillis() - MAX_AGE_MILLIS
        directory.listFiles()
            ?.filter { it.isFile && it.lastModified() < threshold }
            ?.forEach { it.delete() }
    }
}
