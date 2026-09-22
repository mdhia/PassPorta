package org.shadowgrove.passporta.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.shadowgrove.passporta.data.importer.ImportJson
import org.shadowgrove.passporta.data.importer.PassAssetStore
import org.shadowgrove.passporta.data.repository.PassRepository
import org.shadowgrove.passporta.data.settings.SettingsStore
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Why a backup export or import could not be completed. */
enum class BackupFailure {
    /** The destination/source file could not be opened or written to. */
    WRITE_FAILED,

    /** The source file could not be read. */
    READ_FAILED,

    /** The archive doesn't contain a readable `backup.json` manifest. */
    INVALID_FILE,
}

/** Result of a backup export or import. */
sealed interface BackupResult {

    /** @param passCount number of passes written to the archive. */
    data class ExportSuccess(val passCount: Int) : BackupResult

    /**
     * @param importedCount number of passes that didn't exist locally before.
     * @param updatedCount number of passes that replaced an already existing one (same id).
     */
    data class ImportSuccess(val importedCount: Int, val updatedCount: Int) : BackupResult

    data class Failure(val reason: BackupFailure) : BackupResult
}

/**
 * Exports and imports the entire local dataset - passes, their logos/hero images/original
 * documents, and the handful of app settings - as a single `.zip` archive.
 *
 * Deliberately not a raw copy of the Room database file: SQLite can be mid-transaction or
 * WAL-journaled while the app runs, so a byte-for-byte copy risks a corrupt restore. A JSON
 * manifest (`backup.json`) plus the referenced asset files is portable, inspectable by hand and
 * independent of Room's on-disk format, so an app update that changes the schema can still read
 * an old backup.
 */
