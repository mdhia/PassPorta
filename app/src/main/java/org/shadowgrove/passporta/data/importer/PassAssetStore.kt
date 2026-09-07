package org.shadowgrove.passporta.data.importer

import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Storage for imported image files under `Context.filesDir`.
 *
 * Only the relative path (e.g. `logos/<id>.png`) is stored in the database. This keeps the
 * database small, makes backups traceable, and allows the app folder to be moved.
 */
class PassAssetStore(private val rootDir: File) {

    private val logoDir: File get() = File(rootDir, LOGO_DIR)

    /**
     * Saves [bitmap] as PNG for pass [passId].
     *
     * @return relative path to the file or `null` if writing failed.
     */
    fun saveLogo(passId: String, bitmap: Bitmap): String? = savePng(LOGO_DIR, passId, bitmap)

    /** Saves the hero/strip image of a pass. */
    fun saveHeroImage(passId: String, bitmap: Bitmap): String? = savePng(HERO_DIR, passId, bitmap)

    /**
     * Stores the original file of an import unchanged.
     *
     * @param fileName original file name; only the extension is taken from it.
     * @return relative path or `null` if writing failed.
     */
    fun saveOriginal(passId: String, bytes: ByteArray, fileName: String?): String? {
        if (bytes.isEmpty()) return null

        val relativePath = "$ORIGINAL_DIR/$passId.${extensionOf(fileName)}"
        val target = File(rootDir, relativePath)
        return runCatching {
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            relativePath
        }.onFailure { error ->
            Log.w(TAG, "Original for $passId could not be saved", error)
            target.delete()
        }.getOrNull()
    }

    /** Absolute file for a relative path; `null` if missing or outside [rootDir]. */
    fun resolve(relativePath: String?): File? {
        val path = relativePath?.takeIf { it.isNotBlank() } ?: return null
        val file = File(rootDir, path)
        val canonicalRoot = runCatching { rootDir.canonicalPath }.getOrNull() ?: return null
        val canonicalFile = runCatching { file.canonicalPath }.getOrNull() ?: return null
        if (!canonicalFile.startsWith(canonicalRoot)) return null
        return file.takeIf { it.isFile }
    }

    /** Removes a previously saved file. */
    fun delete(relativePath: String?): Boolean = resolve(relativePath)?.delete() == true

    /**
     * Writes [bytes] verbatim to [relativePath] - used when restoring a backup archive, where
     * the relative path (e.g. `logos/<id>.png`) is already known from the backup manifest.
     *
     * @return [relativePath] on success, or `null` if the path is unsafe or writing failed.
     */
    fun restore(relativePath: String, bytes: ByteArray): String? {
        val path = relativePath.takeIf { it.isNotBlank() && !it.contains("..") } ?: return null
        val target = File(rootDir, path)
        val canonicalRoot = runCatching { rootDir.canonicalPath }.getOrNull() ?: return null

        return runCatching {
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            require(target.canonicalPath.startsWith(canonicalRoot)) { "Path escapes the asset root" }
            path
        }.onFailure { error ->
            Log.w(TAG, "Could not restore $path", error)
            target.delete()
        }.getOrNull()
    }

    /** Deletes all files of a pass - logo, hero image and original document. */
    fun deleteAllFor(vararg relativePaths: String?) {
        relativePaths.forEach { delete(it) }
    }

    /** Deletes orphaned files that no longer belong to any [keepRelativePaths] entry. */
    fun deleteOrphans(keepRelativePaths: Set<String>): Int {
        val files = logoDir.listFiles() ?: return 0
        val keep = keepRelativePaths.map { it.substringAfterLast('/') }.toSet()
        return files.count { it.name !in keep && it.delete() }
    }

    private fun savePng(directory: String, passId: String, bitmap: Bitmap): String? {
        val relativePath = "$directory/$passId.png"
        val target = File(rootDir, relativePath)
        return runCatching {
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)) {
                    "Bitmap could not be encoded as PNG"
                }
            }
            relativePath
        }.onFailure { error ->
            Log.w(TAG, "Image for $passId could not be saved", error)
            target.delete()
        }.getOrNull()
    }

    /**
     * Extension from the file name, reduced to harmless characters.
     *
     * The name comes from an external source and must not influence the target path.
     */
    private fun extensionOf(fileName: String?): String = fileName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        ?.filter { it.isLetterOrDigit() }
        ?.take(MAX_EXTENSION_LENGTH)
        ?.takeIf { it.isNotEmpty() }
        ?: DEFAULT_EXTENSION

    private companion object {
        const val TAG = "PassAssetStore"
        const val LOGO_DIR = "logos"
        const val HERO_DIR = "heroes"
        const val ORIGINAL_DIR = "originals"
        const val PNG_QUALITY = 100
        const val MAX_EXTENSION_LENGTH = 8
        const val DEFAULT_EXTENSION = "bin"
    }
}
