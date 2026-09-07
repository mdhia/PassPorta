package org.shadowgrove.passporta.data.importer

import androidx.annotation.ColorInt
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.data.local.entity.PassFieldSection
import org.shadowgrove.passporta.data.local.entity.PassSource

/** A label-value pair, as Apple and Google passes bring along in any number. */
class PassFieldDraft(
    val label: String?,
    val value: String,
    val section: PassFieldSection = PassFieldSection.DEFAULT,
)

/**
 * Intermediate result of an import - not yet tied to database or file.
 *
 * The parsers (`.pkpass`, Wallet JWT, later ML Kit) exclusively produce [PassDraft]s. Only the
 * [PassImporter] decides on id, logo file, fallback color and persistence. This keeps the
 * parsers free of Android and Room dependencies and easy to test.
 */
class PassDraft(
    val folderName: String,
    val title: String,
    val subtitle: String? = null,
    val ownerName: String,
    val identifier: String? = null,
    val barcodeData: String,
    val barcodeType: BarcodeType = BarcodeType.DEFAULT,
    val barcodeAltText: String? = null,

    /** Error correction level of the original code, if the source specifies it. */
    val barcodeEcc: String? = null,

    /** Character set of the barcode payload (pkpass: `messageEncoding`). */
    val barcodeEncoding: String? = null,

    /** Explicitly given background color; `null` = derive from the logo. */
    @ColorInt val backgroundColor: Int? = null,

    /** Raw bytes of the logo (PNG/JPEG), stored locally by the importer. */
    val logoImage: ByteArray? = null,

    /** Raw bytes of the hero/strip image. */
    val heroImage: ByteArray? = null,

    /** Additional fields in display order. */
    val fields: List<PassFieldDraft> = emptyList(),

    /** Expiration time in milliseconds; after that, the pass moves to the archive. */
    val expirationDate: Long? = null,

    val location: String? = null,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,

    val source: PassSource,

    /** Stable foreign key (pkpass `serialNumber`, wallet object id) for duplicate checking. */
    val externalId: String? = null,
) {

    override fun toString(): String =
        "PassDraft(folder=$folderName, title=$title, type=$barcodeType, source=$source)"
}
