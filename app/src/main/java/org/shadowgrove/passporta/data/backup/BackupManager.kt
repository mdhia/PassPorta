package org.shadowgrove.passporta.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.shadowgrove.passporta.data.importer.ImportJson
import org.shadowgrove.passporta.data.importer.PassAssetStore
import org.shadowgrove.passporta.data.importer.PassImportException
import org.shadowgrove.passporta.data.importer.pkpass.PkPassArchive
import org.shadowgrove.passporta.data.importer.pkpass.PkPassImport
import org.shadowgrove.passporta.data.importer.pkpass.PkPassParser
import org.shadowgrove.passporta.data.local.entity.PassBarcodeEntity
import org.shadowgrove.passporta.data.local.entity.PassEntity
import org.shadowgrove.passporta.data.local.entity.PassFieldEntity
import org.shadowgrove.passporta.data.local.model.PassWithFields
import org.shadowgrove.passporta.data.repository.PassRepository
import org.shadowgrove.passporta.data.security.BackupCrypto
import org.shadowgrove.passporta.data.security.BackupPasswordStore
import org.shadowgrove.passporta.data.settings.SettingsStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Why a backup export or import could not be completed. */
enum class BackupFailure {
    /** The destination file could not be opened or written to. */
    WRITE_FAILED,

    /** The source file could not be read. */
    READ_FAILED,

    /** The file is not a (complete) PassPorta backup. */
    INVALID_FILE,

    /** The backup is encrypted and no password is available. */
    PASSWORD_REQUIRED,

    /** The password is wrong or the encrypted backup was modified. */
    INVALID_PASSWORD,

