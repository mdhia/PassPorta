package org.shadowgrove.passporta.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import org.shadowgrove.passporta.data.local.entity.PassEntity
import org.shadowgrove.passporta.data.local.entity.PassFieldEntity
import org.shadowgrove.passporta.data.local.entity.PassBarcodeEntity

/**
 * A pass along with its label-value fields.
 *
 * Room cannot enforce ordering with `@Relation`, so [orderedFields] provides the display
 * order.
 */
data class PassWithFields(

    @Embedded
    val pass: PassEntity,

    @Relation(parentColumn = "id", entityColumn = "pass_id")
    val fields: List<PassFieldEntity> = emptyList(),

    @Relation(parentColumn = "id", entityColumn = "pass_id")
    val barcodes: List<PassBarcodeEntity> = emptyList(),
) {

    /** Fields by section and position - exactly as they appear in the detail view. */
    val orderedFields: List<PassFieldEntity>
        get() = fields.sortedWith(compareBy({ it.section.ordinal }, { it.position }))

    /** Barcodes in their display and swipe order. */
    val orderedBarcodes: List<PassBarcodeEntity>
        get() = barcodes.sortedBy { it.position }
}
