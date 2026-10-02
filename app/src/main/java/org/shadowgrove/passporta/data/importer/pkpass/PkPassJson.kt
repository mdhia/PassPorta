package org.shadowgrove.passporta.data.importer.pkpass

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.shadowgrove.passporta.data.importer.ImportJson
import org.shadowgrove.passporta.data.local.entity.PassFieldSection

/**
 * Partial mapping of the Apple `pass.json` schema.
 *
 * Only the fields relevant to PassPorta are modeled; everything else is ignored during
 * parsing (see `ImportJson.ignoreUnknownKeys`).
 */
@Serializable
internal data class PkPassJson(
    // Default values are not encoded by kotlinx.serialization; the key is mandatory for readers.
    @EncodeDefault
    val formatVersion: Int = 1,
    val passTypeIdentifier: String? = null,
    val teamIdentifier: String? = null,
    val serialNumber: String? = null,
    val organizationName: String? = null,
    val description: String? = null,
    val logoText: String? = null,

    val foregroundColor: String? = null,
    val backgroundColor: String? = null,
    val labelColor: String? = null,

    /** ISO 8601; after this Apple Wallet shows the pass as expired. */
    val expirationDate: String? = null,

    /** Withdrawn by the issuer - the pass is therefore immediately invalid. */
    val voided: Boolean = false,

    val relevantDate: String? = null,

    /** Locations where the pass is relevant (venue, branch, ...). */
    val locations: List<PkLocation> = emptyList(),

    /** Before iOS 9: exactly one barcode. */
    val barcode: PkBarcode? = null,

    /** From iOS 9 onward: a priority-sorted list. */
    val barcodes: List<PkBarcode> = emptyList(),

    val boardingPass: PkPassStructure? = null,
    val coupon: PkPassStructure? = null,
    val eventTicket: PkPassStructure? = null,
    val generic: PkPassStructure? = null,
    val storeCard: PkPassStructure? = null,

    /**
     * Free-form issuer dictionary (Apple spec). Deliberately untyped: other issuers put
     * arbitrary JSON here, which must never break parsing. PassPorta's own round-trip data lives
     * below the namespaced [PASSPORTA_USER_INFO_KEY], see [passportaData].
     */
    val userInfo: JsonElement? = null,
) {

    /** PassPorta round-trip metadata; `null` if absent or not in the expected shape. */
    val passportaData: PkPassPortaData?
        get() = (userInfo as? JsonObject)?.get(PASSPORTA_USER_INFO_KEY)?.let { element ->
            runCatching {
                ImportJson.decodeFromJsonElement(PkPassPortaData.serializer(), element)
            }.getOrNull()
        }

    /** The style actually used, along with its fields. */
    val style: PkPassStyle?
        get() = when {
            boardingPass != null -> PkPassStyle.BOARDING_PASS
            eventTicket != null -> PkPassStyle.EVENT_TICKET
            storeCard != null -> PkPassStyle.STORE_CARD
            coupon != null -> PkPassStyle.COUPON
            generic != null -> PkPassStyle.GENERIC
            else -> null
        }

    val structure: PkPassStructure?
        get() = boardingPass ?: eventTicket ?: storeCard ?: coupon ?: generic

    /** Preferred barcode: first entry from [barcodes], otherwise the legacy field. */
    val preferredBarcode: PkBarcode?
        get() = barcodes.firstOrNull { !it.message.isNullOrBlank() } ?: barcode

    companion object {
        const val PASSPORTA_USER_INFO_KEY = "passporta"
    }
}

/**
 * Complete PassPorta pass data embedded under `userInfo.passporta`, so a backup restores
 * everything the standard `pass.json` keys can't express (folder, favorite, all barcode types
 * and metadata, field sections, timestamps, ...). Other PKPASS readers ignore it.
 */
@Serializable
data class PkPassPortaData(
    val id: String,
    val folderName: String,
    val isFavorite: Boolean = false,
    val title: String,
    val subtitle: String? = null,
    val ownerName: String,
    val identifier: String? = null,
    val barcodeData: String,
    val barcodeType: String,
    val barcodeAltText: String? = null,
    val barcodeEcc: String? = null,
    val barcodeEncoding: String? = null,
    val barcodes: List<PkPassPortaBarcode> = emptyList(),
    val backgroundColor: Int,
    val iconKey: String? = null,
    val originalFileName: String? = null,
    val originalMimeType: String? = null,
    val expirationDate: Long? = null,
    val startDate: Long? = null,
    val location: String? = null,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val fields: List<PkPassPortaField> = emptyList(),
    val logoEntry: String? = null,
    val heroEntry: String? = null,
    val originalEntry: String? = null,
)

@Serializable
data class PkPassPortaBarcode(
    val barcodeData: String,
    val barcodeType: String,
    val barcodeAltText: String? = null,
    val barcodeEcc: String? = null,
    val barcodeEncoding: String? = null,
    val position: Int = 0,
)

@Serializable
data class PkPassPortaField(
    val label: String? = null,
    val value: String,
    val section: String,
    val position: Int = 0,
)

@Serializable
internal data class PkBarcode(
    val format: String? = null,
    val message: String? = null,
    val messageEncoding: String? = null,
    val altText: String? = null,
)

@Serializable
internal data class PkLocation(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val relevantText: String? = null,
)

@Serializable
internal data class PkPassStructure(
    val headerFields: List<PkField> = emptyList(),
    val primaryFields: List<PkField> = emptyList(),
    val secondaryFields: List<PkField> = emptyList(),
    val auxiliaryFields: List<PkField> = emptyList(),
    val backFields: List<PkField> = emptyList(),
    val transitType: String? = null,
) {

    /** All fields in order of significance. */
    val allFields: List<PkField>
        get() = headerFields + primaryFields + secondaryFields + auxiliaryFields + backFields

    /** All fields along with their section - basis of the field list in the detail view. */
    val sectionedFields: List<Pair<PassFieldSection, PkField>>
        get() = headerFields.map { PassFieldSection.HEADER to it } +
            primaryFields.map { PassFieldSection.PRIMARY to it } +
            secondaryFields.map { PassFieldSection.SECONDARY to it } +
            auxiliaryFields.map { PassFieldSection.AUXILIARY to it } +
            backFields.map { PassFieldSection.BACK to it }
}

@Serializable
internal data class PkField(
    val key: String = "",
    val label: String? = null,

    /** Can be text, number or ISO date - hence generic. */
    val value: JsonElement? = null,

    @SerialName("attributedValue")
    val attributedValue: JsonElement? = null,
)

/** Pass styles per Apple specification. */
internal enum class PkPassStyle(val folderName: String) {
    BOARDING_PASS("Bordkarten"),
    EVENT_TICKET("Tickets"),
    STORE_CARD("Kundenkarten"),
    COUPON("Gutscheine"),
    GENERIC("Allgemein"),
}