    /**
     * A backup password is configured but can't be decrypted on this device. Exports fail
     * closed instead of silently writing an unencrypted backup.
     */
    PASSWORD_UNAVAILABLE,
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
 * Exports and imports the entire local dataset as one `.zip` archive:
 * `settings.json` plus one standard PKPASS file per pass under `passes/` (see [PkPassExporter]).
 *
 * If a backup password is configured, the whole archive is wrapped in [BackupCrypto]'s
 * authenticated streaming encryption. Older archives with a `backup.json` manifest stay
 * importable.
 *
 * Everything is streamed - the archive is never held in memory as a whole, so backups with many
 * original documents don't exhaust the heap. Imports are validated completely before the first
 * pass is written, so a damaged archive never leaves a half-restored state behind.
 */
class BackupManager(
    private val context: Context,
    private val repository: PassRepository,
    private val assetStore: PassAssetStore,
    private val settingsStore: SettingsStore,
    private val passwordStore: BackupPasswordStore,
) {

    private val pkPassExporter = PkPassExporter(assetStore)
    private val pkPassParser = PkPassParser(PkPassArchive.Limits.BACKUP)

    /** Writes a full backup to [destination] (typically chosen via `CreateDocument`). */
    suspend fun export(destination: Uri): BackupResult = withContext(Dispatchers.IO) {
        var result: BackupResult? = null
        try {
            exportTo(destination).also { result = it }
        } finally {
            // Don't leave an empty or truncated file behind that looks like a valid backup.
            if (result !is BackupResult.ExportSuccess) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }
            }
        }
    }

    /** Exports a uniquely named automatic backup and keeps only the newest [rollingBackupCount]. */
    suspend fun exportAutomatic(folderUri: Uri, rollingBackupCount: Int): BackupResult =
        withContext(Dispatchers.IO) {
            val folder = runCatching { DocumentFile.fromTreeUri(context, folderUri) }.getOrNull()
                ?: return@withContext BackupResult.Failure(BackupFailure.WRITE_FAILED)
            val target = runCatching { folder.createFile(MIME_TYPE_ZIP, buildAutomaticFileName()) }
                .getOrNull()
                ?: return@withContext BackupResult.Failure(BackupFailure.WRITE_FAILED)

            var result: BackupResult? = null
            try {
                result = exportTo(target.uri)
            } finally {
                if (result !is BackupResult.ExportSuccess) runCatching { target.delete() }
            }

            runCatching { rotateAutomaticBackups(folder, rollingBackupCount) }
                .onFailure { Log.w(TAG, "Automatic backup rotation failed", it) }
            result
        }

    /**
     * Restores a PKPASS backup or an older `backup.json` archive from [source].
     *
     * @param password password for an encrypted backup; if `null`, the stored backup password
     *   is tried. The caller keeps ownership of the array.
     */
    suspend fun import(source: Uri, password: CharArray? = null): BackupResult =
        withContext(Dispatchers.IO) {
            deleteStaleTempFiles()
            var archiveFile: File? = null
            try {
                val file = File.createTempFile(TEMP_FILE_PREFIX, ".zip", context.cacheDir)
                archiveFile = file
                copyPlainArchive(source, password, file)?.let { failure -> return@withContext failure }
                ZipFile(file).use { zip -> restore(zip) }
            } catch (_: BackupCrypto.InvalidBackupPasswordException) {
                BackupResult.Failure(BackupFailure.INVALID_PASSWORD)
            } catch (_: BackupCrypto.InvalidBackupException) {
                BackupResult.Failure(BackupFailure.INVALID_FILE)
            } catch (_: InvalidArchiveException) {
                BackupResult.Failure(BackupFailure.INVALID_FILE)
            } catch (_: ZipException) {
                BackupResult.Failure(BackupFailure.INVALID_FILE)
            } catch (_: PassImportException) {
                BackupResult.Failure(BackupFailure.INVALID_FILE)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Import failed", error)
                BackupResult.Failure(BackupFailure.READ_FAILED)
            } finally {
                archiveFile?.delete()
            }
        }

    // --- Export ---------------------------------------------------------------------------

    private suspend fun exportTo(destination: Uri): BackupResult {
        val password = when (val stored = passwordStore.load()) {
            BackupPasswordStore.StoredPassword.None -> null
            is BackupPasswordStore.StoredPassword.Available -> stored.chars
            BackupPasswordStore.StoredPassword.Unavailable ->
                return BackupResult.Failure(BackupFailure.PASSWORD_UNAVAILABLE)
        }
        return try {
            val passes = repository.getAllWithFields()
            openOutput(destination)?.use { output ->
                val sink = output.buffered(COPY_BUFFER_BYTES).let { buffered ->
                    if (password != null) BackupCrypto.encryptingStream(buffered, password) else buffered
                }
                ZipOutputStream(sink).use { zip -> writeArchive(zip, passes) }
            } ?: return BackupResult.Failure(BackupFailure.WRITE_FAILED)
            BackupResult.ExportSuccess(passes.size)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Export failed", error)
            BackupResult.Failure(BackupFailure.WRITE_FAILED)
        } finally {
            password?.fill('\u0000')
        }
    }

    private fun writeArchive(zip: ZipOutputStream, passes: List<PassWithFields>) {
        zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
        zip.write(
            ImportJson.encodeToString(BackupSettings.serializer(), settingsStore.current.toBackupSettings())
                .toByteArray(Charsets.UTF_8),
        )
        zip.closeEntry()

        val usedNames = HashSet<String>()
        passes.forEach { pass ->
            zip.putNextEntry(
                ZipEntry(BackupEntryName.next(pass.pass.title, pass.pass.id, usedNames)),
            )
            pkPassExporter.write(pass, zip)
            zip.closeEntry()
        }
    }

    /** Truncating write mode, so overwriting a larger existing file leaves no trailing bytes. */
    private fun openOutput(uri: Uri): OutputStream? = try {
        context.contentResolver.openOutputStream(uri, "wt")
    } catch (_: FileNotFoundException) {
        context.contentResolver.openOutputStream(uri, "w")
    } catch (_: IllegalArgumentException) {
        context.contentResolver.openOutputStream(uri, "w")
    }

    private fun rotateAutomaticBackups(folder: DocumentFile, rollingBackupCount: Int) {
        val keep = rollingBackupCount.coerceIn(1, 7)
        folder.listFiles()
            .filter { file ->
                val name = file.name.orEmpty()
                !file.isDirectory && name.startsWith(AUTOMATIC_BACKUP_PREFIX) && name.endsWith(".zip")
            }
            .sortedWith(
                compareByDescending<DocumentFile> { it.lastModified() }
                    .thenByDescending { it.name.orEmpty() },
            )
            .drop(keep)
            .forEach { it.delete() }
    }

    private fun buildAutomaticFileName(): String {
        val timestamp = SimpleDateFormat(AUTOMATIC_BACKUP_DATE_FORMAT, Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
        return "$AUTOMATIC_BACKUP_PREFIX$timestamp-${UUID.randomUUID()}.zip"
    }

    // --- Import ---------------------------------------------------------------------------

    /**
     * Copies the (decrypted) archive from [source] to [target].
     *
     * @return a failure if the backup is encrypted and no password is available, else `null`.
     */
    private fun copyPlainArchive(source: Uri, password: CharArray?, target: File): BackupResult? {
        val input = context.contentResolver.openInputStream(source)
            ?: return BackupResult.Failure(BackupFailure.READ_FAILED)
        input.buffered(COPY_BUFFER_BYTES).use { buffered ->
            buffered.mark(BackupCrypto.MAGIC_BYTES)
            val prefix = ByteArray(BackupCrypto.MAGIC_BYTES)
            val prefixLength = buffered.readFully(prefix)
            buffered.reset()

            if (prefixLength < prefix.size || !BackupCrypto.hasMagic(prefix)) {
                copyLimited(buffered, target)
                return null
            }
            if (password != null) {
                copyLimited(BackupCrypto.decryptingStream(buffered, password), target)
                return null
            }
            val stored = passwordStore.load() as? BackupPasswordStore.StoredPassword.Available
                ?: return BackupResult.Failure(BackupFailure.PASSWORD_REQUIRED)
            try {
                copyLimited(BackupCrypto.decryptingStream(buffered, stored.chars), target)
            } finally {
                stored.chars.fill('\u0000')
            }
        }
        return null
    }

    private suspend fun restore(zip: ZipFile): BackupResult {
        zip.getEntry(LEGACY_MANIFEST_ENTRY)?.let { entry ->
            val manifest = runCatching {
                ImportJson.decodeFromString(BackupManifest.serializer(), readEntry(zip, entry).toString(Charsets.UTF_8))
            }.getOrElse { throw InvalidArchiveException("Legacy manifest unreadable", it) }
            return restoreLegacy(zip, manifest)
        }

        val settings = zip.getEntry(SETTINGS_ENTRY)?.let { entry ->
            runCatching {
                ImportJson.decodeFromString(BackupSettings.serializer(), readEntry(zip, entry).toString(Charsets.UTF_8))
            }.onFailure { Log.w(TAG, "Backup settings unreadable - skipped", it) }.getOrNull()
        }
        val passEntries = zip.entries().toList().filter { entry ->
            !entry.isDirectory && entry.name.startsWith(PASSES_PREFIX) && entry.name.endsWith(PKPASS_SUFFIX)
        }
        if (passEntries.isEmpty() && zip.getEntry(SETTINGS_ENTRY) == null) {
            throw InvalidArchiveException("Neither passes nor settings found")
        }
        if (passEntries.size > MAX_PASSES) throw InvalidArchiveException("Too many passes")

        // Validate everything first: nothing is written if any pass is unreadable.
        passEntries.forEach { entry -> parsePass(zip, entry) }

        var updatedCount = 0
        passEntries.forEach { entry ->
            if (restorePkPass(parsePass(zip, entry))) updatedCount++
        }
        settings?.let { settingsStore.restore(it) }
        return BackupResult.ImportSuccess(passEntries.size - updatedCount, updatedCount)
    }

    private fun parsePass(zip: ZipFile, entry: ZipEntry): PkPassImport =
        zip.getInputStream(entry).use { pkPassParser.parse(it) }

    /** @return `true` if a pass with the same id already existed. */
    private suspend fun restorePkPass(parsed: PkPassImport): Boolean {
        val metadata = parsed.passportaData ?: return restoreForeignPkPass(parsed)

        val id = metadata.id
        val existed = repository.getPass(id) != null
        fun asset(entry: String?): ByteArray? = entry?.let { parsed.assets[it.lowercase()] }

        val logoPath = asset(metadata.logoEntry)?.let { assetStore.restore("logos/$id.png", it) }
        val heroPath = asset(metadata.heroEntry)?.let { assetStore.restore("heroes/$id.png", it) }
        val originalPath = asset(metadata.originalEntry)?.let { bytes ->
            val extension = metadata.originalEntry
                ?.substringAfterLast('.', "")
                ?.filter(Char::isLetterOrDigit)
                ?.take(MAX_EXTENSION_LENGTH)
                ?.ifEmpty { null }
                ?: DEFAULT_EXTENSION
            assetStore.restore("originals/$id.$extension", bytes)
        }
        repository.restore(
            pass = metadata.toPassEntity(logoPath, heroPath, originalPath),
            fields = metadata.toFields(id),
            barcodes = metadata.toBarcodes(id),
        )
        return existed
    }

    /** A PKPASS without PassPorta metadata (e.g. added to the archive by hand). */
    private suspend fun restoreForeignPkPass(parsed: PkPassImport): Boolean {
        val draft = parsed.draft
        val existing = repository.findDuplicate(draft.barcodeData)
        val id = existing?.id ?: UUID.randomUUID().toString()
        val logoPath = draft.logoImage?.let { assetStore.restore("logos/$id.png", it) }
            ?: existing?.logoPath
        val heroPath = draft.heroImage?.let { assetStore.restore("heroes/$id.png", it) }
            ?: existing?.heroImagePath
        repository.saveWithFields(
            pass = PassEntity(
                id = id,
                folderName = draft.folderName,
                isFavorite = existing?.isFavorite == true,
                title = draft.title,
                subtitle = draft.subtitle,
                ownerName = draft.ownerName,
                identifier = draft.identifier,
                barcodeData = draft.barcodeData,
                barcodeType = draft.barcodeType,
                barcodeAltText = draft.barcodeAltText,
                barcodeEcc = draft.barcodeEcc,
                barcodeEncoding = draft.barcodeEncoding,
                backgroundColor = draft.backgroundColor
                    ?: existing?.backgroundColor
                    ?: PassEntity.DEFAULT_BACKGROUND_COLOR,
                logoPath = logoPath,
                heroImagePath = heroPath,
                expirationDate = draft.expirationDate,
                location = draft.location,
                locationLatitude = draft.locationLatitude,
                locationLongitude = draft.locationLongitude,
                source = draft.source,
            ),
            fields = draft.fields.mapIndexed { index, field ->
                PassFieldEntity(
                    passId = id,
                    label = field.label,
                    value = field.value,
                    section = field.section,
                    position = index,
                )
            },
            barcodes = draft.barcodes.mapIndexed { index, barcode ->
                PassBarcodeEntity(
                    passId = id,
                    barcodeData = barcode.data,
                    barcodeType = barcode.type,
                    barcodeAltText = barcode.altText,
                    barcodeEcc = barcode.ecc,
                    barcodeEncoding = barcode.encoding,
                    position = index,
                )
            },
        )
        return existing != null
    }

    /** Restores an archive in the previous JSON format (`backup.json` plus asset files). */
    private suspend fun restoreLegacy(zip: ZipFile, backup: BackupManifest): BackupResult {
        var updatedCount = 0
        backup.passes.forEach { backupPass ->
            if (repository.getPass(backupPass.id) != null) updatedCount++
            repository.restore(
                pass = backupPass.toPassEntity(
                    restoreLegacyAsset(zip, backupPass.logoPath),
                    restoreLegacyAsset(zip, backupPass.heroImagePath),
                    restoreLegacyAsset(zip, backupPass.originalFilePath),
                ),
                fields = backupPass.fields.map { it.toPassFieldEntity(backupPass.id) },
                barcodes = backupPass.barcodes.sortedBy { it.position }.mapIndexed { index, barcode ->
                    barcode.toPassBarcodeEntity(backupPass.id, index)
                },
            )
        }
        backup.settings?.let { settingsStore.restore(it) }
        return BackupResult.ImportSuccess(backup.passes.size - updatedCount, updatedCount)
    }

    private fun restoreLegacyAsset(zip: ZipFile, relativePath: String?): String? {
        val path = relativePath ?: return null
        val entry = zip.getEntry(path) ?: return null
        val bytes = runCatching { readEntry(zip, entry) }
            .onFailure { Log.w(TAG, "Asset $path skipped", it) }
            .getOrNull()
            ?: return null
        return assetStore.restore(path, bytes)
    }

    private fun readEntry(zip: ZipFile, entry: ZipEntry): ByteArray =
        zip.getInputStream(entry).use { input ->
            val output = ByteArrayOutputStream()
            copyLimited(input, output, MAX_ENTRY_BYTES)
            output.toByteArray()
        }

    private fun copyLimited(input: InputStream, target: File) {
        target.outputStream().use { output -> copyLimited(input, output, MAX_ARCHIVE_BYTES) }
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > limit) throw InvalidArchiveException("Backup exceeds the size limit")
            output.write(buffer, 0, read)
        }
    }

    private fun InputStream.readFully(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    /** Removes decrypted archives left behind if the process died during an import. */
    private fun deleteStaleTempFiles() {
        val cutoff = System.currentTimeMillis() - STALE_TEMP_FILE_MILLIS
        context.cacheDir.listFiles { file ->
            file.name.startsWith(TEMP_FILE_PREFIX) && file.lastModified() < cutoff
        }?.forEach { it.delete() }
    }

    private class InvalidArchiveException(message: String, cause: Throwable? = null) :
        IOException(message, cause)

    private companion object {
        const val TAG = "BackupManager"
        const val MIME_TYPE_ZIP = "application/zip"
        const val LEGACY_MANIFEST_ENTRY = "backup.json"
        const val SETTINGS_ENTRY = "settings.json"
        const val PASSES_PREFIX = "passes/"
        const val PKPASS_SUFFIX = ".pkpass"
        const val AUTOMATIC_BACKUP_PREFIX = "passporta-auto-backup-"
        const val AUTOMATIC_BACKUP_DATE_FORMAT = "yyyyMMdd'T'HHmmssSSS'Z'"
        const val TEMP_FILE_PREFIX = "backup-import-"
        const val STALE_TEMP_FILE_MILLIS = 60L * 60 * 1000
        const val MAX_PASSES = 10_000
        const val MAX_EXTENSION_LENGTH = 8
        const val DEFAULT_EXTENSION = "bin"

        /** Largest accepted single archive entry (manifest, settings or legacy asset). */
        const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

        /** Largest accepted (decrypted) archive, so a crafted file can't fill the disk. */
        const val MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024

        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}
