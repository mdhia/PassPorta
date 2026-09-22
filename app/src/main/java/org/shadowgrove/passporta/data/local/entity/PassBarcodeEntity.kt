package org.shadowgrove.passporta.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** One of potentially several barcodes belonging to a pass. */
@Entity(
    tableName = "pass_barcodes",
    foreignKeys = [
        ForeignKey(
            entity = PassEntity::class,
            parentColumns = ["id"],
            childColumns = ["pass_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["pass_id"]),
        Index(value = ["pass_id", "position"], unique = true),
    ],
)
data class PassBarcodeEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "pass_id")
    val passId: String,

    /** Encoded payload, exactly as it should be presented to a scanner. */
    @ColumnInfo(name = "barcode_data")
    val barcodeData: String,

    @ColumnInfo(name = "barcode_type")
    val barcodeType: BarcodeType = BarcodeType.DEFAULT,

    /** Optional text shown below this barcode. */
    @ColumnInfo(name = "barcode_alt_text")
    val barcodeAltText: String? = null,

    @ColumnInfo(name = "barcode_ecc")
    val barcodeEcc: String? = null,

    @ColumnInfo(name = "barcode_encoding")
    val barcodeEncoding: String? = null,

    /** Display and swap order within the pass. */
    @ColumnInfo(name = "position")
    val position: Int = 0,
)

