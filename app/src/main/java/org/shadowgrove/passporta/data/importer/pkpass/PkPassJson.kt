package org.shadowgrove.passporta.data.importer.pkpass

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.shadowgrove.passporta.data.local.entity.PassFieldSection

/**
 * Partial mapping of the Apple `pass.json` schema.
 *
 * Only the fields relevant to PassPorta are modeled; everything else is ignored during
 * parsing (see `ImportJson.ignoreUnknownKeys`).
 */
@Serializable
internal data class PkPassJson(
    val formatVersion: Int = 1,
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
) {

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
}

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
