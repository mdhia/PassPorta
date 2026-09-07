package org.shadowgrove.passporta.data.importer.pkpass

import kotlinx.serialization.json.JsonElement
import org.shadowgrove.passporta.data.importer.ImportFailure
import org.shadowgrove.passporta.data.importer.ImportJson
import org.shadowgrove.passporta.data.importer.PassDraft
import org.shadowgrove.passporta.data.importer.PassFieldDraft
import org.shadowgrove.passporta.data.importer.PassImportException
import org.shadowgrove.passporta.data.importer.asText
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.data.local.entity.PassEntity
import org.shadowgrove.passporta.data.local.entity.PassSource
import org.shadowgrove.passporta.util.ColorParser
import org.shadowgrove.passporta.util.Iso8601
import java.io.InputStream

/** Result of `.pkpass` parsing. */
data class PkPassImport(
    val draft: PassDraft,

    /** `true` if all checksums from `manifest.json` match. */
    val integrityVerified: Boolean,
)

/**
 * Reads a `.pkpass` archive (Apple Wallet) fully offline and translates it into a [PassDraft].
 *
 * Deliberately without Android dependencies, so the entire mapping logic can be verified in
 * JVM unit tests.
 */
class PkPassParser {

    /**
     * @param input data stream of the archive; is read but not closed.
     * @param preferredLanguage preferred language for `pass.strings` (e.g. `"de"`).
     * @throws PassImportException if the archive is unusable or contains no barcode.
     */
    fun parse(input: InputStream, preferredLanguage: String? = null): PkPassImport {
        val archive = runCatching { PkPassArchive.read(input) }.getOrElse { error ->
            throw PassImportException(ImportFailure.INVALID_PKPASS, "Archive unreadable", error)
        }

        val passBytes = archive[PkPassArchive.PASS_ENTRY]
            ?: throw PassImportException(ImportFailure.INVALID_PKPASS, "pass.json missing")

        val pass = runCatching {
            ImportJson.decodeFromString<PkPassJson>(passBytes.toString(Charsets.UTF_8))
        }.getOrElse { error ->
            throw PassImportException(ImportFailure.INVALID_PKPASS, "pass.json invalid", error)
        }

        val barcode = pass.preferredBarcode?.takeIf { !it.message.isNullOrBlank() }
            ?: throw PassImportException(ImportFailure.NO_BARCODE, "No barcode contained in the pass")

        val mapper = PkPassMapper(pass, PkPassStrings.load(archive, preferredLanguage))
        val ownerName = mapper.ownerName()
        val identifier = mapper.identifier()
        val subtitle = mapper.subtitle()
        val location = pass.locations.firstOrNull()

        val draft = PassDraft(
            folderName = mapper.folderName(),
            title = mapper.title(),
            subtitle = subtitle,
            ownerName = ownerName,
            identifier = identifier,
            barcodeData = barcode.message.orEmpty(),
            barcodeType = BarcodeType.fromKey(barcode.format),
            barcodeAltText = mapper.translate(barcode.altText),
            // Apple does not specify an error correction level, but does specify the character
            // set. It matters for a faithful result because it determines the ECI header.
            barcodeEncoding = barcode.messageEncoding?.trim()?.takeIf { it.isNotEmpty() },
            backgroundColor = ColorParser.parseOrNull(pass.backgroundColor),
            logoImage = archive.firstOf(*LOGO_CANDIDATES),
            heroImage = archive.firstOf(*HERO_CANDIDATES),
            fields = mapper.fields(exclude = setOfNotNull(ownerName, identifier, subtitle)),
            expirationDate = mapper.expirationDate(),
            location = mapper.translate(location?.relevantText),
            locationLatitude = location?.latitude,
            locationLongitude = location?.longitude,
            source = PassSource.PKPASS,
            externalId = pass.serialNumber?.takeIf { it.isNotBlank() },
        )
        return PkPassImport(draft = draft, integrityVerified = archive.verifyIntegrity())
    }
}

/**
 * Translates Apple's freely definable fields into PassPorta's fixed structure.
 *
 * Apple prescribes neither field keys nor order, so heuristics are unavoidable. They are
 * bundled here so they can be tested and refined centrally.
 */
