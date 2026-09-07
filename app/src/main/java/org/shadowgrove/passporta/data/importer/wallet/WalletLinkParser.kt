package org.shadowgrove.passporta.data.importer.wallet

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.shadowgrove.passporta.data.importer.ImportFailure
import org.shadowgrove.passporta.data.importer.PassDraft
import org.shadowgrove.passporta.data.importer.PassFieldDraft
import org.shadowgrove.passporta.data.importer.PassImportException
import org.shadowgrove.passporta.data.importer.array
import org.shadowgrove.passporta.data.importer.asText
import org.shadowgrove.passporta.data.importer.child
import org.shadowgrove.passporta.data.importer.firstText
import org.shadowgrove.passporta.data.importer.objectArray
import org.shadowgrove.passporta.data.importer.text
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.data.local.entity.PassFieldSection
import org.shadowgrove.passporta.data.local.entity.PassSource
import org.shadowgrove.passporta.util.ColorParser
import org.shadowgrove.passporta.util.Iso8601

/**
 * Reads "Add to Google Wallet" links offline.
 *
 * Google wraps the pass data in a JWT that sits directly in the URL
 * (`https://pay.google.com/gp/v/save/<jwt>`). The complete content is therefore available
 * locally - no network access is needed to get barcode, title and colors.
 *
 * Not representable are logos (only included as a URL) and rotating barcodes (generated
 * server-side).
 */
class WalletLinkParser {

    /**
     * @param rawLink link or shared text containing a wallet JWT.
     * @throws PassImportException if no token is found or no object has a barcode.
     */
    fun parse(rawLink: String): List<PassDraft> {
        val token = JwtDecoder.findToken(rawLink)
            ?: throw PassImportException(
                failure = if (isShortLink(rawLink)) {
                    ImportFailure.SHORT_LINK_REQUIRES_NETWORK
                } else {
                    ImportFailure.INVALID_JWT
                },
                message = "No decodable JWT in the link",
            )

        val payload = JwtDecoder.decodePayload(token)
            ?: throw PassImportException(ImportFailure.INVALID_JWT, "JWT payload unreadable")

        // The actual objects sit under "payload"; some issuers provide them flat.
        val container = payload.child("payload") ?: payload

        val drafts = WALLET_GROUPS.flatMap { group ->
            val classes = container.objectArray(group.classesKey)
            container.objectArray(group.objectsKey).mapNotNull { walletObject ->
                toDraft(walletObject, classes, group)
            }
        }

        if (drafts.isEmpty()) {
            throw PassImportException(
                ImportFailure.NO_BARCODE,
                "Wallet token contains no object with a static barcode",
            )
        }
        return drafts
    }

