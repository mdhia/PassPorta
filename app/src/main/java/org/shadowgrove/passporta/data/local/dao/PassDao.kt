package org.shadowgrove.passporta.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.shadowgrove.passporta.data.local.entity.PassEntity
import org.shadowgrove.passporta.data.local.entity.PassFieldEntity
import org.shadowgrove.passporta.data.local.entity.PassBarcodeEntity
import org.shadowgrove.passporta.data.local.model.FolderSummary
import org.shadowgrove.passporta.data.local.model.PassWithFields

/**
 * Access to the pass table. All observing queries return [Flow]s, so the Compose UI updates
 * automatically.
 */
@Dao
interface PassDao {

    @Query(
        """
        SELECT * FROM passes
        ORDER BY folder_name COLLATE NOCASE ASC, title COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<PassEntity>>

    @Query(
        """
        SELECT * FROM passes
        WHERE folder_name = :folderName
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    fun observeByFolder(folderName: String): Flow<List<PassEntity>>

    @Query(
        """
        SELECT folder_name AS folder_name,
               COUNT(*) AS pass_count,
               MAX(updated_at) AS last_updated_at
        FROM passes
        GROUP BY folder_name
        ORDER BY folder_name COLLATE NOCASE ASC
        """
    )
    fun observeFolders(): Flow<List<FolderSummary>>

    @Query("SELECT * FROM passes WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PassEntity?>

    @Query(
        """
        SELECT * FROM passes
        WHERE title LIKE '%' || :query || '%' COLLATE NOCASE
           OR subtitle LIKE '%' || :query || '%' COLLATE NOCASE
           OR owner_name LIKE '%' || :query || '%' COLLATE NOCASE
           OR identifier LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY title COLLATE NOCASE ASC
        """
    )
    fun search(query: String): Flow<List<PassEntity>>

    @Query("SELECT * FROM passes WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PassEntity?

    /** Used for duplicate detection during import (phase 2 / phase 5). */
    @Query(
        """
        SELECT * FROM passes
        WHERE barcode_data = :barcodeData
           OR id IN (
               SELECT pass_id FROM pass_barcodes WHERE barcode_data = :barcodeData
           )
        LIMIT 1
        """,
    )
    suspend fun findByBarcodeData(barcodeData: String): PassEntity?

    @Query("SELECT COUNT(*) FROM passes")
    suspend fun count(): Int

    /**
     * All passes with their fields, in one shot - basis of a full data export/backup.
     *
     * `@Transaction` again ensures passes and fields reflect the same database state, and a
     * plain `suspend` (no `Flow`) is enough since an export just needs a single snapshot.
     */
    @Transaction
    @Query("SELECT * FROM passes")
    suspend fun getAllWithFields(): List<PassWithFields>

    @Upsert
    suspend fun upsert(pass: PassEntity)

    @Upsert
    suspend fun upsertAll(passes: List<PassEntity>)

    @Query("UPDATE passes SET folder_name = :folderName, updated_at = :updatedAt WHERE id = :id")
    suspend fun moveToFolder(id: String, folderName: String, updatedAt: Long): Int

    /**
     * Sets the favorite marker.
     *
     * Deliberately a targeted `UPDATE` instead of an `upsert` of the whole row: this way the
     * star can be toggled from any view without concurrently opened forms writing back stale
     * values.
     */
    @Query("UPDATE passes SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean): Int

    @Delete
    suspend fun delete(pass: PassEntity)

    @Query("DELETE FROM passes WHERE id = :id")
    suspend fun deleteById(id: String): Int

    // --- Additional fields (label/value) ---

    /**
     * Pass along with its fields. `@Transaction` is required with `@Relation` so the pass and
     * its fields see the same database state.
     */
    @Transaction
    @Query("SELECT * FROM passes WHERE id = :id LIMIT 1")
    fun observeWithFields(id: String): Flow<PassWithFields?>

    @Query("SELECT * FROM pass_fields WHERE pass_id = :passId ORDER BY position ASC")
    suspend fun fieldsOf(passId: String): List<PassFieldEntity>

    @Query("DELETE FROM pass_fields WHERE pass_id = :passId")
    suspend fun deleteFields(passId: String)

    @Insert
    suspend fun insertFields(fields: List<PassFieldEntity>)

    /**
     * Replaces all fields of a pass.
     *
     * Deletion and insertion run in one transaction: an interruption in between would
     * otherwise leave the pass without fields.
     */
    @Transaction
    suspend fun replaceFields(passId: String, fields: List<PassFieldEntity>) {
        deleteFields(passId)
        if (fields.isNotEmpty()) insertFields(fields)
    }

    @Query("SELECT * FROM pass_barcodes WHERE pass_id = :passId ORDER BY position ASC")
    suspend fun barcodesOf(passId: String): List<PassBarcodeEntity>

    @Query("DELETE FROM pass_barcodes WHERE pass_id = :passId")
    suspend fun deleteBarcodes(passId: String)

    @Insert
    suspend fun insertBarcodes(barcodes: List<PassBarcodeEntity>)

    /** Replaces all barcodes of a pass in one transaction. */
    @Transaction
    suspend fun replaceBarcodes(passId: String, barcodes: List<PassBarcodeEntity>) {
        deleteBarcodes(passId)
        if (barcodes.isNotEmpty()) insertBarcodes(barcodes)
    }
}