internal class PkPassMapper(
    private val pass: PkPassJson,
    private val strings: Map<String, String> = PkPassStrings.EMPTY,
) {

    private val structure: PkPassStructure? = pass.structure

    /** In localized passes, the value itself is only a key into `pass.strings`. */
    fun translate(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return strings[value]?.takeIf { it.isNotBlank() } ?: value
    }

    fun folderName(): String {
        val style = pass.style ?: return PassEntity.DEFAULT_FOLDER
        if (style != PkPassStyle.BOARDING_PASS) return style.folderName
        return when (structure?.transitType?.lowercase()?.removePrefix("pktransittype")) {
            "air" -> "Flüge"
            "train" -> "Bahn"
            "bus" -> "Bus"
            "boat" -> "Fähren"
            else -> PkPassStyle.BOARDING_PASS.folderName
        }
    }

    fun title(): String = translate(pass.logoText)
        ?: translate(pass.organizationName)
        ?: translate(pass.description)
        ?: FALLBACK_TITLE

    fun subtitle(): String? {
        val title = title()
        val primaryValues = structure?.primaryFields.orEmpty().mapNotNull { fieldValue(it) }

        // Boarding passes: the two primary fields are origin and destination.
        if (pass.style == PkPassStyle.BOARDING_PASS && primaryValues.size >= 2) {
            return "${primaryValues[0]} → ${primaryValues[1]}"
        }

        return listOfNotNull(
            primaryValues.firstOrNull(),
            structure?.headerFields.orEmpty().firstNotNullOfOrNull { fieldValue(it) },
            translate(pass.description),
        ).firstOrNull { !it.equals(title, ignoreCase = true) }
    }

    fun ownerName(): String = findField(NAME_KEYWORDS, NAME_EXCLUSIONS)
        ?: translate(pass.organizationName)
        ?: title()

    fun identifier(): String? = findField(IDENTIFIER_KEYWORDS, IDENTIFIER_EXCLUSIONS)
        ?: translate(pass.preferredBarcode?.altText)

    /**
     * All additional fields as label-value pairs.
     *
     * @param exclude values already prominently displayed (owner, card number, subtitle).
     *   Without this filter, the same information would appear twice on screen.
     */
    fun fields(exclude: Set<String> = emptySet()): List<PassFieldDraft> {
        val structure = structure ?: return emptyList()
        val seen = mutableSetOf<String>()

        return structure.sectionedFields.mapNotNull { (section, field) ->
            val value = fieldValue(field) ?: return@mapNotNull null
            if (value in exclude) return@mapNotNull null

            val label = translate(field.label)
            // The same entry in multiple sections (e.g. header and back) only once.
            if (!seen.add("${label.orEmpty()}\u0000$value")) return@mapNotNull null

            PassFieldDraft(label = label, value = value, section = section)
        }
    }

    /**
     * Expiration time of the pass.
     *
     * A `voided` pass has been withdrawn by the issuer and is immediately invalid regardless
     * of a later expiration date - it therefore goes straight to the archive.
     */
    fun expirationDate(): Long? {
        val declared = Iso8601.parseOrNull(pass.expirationDate)
        if (!pass.voided) return declared

        val invalidSince = System.currentTimeMillis() - 1
        return minOf(declared ?: Long.MAX_VALUE, invalidSince)
    }

    /**
     * Searches for the first field whose key or label contains one of the [keywords].
     * [keywords] is sorted by significance, so e.g. "passenger" takes precedence over "name".
     */
    private fun findField(keywords: List<String>, exclusions: List<String>): String? {
        val fields = structure?.allFields.orEmpty()
        for (keyword in keywords) {
            val match = fields.firstOrNull { field ->
                val haystack = field.searchKey(strings)
                haystack.contains(keyword) && exclusions.none { haystack.contains(it) }
            }
            val value = match?.let { fieldValue(it) }
            if (value != null) return value
        }
        return null
    }

    private fun fieldValue(field: PkField): String? =
        translate(field.value.asDisplayText()) ?: translate(field.attributedValue.asDisplayText())
}

private const val FALLBACK_TITLE = "Pass"

/** Retina variants first - Coil downscales anyway later. */
private val LOGO_CANDIDATES = arrayOf(
    "logo@3x.png",
    "logo@2x.png",
    "logo.png",
    "icon@3x.png",
    "icon@2x.png",
    "icon.png",
    "thumbnail@2x.png",
    "thumbnail.png",
)

/** Large-format images: for Apple `strip` (cards) or `background` (event tickets). */
private val HERO_CANDIDATES = arrayOf(
    "strip@3x.png",
    "strip@2x.png",
    "strip.png",
    "background@2x.png",
    "background.png",
)

private val NAME_KEYWORDS = listOf(
    "passenger", "passagier", "ticketholder", "holder", "inhaber", "member", "mitglied",
    "guest", "gast", "customer", "kunde", "traveler", "traveller", "reisender", "owner",
    "name",
)

/**
 * Prevents event/company names or number fields from being interpreted as the owner.
 * Without the number exclusion, e.g. `membershipNumber` would take precedence over `memberName`.
 */
private val NAME_EXCLUSIONS = listOf(
    "eventname", "venuename", "programname", "organizationname", "airlinename",
    "companyname", "username",
    "number", "nummer", "nr", "code",
)

private val IDENTIFIER_KEYWORDS = listOf(
    "membershipnumber", "membernumber", "memberid", "cardnumber", "kartennummer",
    "kundennummer", "accountnumber", "kontonummer", "customernumber", "ticketnumber",
    "ticketnr", "bookingreference", "buchungsnummer", "confirmation", "reference",
    "referenz", "number", "nummer", "account", "konto", "id",
)

private val IDENTIFIER_EXCLUSIONS = listOf("valid", "gueltig", "date", "datum", "seat", "sitz")

private val HTML_TAG = Regex("<[^>]+>")

/** Normalized search text of a field: key and (translated) label. */
private fun PkField.searchKey(strings: Map<String, String>): String {
    val translatedLabel = label?.let { strings[it] ?: it }.orEmpty()
    return "$key $translatedLabel".lowercase().filter { it.isLetterOrDigit() }
}

/** `attributedValue` may contain HTML - it is removed for display. */
private fun JsonElement?.asDisplayText(): String? =
    asText()?.replace(HTML_TAG, "")?.trim()?.takeIf { it.isNotEmpty() }