    /**
     * Short links (`pay.app.goo.gl`) are pure redirects with no payload - not resolvable
     * offline.
     */
    private fun isShortLink(rawLink: String): Boolean {
        val host = HOST_PATTERN.find(rawLink)?.groupValues?.get(1)?.lowercase() ?: return false
        return SHORT_LINK_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private fun toDraft(
        walletObject: JsonObject,
        classes: List<JsonObject>,
        group: WalletGroup,
    ): PassDraft? {
        val barcode = walletObject.child("barcode") ?: return null
        val barcodeValue = barcode.firstText("value", "alternateText") ?: return null

        val classObject = walletObject.text("classId")
            ?.let { classId -> classes.firstOrNull { it.text("id") == classId } }
            ?: walletObject.child("classReference")
            ?: classes.firstOrNull()

        // `cardTitle` is the most specific label of the object and therefore takes precedence
        // over the class name (often just the issuer).
        val title = walletObject.localized("cardTitle")
            ?: classObject?.firstLocalized(*CLASS_TITLE_KEYS)
            ?: walletObject.firstLocalized(*OBJECT_TITLE_KEYS)
            ?: FALLBACK_TITLE

        val ownerName = ownerName(walletObject, classObject) ?: title
        val identifier = identifier(walletObject, barcode)
        val subtitle = subtitle(walletObject, classObject, title, ownerName)
        val place = place(walletObject, classObject)

        return PassDraft(
            folderName = group.folderName,
            title = title,
            subtitle = subtitle,
            ownerName = ownerName,
            identifier = identifier,
            barcodeData = barcodeValue,
            barcodeType = BarcodeType.fromKey(barcode.text("type")),
            barcodeAltText = barcode.text("alternateText")?.takeIf { it != barcodeValue },
            backgroundColor = ColorParser.parseOrNull(
                walletObject.text("hexBackgroundColor") ?: classObject?.text("hexBackgroundColor"),
            ),
            // Logos and hero images only exist as a URL in the token and would require a
            // download - PassPorta deliberately has no network permission.
            logoImage = null,
            fields = fields(
                walletObject = walletObject,
                classObject = classObject,
                exclude = setOfNotNull(title, ownerName, identifier, subtitle),
            ),
            expirationDate = expirationDate(walletObject, classObject),
            location = place?.text,
            locationLatitude = place?.latitude,
            locationLongitude = place?.longitude,
            source = PassSource.WALLET_LINK,
            externalId = walletObject.text("id"),
        )
    }

    private fun subtitle(
        walletObject: JsonObject,
        classObject: JsonObject?,
        title: String,
        ownerName: String,
    ): String? = listOfNotNull(
        walletObject.localized("subheader"),
        classObject?.firstLocalized("issuerName", "localizedIssuerName", "provider"),
        walletObject.textModule(SUBTITLE_KEYWORDS),
    ).firstOrNull { !it.equals(title, ignoreCase = true) && !it.equals(ownerName, ignoreCase = true) }

    /**
     * Name of the holder.
     *
     * `header` is the large main line in Google's generic layout - it usually holds the
     * holder's name (unlike `subheader`, which describes the role).
     */
    private fun ownerName(walletObject: JsonObject, classObject: JsonObject?): String? =
        walletObject.firstLocalized(*OWNER_KEYS)
            ?: walletObject.child("reservationInfo")?.firstLocalized(*OWNER_KEYS)
            ?: walletObject.array("passengerNames").asTextOrNull()
            ?: walletObject.localized("header")
            ?: walletObject.textModule(OWNER_KEYWORDS)
            ?: classObject?.firstLocalized("issuerName", "localizedIssuerName")

    private fun identifier(walletObject: JsonObject, barcode: JsonObject): String? =
        walletObject.firstLocalized(*IDENTIFIER_KEYS)
            ?: walletObject.child("reservationInfo")?.firstLocalized(*IDENTIFIER_KEYS)
            ?: walletObject.textModule(IDENTIFIER_KEYWORDS)
            ?: barcode.text("alternateText")

    /**
     * All additional information as label-value pairs.
     *
     * Google distributes them across three structures: `textModulesData` (free-form entries),
     * `infoModuleData.labelValueRows` (tabular) and `linksModuleData` (references). All three
     * are merged, and duplicate or already prominently displayed values are dropped.
     */
    private fun fields(
        walletObject: JsonObject,
        classObject: JsonObject?,
        exclude: Set<String>,
    ): List<PassFieldDraft> {
        val collected = mutableListOf<PassFieldDraft>()

        listOfNotNull(walletObject, classObject).forEach { source ->
            source.objectArray("textModulesData").forEach { module ->
                val value = module.localized("body") ?: return@forEach
                val label = module.localized("header") ?: module.text("id")
                collected += PassFieldDraft(label, value, PassFieldSection.SECONDARY)
            }

            source.child("infoModuleData")
                ?.objectArray("labelValueRows")
                ?.forEach { row ->
                    row.objectArray("columns").forEach { column ->
                        val value = column.localized("value") ?: return@forEach
                        collected += PassFieldDraft(
                            label = column.localized("label"),
                            value = value,
                            section = PassFieldSection.AUXILIARY,
                        )
                    }
                }

            source.child("linksModuleData")
                ?.objectArray("uris")
                ?.forEach { link ->
                    val value = link.text("uri") ?: return@forEach
                    collected += PassFieldDraft(
                        label = link.localized("description") ?: link.text("id"),
                        value = value,
                        section = PassFieldSection.BACK,
                    )
                }
        }

        val seen = mutableSetOf<String>()
        return collected.filter { field ->
            field.value !in exclude && seen.add("${field.label.orEmpty()}\u0000${field.value}")
        }
    }

    /** End of validity; the object takes precedence over the class. */
    private fun expirationDate(walletObject: JsonObject, classObject: JsonObject?): Long? =
        walletObject.validUntil() ?: classObject?.validUntil()

    private fun place(walletObject: JsonObject, classObject: JsonObject?): Place? {
        val coordinates = (walletObject.objectArray("locations") + classObject?.objectArray("locations").orEmpty())
            .firstOrNull { it.text("latitude") != null && it.text("longitude") != null }

        // `venue` exists for event tickets and, unlike `locations`, provides plain text.
        val venue = classObject?.child("venue")
        val text = listOfNotNull(
            venue?.localized("name"),
            venue?.localized("address"),
        ).joinToString(separator = ", ").takeIf { it.isNotEmpty() }

        if (text == null && coordinates == null) return null
        return Place(
            text = text,
            latitude = coordinates?.text("latitude")?.toDoubleOrNull(),
            longitude = coordinates?.text("longitude")?.toDoubleOrNull(),
        )
    }
}

/** Location of a pass, composed of plain text and/or coordinates. */
private class Place(val text: String?, val latitude: Double?, val longitude: Double?)

/** Object/class pairs of the Wallet API along with their target folder in PassPorta. */
private data class WalletGroup(
    val objectsKey: String,
    val classesKey: String,
    val folderName: String,
)

private val WALLET_GROUPS = listOf(
    WalletGroup("loyaltyObjects", "loyaltyClasses", "Kundenkarten"),
    WalletGroup("offerObjects", "offerClasses", "Gutscheine"),
    WalletGroup("giftCardObjects", "giftCardClasses", "Geschenkkarten"),
    WalletGroup("eventTicketObjects", "eventTicketClasses", "Tickets"),
    WalletGroup("flightObjects", "flightClasses", "Flüge"),
    WalletGroup("transitObjects", "transitClasses", "Fahrkarten"),
    WalletGroup("genericObjects", "genericClasses", "Allgemein"),
)

private const val FALLBACK_TITLE = "Wallet-Pass"

/** Host from an arbitrary URL - even when it's embedded in a longer text. */
private val HOST_PATTERN = Regex("""https?://([^/?#\s]+)""", RegexOption.IGNORE_CASE)

private val SHORT_LINK_HOSTS = listOf("goo.gl", "g.co", "bit.ly", "t.co")

private val CLASS_TITLE_KEYS = arrayOf("programName", "eventName", "title", "issuerName", "localizedIssuerName")

private val OBJECT_TITLE_KEYS = arrayOf("cardTitle", "header", "title")

private val OWNER_KEYS = arrayOf(
    "accountName", "ticketHolderName", "passengerName", "holderName", "customerName", "name",
)

private val IDENTIFIER_KEYS = arrayOf(
    "accountId", "ticketNumber", "cardNumber", "confirmationCode", "memberId", "serialNumber",
)

private val OWNER_KEYWORDS = listOf("name", "inhaber", "holder", "kunde", "member", "mitglied")

private val IDENTIFIER_KEYWORDS = listOf("nummer", "number", "id", "konto", "account", "karte", "card")

private val SUBTITLE_KEYWORDS = listOf("beschreibung", "description", "info", "details")

/**
 * Reads a field that is either a plain string or a `LocalizedString` structure
 * (`{ "defaultValue": { "language": "de", "value": "..." } }`).
 */
private fun JsonObject.localized(key: String): String? {
    val element = this[key] ?: return null
    (element as? JsonPrimitive)?.let { return it.asText() }
    val wrapper = element as? JsonObject ?: return null
    return wrapper.child("defaultValue")?.text("value")
        ?: wrapper.text("value")
        ?: wrapper.objectArray("translatedValues").firstNotNullOfOrNull { it.text("value") }
}

private fun JsonObject.firstLocalized(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { localized(it) }

/**
 * Searches `textModulesData` - many issuers store additional information there, such as
 * customer number or name, when there is no matching standard field.
 */
private fun JsonObject.textModule(keywords: List<String>): String? {
    val modules = objectArray("textModulesData")
    if (modules.isEmpty()) return null
    for (keyword in keywords) {
        val match = modules.firstOrNull { module ->
            val header = (module.localized("header") ?: module.text("id")).orEmpty().lowercase()
            header.contains(keyword)
        }
        val body = match?.localized("body")
        if (body != null) return body
    }
    return null
}

/** Joins a string array (e.g. `passengerNames`) into a single line. */
private fun JsonArray?.asTextOrNull(): String? =
    this?.mapNotNull { it.asText() }?.takeIf { it.isNotEmpty() }?.joinToString(", ")

/**
 * End of the validity period.
 *
 * Google nests the timestamp in `validTimeInterval.end.date` (RFC 3339). Some issuers instead
 * set the end flat as `validTimeInterval.end`.
 */
private fun JsonObject.validUntil(): Long? {
    val interval = child("validTimeInterval") ?: return null
    val end = interval.child("end")?.text("date") ?: interval.text("end")
    return Iso8601.parseOrNull(end)
}



