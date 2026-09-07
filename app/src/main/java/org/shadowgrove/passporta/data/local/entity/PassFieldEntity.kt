package org.shadowgrove.passporta.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * An additional field of a pass as a label-value pair (e.g. "Seat" / "14A").
 *
 * Apple and Google passes largely consist of such freely defined fields. They deliberately live
 * in their own table instead of a JSON column: this keeps them individually editable and
 * sortable, without having to re-serialize the entire pass.
 *
 * When the pass is deleted, the database cleans up the fields itself via `CASCADE`.
 */
@Entity(
    tableName = "pass_fields",
    foreignKeys = [
        ForeignKey(
            entity = PassEntity::class,
            parentColumns = ["id"],
            childColumns = ["pass_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["pass_id"])],
)
data class PassFieldEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "pass_id")
    val passId: String,

    /** Heading of the line; may be missing if the source only provides a value. */
    @ColumnInfo(name = "label")
    val label: String? = null,

    @ColumnInfo(name = "value")
    val value: String,

    @ColumnInfo(name = "section")
    val section: PassFieldSection = PassFieldSection.DEFAULT,

    /** Display order within the pass. */
    @ColumnInfo(name = "position")
    val position: Int = 0,
)
