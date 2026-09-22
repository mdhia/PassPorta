package org.shadowgrove.passporta.data.repository

import kotlinx.coroutines.flow.Flow
import org.shadowgrove.passporta.data.local.dao.PassDao
import org.shadowgrove.passporta.data.local.entity.PassEntity
import org.shadowgrove.passporta.data.local.entity.PassFieldEntity
import org.shadowgrove.passporta.data.local.entity.PassBarcodeEntity
import org.shadowgrove.passporta.data.local.model.FolderSummary
import org.shadowgrove.passporta.data.local.model.PassWithFields

/**
 * The single entry point of the app to pass data. Wraps the DAO so later phases
 * (pkpass import, logo files, ML Kit extraction) can hook in here without touching the UI.
 */
class PassRepository(
    private val passDao: PassDao,
) {

    /** All passes or - if [folderName] is set - only those of the folder. */
    fun observePasses(folderName: String? = null): Flow<List<PassEntity>> =
        if (folderName.isNullOrBlank()) passDao.observeAll() else passDao.observeByFolder(folderName)

    fun observeFolders(): Flow<List<FolderSummary>> = passDao.observeFolders()

    fun observePass(id: String): Flow<PassEntity?> = passDao.observeById(id)

    /** Pass along with label-value fields - basis of the detail view. */
    fun observePassWithFields(id: String): Flow<PassWithFields?> = passDao.observeWithFields(id)

    fun search(query: String): Flow<List<PassEntity>> = passDao.search(query.trim())

    suspend fun getPass(id: String): PassEntity? = passDao.findById(id)

    suspend fun getFields(passId: String): List<PassFieldEntity> = passDao.fieldsOf(passId)

    suspend fun getBarcodes(passId: String): List<PassBarcodeEntity> = passDao.barcodesOf(passId)

    /** Finds an already saved pass with identical barcode data (duplicate protection). */
    suspend fun findDuplicate(barcodeData: String): PassEntity? =
        passDao.findByBarcodeData(barcodeData)

    suspend fun count(): Int = passDao.count()

    /** All passes with their fields in one snapshot - basis of a full data export. */
    suspend fun getAllWithFields(): List<PassWithFields> = passDao.getAllWithFields()

    /**
     * Creates or updates a pass and sets [PassEntity.updatedAt] in the process.
     * @return the id of the saved pass.
     */
    suspend fun save(pass: PassEntity, now: Long = System.currentTimeMillis()): String {
        val normalized = pass.copy(
            folderName = pass.folderName.trim().ifBlank { PassEntity.DEFAULT_FOLDER },
            title = pass.title.trim(),
            subtitle = pass.subtitle?.trim()?.takeIf { it.isNotEmpty() },
            ownerName = pass.ownerName.trim(),
            identifier = pass.identifier?.trim()?.takeIf { it.isNotEmpty() },
            location = pass.location?.trim()?.takeIf { it.isNotEmpty() },
            backgroundColor = pass.backgroundColor or ALPHA_OPAQUE,
            updatedAt = now,
        )
        passDao.upsert(normalized)
        return normalized.id
    }

    /**
     * Saves a pass together with its additional fields.
     *
     * The fields fully replace the previous ones; empty values are discarded and the
     * positions are reassigned so the display order is always gapless.
     */
    suspend fun saveWithFields(
        pass: PassEntity,
        fields: List<PassFieldEntity>,
        barcodes: List<PassBarcodeEntity> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): String {
        val id = save(pass, now)
        val normalized = fields
            .mapNotNull { field ->
                val value = field.value.trim()
                if (value.isEmpty()) {
                    null
                } else {
                    field.copy(
                        passId = id,
                        label = field.label?.trim()?.takeIf { it.isNotEmpty() },
                        value = value,
                    )
                }
            }
            .mapIndexed { index, field -> field.copy(position = index) }

        passDao.replaceFields(id, normalized)
        val normalizedBarcodes = barcodes.ifEmpty {
            listOf(
                PassBarcodeEntity(
                    passId = id,
                    barcodeData = pass.barcodeData,
                    barcodeType = pass.barcodeType,
                    barcodeAltText = pass.barcodeAltText,
                    barcodeEcc = pass.barcodeEcc,
                    barcodeEncoding = pass.barcodeEncoding,
                    position = 0,
                ),
            )
        }
            .mapIndexed { index, barcode ->
                barcode.copy(
                    passId = id,
                    position = index,
                )
            }
        passDao.replaceBarcodes(id, normalizedBarcodes)
        return id
    }

    suspend fun saveAll(passes: List<PassEntity>, now: Long = System.currentTimeMillis()) {
        passDao.upsertAll(passes.map { it.copy(updatedAt = now) })
    }

    suspend fun moveToFolder(
        id: String,
        folderName: String,
        now: Long = System.currentTimeMillis(),
    ): Boolean = passDao.moveToFolder(
        id = id,
        folderName = folderName.trim().ifBlank { PassEntity.DEFAULT_FOLDER },
        updatedAt = now,
    ) > 0

    /**
     * Marks a pass as favorite or removes the mark.
     *
     * [PassEntity.updatedAt] is deliberately left untouched: a star is not a content change of
     * the pass, and the folder overview derives its "last changed" value from that field.
     */
    suspend fun setFavorite(id: String, favorite: Boolean): Boolean =
        passDao.setFavorite(id, favorite) > 0

    suspend fun delete(pass: PassEntity) = passDao.delete(pass)

    suspend fun deleteById(id: String): Boolean = passDao.deleteById(id) > 0

    /**
     * Writes a pass and its fields back exactly as given - used when restoring a backup.
     *
     * Deliberately without the normalization and `updatedAt` handling of [saveWithFields]: a
     * restored pass should keep its original timestamps and values byte for byte, not look like
     * it was just edited by the user.
     */
    suspend fun restore(
        pass: PassEntity,
        fields: List<PassFieldEntity>,
        barcodes: List<PassBarcodeEntity> = emptyList(),
    ) {
        passDao.upsert(pass)
        passDao.replaceFields(pass.id, fields.mapIndexed { index, field -> field.copy(passId = pass.id, position = index) })
        val normalizedBarcodes = barcodes.ifEmpty {
            listOf(
                PassBarcodeEntity(
                    passId = pass.id,
                    barcodeData = pass.barcodeData,
                    barcodeType = pass.barcodeType,
                    barcodeAltText = pass.barcodeAltText,
                    barcodeEcc = pass.barcodeEcc,
                    barcodeEncoding = pass.barcodeEncoding,
                    position = 0,
                ),
            )
        }
            .mapIndexed { index, barcode ->
                barcode.copy(
                    passId = pass.id,
                    position = index,
                )
            }
        passDao.replaceBarcodes(pass.id, normalizedBarcodes)
    }

    private companion object {
        /** Forces an opaque background so the contrast calculation stays valid. */
        const val ALPHA_OPAQUE = 0xFF000000.toInt()
    }
}