class BackupManager(
    private val context: Context,
    private val repository: PassRepository,
    private val assetStore: PassAssetStore,
    private val settingsStore: SettingsStore,
) {

    /** Writes a full backup to [destination] (typically chosen via `CreateDocument`). */
    suspend fun export(destination: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val passesWithFields = repository.getAllWithFields()
            val manifest = BackupManifest(
                exportedAt = System.currentTimeMillis(),
                passes = passesWithFields.map { it.toBackupPass() },
                settings = settingsStore.current.toBackupSettings(),
            )

            val output = context.contentResolver.openOutputStream(destination)
                ?: return@withContext BackupResult.Failure(BackupFailure.WRITE_FAILED)

            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(ImportJson.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // Every asset a pass points at - logo, hero image, original document - is
                // stored under its own relative path, so the archive doubles as a plain,
                // browsable folder structure if ever opened by hand.
                passesWithFields.forEach { entry ->
                    writeAsset(zip, entry.pass.logoPath)
                    writeAsset(zip, entry.pass.heroImagePath)
                    writeAsset(zip, entry.pass.originalFilePath)
                }
            }

            BackupResult.ExportSuccess(passesWithFields.size)
        } catch (error: Exception) {
            Log.w(TAG, "Export failed", error)
            BackupResult.Failure(BackupFailure.WRITE_FAILED)
        }
    }

    /** Exports a uniquely named automatic backup and keeps only the newest [rollingBackupCount]. */
    suspend fun exportAutomatic(folderUri: Uri, rollingBackupCount: Int): BackupResult =
        withContext(Dispatchers.IO) {
            val folder = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@withContext BackupResult.Failure(BackupFailure.WRITE_FAILED)
            val fileName = buildAutomaticFileName()
            val target = folder.createFile("application/zip", fileName)
                ?: return@withContext BackupResult.Failure(BackupFailure.WRITE_FAILED)

            val result = export(target.uri)
            if (result !is BackupResult.ExportSuccess) {
                target.delete()
                return@withContext result
            }

            runCatching { rotateAutomaticBackups(folder, rollingBackupCount) }
                .onFailure { Log.w(TAG, "Automatic backup rotation failed", it) }
            result
        }

    /** Restores passes, assets and settings from a previously exported archive at [source]. */
    suspend fun import(source: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: return@withContext BackupResult.Failure(BackupFailure.READ_FAILED)

            var manifest: BackupManifest? = null
            val assets = mutableMapOf<String, ByteArray>()
            var totalBytes = 0L

            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val bytes = zip.readLimited(MAX_ENTRY_BYTES)
                        if (bytes != null) {
                            totalBytes += bytes.size
                            require(totalBytes <= MAX_TOTAL_BYTES) { "Archive exceeds the size limit" }
                            if (entry.name == MANIFEST_ENTRY) {
                                manifest = runCatching {
                                    ImportJson.decodeFromString<BackupManifest>(bytes.toString(Charsets.UTF_8))
                                }.getOrNull()
                            } else {
                                assets[entry.name] = bytes
                            }
                        } else {
                            Log.w(TAG, "Entry ${entry.name} skipped (too large)")
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val backup = manifest ?: return@withContext BackupResult.Failure(BackupFailure.INVALID_FILE)

            var updatedCount = 0
            backup.passes.forEach { backupPass ->
                if (repository.getPass(backupPass.id) != null) updatedCount++

                val logoPath = restoreAsset(backupPass.logoPath, assets)
                val heroPath = restoreAsset(backupPass.heroImagePath, assets)
                val originalPath = restoreAsset(backupPass.originalFilePath, assets)

                repository.restore(
                    pass = backupPass.toPassEntity(logoPath, heroPath, originalPath),
                    fields = backupPass.fields.map { it.toPassFieldEntity(backupPass.id) },
                    barcodes = backupPass.barcodes
                        .sortedBy { it.position }
                        .mapIndexed { index, barcode ->
                            barcode.toPassBarcodeEntity(backupPass.id, index)
                        },
                )
            }

            backup.settings?.let { settingsStore.restore(it) }

            BackupResult.ImportSuccess(
                importedCount = backup.passes.size - updatedCount,
                updatedCount = updatedCount,
            )
        } catch (error: Exception) {
            Log.w(TAG, "Import failed", error)
            BackupResult.Failure(BackupFailure.READ_FAILED)
        }
    }

    private fun writeAsset(zip: ZipOutputStream, relativePath: String?) {
        val file = assetStore.resolve(relativePath) ?: return
        zip.putNextEntry(ZipEntry(relativePath))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /** Writes the asset bytes for [relativePath] back via [PassAssetStore], if present in the archive. */
    private fun restoreAsset(relativePath: String?, assets: Map<String, ByteArray>): String? {
        val path = relativePath ?: return null
        val bytes = assets[path] ?: return null
        return assetStore.restore(path, bytes)
    }

    private fun rotateAutomaticBackups(folder: DocumentFile, rollingBackupCount: Int) {
        val keep = rollingBackupCount.coerceIn(1, 7)
        val automaticBackups = folder.listFiles()
            .filter { file ->
                val name = file.name.orEmpty()
                !file.isDirectory && name.startsWith(AUTOMATIC_BACKUP_PREFIX) && name.endsWith(".zip")
            }
            .sortedWith(
                compareByDescending<DocumentFile> { it.lastModified() }
                    .thenByDescending { it.name.orEmpty() },
            )

        automaticBackups.drop(keep).forEach { it.delete() }
    }

    private fun buildAutomaticFileName(): String {
        val timestamp = SimpleDateFormat(AUTOMATIC_BACKUP_DATE_FORMAT, Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())
        return "$AUTOMATIC_BACKUP_PREFIX$timestamp-${UUID.randomUUID()}.zip"
    }

    /** Reads at most [limit] bytes of the current zip entry; `null` if the entry is larger. */
    private fun ZipInputStream.readLimited(limit: Long): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            if (total > limit) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        const val TAG = "BackupManager"
        const val MANIFEST_ENTRY = "backup.json"
        const val AUTOMATIC_BACKUP_PREFIX = "passporta-auto-backup-"
        const val AUTOMATIC_BACKUP_DATE_FORMAT = "yyyyMMdd'T'HHmmssSSS'Z'"

        /** Largest accepted single asset file (logo, hero image or original document). */
        const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

        /** Upper limit for the entire archive, so a crafted zip can't exhaust memory. */
        const val MAX_TOTAL_BYTES = 512L * 1024 * 1024

        const val COPY_BUFFER_BYTES = 16 * 1024
    }
}


