package org.shadowgrove.passporta.data.local

import androidx.room.TypeConverter
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.data.local.entity.PassFieldSection
import org.shadowgrove.passporta.data.local.entity.PassSource

/**
 * Room TypeConverters for the data model's enums.
 *
 * Deliberately stores the stable `storageKey` (not `ordinal`).
 */
class PassConverters {

    @TypeConverter
    fun fromBarcodeType(value: BarcodeType): String = value.storageKey

    @TypeConverter
    fun toBarcodeType(value: String?): BarcodeType = BarcodeType.fromKey(value)

    @TypeConverter
    fun fromPassSource(value: PassSource): String = value.storageKey

    @TypeConverter
    fun toPassSource(value: String?): PassSource = PassSource.fromKey(value)

    @TypeConverter
    fun fromPassFieldSection(value: PassFieldSection): String = value.storageKey

    @TypeConverter
    fun toPassFieldSection(value: String?): PassFieldSection = PassFieldSection.fromKey(value)
}
