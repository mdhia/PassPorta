package org.shadowgrove.passporta.data.local.model

import androidx.room.ColumnInfo

/**
 * Aggregated folder information for the header and the folder selection of the overview.
 */
data class FolderSummary(

    @ColumnInfo(name = "folder_name")
    val folderName: String,

    @ColumnInfo(name = "pass_count")
    val passCount: Int,

    @ColumnInfo(name = "last_updated_at")
    val lastUpdatedAt: Long,
)
