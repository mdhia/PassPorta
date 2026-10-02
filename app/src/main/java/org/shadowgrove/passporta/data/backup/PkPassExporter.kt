package org.shadowgrove.passporta.data.backup

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.shadowgrove.passporta.data.importer.ImportJson
import org.shadowgrove.passporta.data.importer.PassAssetStore
import org.shadowgrove.passporta.data.importer.pkpass.PkBarcode
import org.shadowgrove.passporta.data.importer.pkpass.PkField
import org.shadowgrove.passporta.data.importer.pkpass.PkLocation
import org.shadowgrove.passporta.data.importer.pkpass.PkPassJson
import org.shadowgrove.passporta.data.importer.pkpass.PkPassPortaBarcode
import org.shadowgrove.passporta.data.importer.pkpass.PkPassPortaData
import org.shadowgrove.passporta.data.importer.pkpass.PkPassPortaField
import org.shadowgrove.passporta.data.importer.pkpass.PkPassStructure
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.data.local.entity.PassBarcodeEntity
import org.shadowgrove.passporta.data.local.entity.PassFieldEntity
import org.shadowgrove.passporta.data.local.entity.PassFieldSection
import org.shadowgrove.passporta.data.local.model.PassWithFields
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes one unsigned, standard-shaped PKPASS archive for a PassPorta pass.
 *
 * Standard keys carry everything other PKPASS readers understand; the complete PassPorta data
 * goes to `userInfo.passporta` and the original document to `passporta/` - both are ignored by
 * other readers. Images live in the standard `icon.png`/`logo.png`/`strip.png` entries, so they
 * are never stored twice in a backup.
 */
internal class PkPassExporter(
    private val assetStore: PassAssetStore,
) {

    /** Writes the archive to [output]; [output] is flushed but not closed. */
    fun write(pass: PassWithFields, output: OutputStream) {
        val entity = pass.pass
        val logo = assetStore.resolve(entity.logoPath)?.readBytes()
        val hero = assetStore.resolve(entity.heroImagePath)?.readBytes()
        val original = assetStore.resolve(entity.originalFilePath)?.readBytes()
        val originalEntry = original?.let { "$APP_DIR/original.${extensionOf(entity.originalFileName)}" }

        val entries = linkedMapOf<String, ByteArray>()
        entries[PASS_ENTRY] = encodePass(
            pass = pass,
            logoEntry = LOGO_ENTRY.takeIf { logo != null },
            heroEntry = STRIP_ENTRY.takeIf { hero != null },
            originalEntry = originalEntry,
        )
        if (logo != null) {
            // Apple requires icon.png; the logo is the closest image PassPorta has.
            entries[ICON_ENTRY] = logo
            entries[LOGO_ENTRY] = logo
        }
        if (hero != null) entries[STRIP_ENTRY] = hero
        if (original != null && originalEntry != null) entries[originalEntry] = original

        val manifest = buildJsonObject {
            entries.forEach { (name, bytes) -> put(name, JsonPrimitive(sha1(bytes))) }
        }
        entries[MANIFEST_ENTRY] = ImportJson.encodeToString(manifest).toByteArray(Charsets.UTF_8)

        ZipOutputStream(NonClosingOutputStream(output)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun encodePass(
        pass: PassWithFields,
        logoEntry: String?,
        heroEntry: String?,
        originalEntry: String?,
    ): ByteArray {
        val backup = pass.toBackupPass()
        val barcodes = pass.orderedBarcodes.ifEmpty {
            listOf(
                PassBarcodeEntity(
                    passId = pass.pass.id,
                    barcodeData = pass.pass.barcodeData,
                    barcodeType = pass.pass.barcodeType,
                    barcodeAltText = pass.pass.barcodeAltText,
                    barcodeEcc = pass.pass.barcodeEcc,
                    barcodeEncoding = pass.pass.barcodeEncoding,
                ),
            )
        }
        // Only formats from the PKPASS spec go to `barcodes`; UPC/EAN/ITF are kept losslessly in
        // `userInfo.passporta` and offered to other readers as Code 128 via the legacy key.
        val standardBarcodes = barcodes.mapNotNull { barcode ->
            barcode.barcodeType.toPkFormat()?.let { format -> barcode.toPkBarcode(format) }
        }
        val legacyBarcode = barcodes.first().let { barcode ->
            barcode.toPkBarcode(barcode.barcodeType.toPkFormat() ?: PK_FORMAT_CODE128)
        }
        val fields = pass.orderedFields.groupBy { it.section }
        val metadata = backup.toPkPassPortaData(logoEntry, heroEntry, originalEntry)
        val pkPass = PkPassJson(
            formatVersion = 1,
            passTypeIdentifier = PASS_TYPE_IDENTIFIER,
            teamIdentifier = TEAM_IDENTIFIER,
            serialNumber = backup.id,
            organizationName = backup.ownerName.ifBlank { APP_NAME },
            description = backup.title.ifBlank { APP_NAME },
            logoText = backup.title,
            backgroundColor = backup.backgroundColor.toPkColor(),
            expirationDate = backup.expirationDate?.toPkDate(),
            relevantDate = backup.startDate?.toPkDate(),
            locations = listOfNotNull(
                backup.locationLatitude?.let { latitude ->
                    backup.locationLongitude?.let { longitude ->
                        PkLocation(latitude, longitude, backup.location)
                    }
                },
            ),
            barcode = legacyBarcode,
            barcodes = standardBarcodes,
            generic = PkPassStructure(
                headerFields = fields[PassFieldSection.HEADER].toPkFields(),
                primaryFields = fields[PassFieldSection.PRIMARY].toPkFields(),
                secondaryFields = fields[PassFieldSection.SECONDARY].toPkFields(),
                auxiliaryFields = fields[PassFieldSection.AUXILIARY].toPkFields(),
                backFields = fields[PassFieldSection.BACK].toPkFields(),
            ),
            userInfo = buildJsonObject {
                put(PkPassJson.PASSPORTA_USER_INFO_KEY, ImportJson.encodeToJsonElement(PkPassPortaData.serializer(), metadata))
            },
        )
        return ImportJson.encodeToString(PkPassJson.serializer(), pkPass).toByteArray(Charsets.UTF_8)
    }

    private fun BackupPass.toPkPassPortaData(
        logoEntry: String?,
        heroEntry: String?,
        originalEntry: String?,
    ) = PkPassPortaData(
        id = id,
        folderName = folderName,
        isFavorite = isFavorite,
        title = title,
        subtitle = subtitle,
        ownerName = ownerName,
        identifier = identifier,
        barcodeData = barcodeData,
        barcodeType = barcodeType,
        barcodeAltText = barcodeAltText,
        barcodeEcc = barcodeEcc,
        barcodeEncoding = barcodeEncoding,
        barcodes = barcodes.map { barcode ->
            PkPassPortaBarcode(
                barcodeData = barcode.barcodeData,
                barcodeType = barcode.barcodeType,
                barcodeAltText = barcode.barcodeAltText,
                barcodeEcc = barcode.barcodeEcc,
                barcodeEncoding = barcode.barcodeEncoding,
                position = barcode.position,
            )
        },
        backgroundColor = backgroundColor,
        iconKey = iconKey,
        originalFileName = originalFileName,
        originalMimeType = originalMimeType,
        expirationDate = expirationDate,
        startDate = startDate,
        location = location,
        locationLatitude = locationLatitude,
        locationLongitude = locationLongitude,
        source = source,
        createdAt = createdAt,
        updatedAt = updatedAt,
        fields = fields.map { field ->
            PkPassPortaField(field.label, field.value, field.section, field.position)
        },
        logoEntry = logoEntry,
        heroEntry = heroEntry,
        originalEntry = originalEntry,
    )

    /** `messageEncoding` is required by the spec; never pick one that can't represent the data. */
    private fun PassBarcodeEntity.toPkBarcode(format: String) = PkBarcode(
        format = format,
        message = barcodeData,
        messageEncoding = barcodeEncoding
            ?: if (Charsets.ISO_8859_1.newEncoder().canEncode(barcodeData)) "iso-8859-1" else "utf-8",
        altText = barcodeAltText,
    )

    private fun List<PassFieldEntity>?.toPkFields(): List<PkField> =
        orEmpty().sortedBy { it.position }.map { field ->
            PkField(
                key = "passporta_${field.section.storageKey}_${field.position}",
                label = field.label,
                value = JsonPrimitive(field.value),
            )
        }

    private fun BarcodeType.toPkFormat(): String? = when (this) {
        BarcodeType.QR -> PK_FORMAT_QR
        BarcodeType.DATA_MATRIX -> PK_FORMAT_DATA_MATRIX
        BarcodeType.AZTEC -> PK_FORMAT_AZTEC
        BarcodeType.PDF417 -> PK_FORMAT_PDF417
        BarcodeType.CODE128 -> PK_FORMAT_CODE128
        BarcodeType.UPC, BarcodeType.EAN, BarcodeType.ITF -> null
    }

    private fun Int.toPkColor(): String {
        val red = (this shr 16) and 0xFF
        val green = (this shr 8) and 0xFF
        val blue = this and 0xFF
        return "rgb($red, $green, $blue)"
    }

    /** A new formatter per call: [SimpleDateFormat] is not thread-safe. */
    private fun Long.toPkDate(): String = SimpleDateFormat(DATE_PATTERN, Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(this))

    private fun sha1(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1")
        .digest(bytes)
        .joinToString(separator = "") { "%02x".format(it) }

    private fun extensionOf(name: String?): String = name
        ?.substringAfterLast('.', "")
        ?.lowercase(Locale.US)
        ?.filter(Char::isLetterOrDigit)
        ?.take(MAX_EXTENSION_LENGTH)
        ?.takeIf { it.isNotEmpty() }
        ?: DEFAULT_EXTENSION

    /** Lets the zip stream release its deflater on close without closing the caller's stream. */
    private class NonClosingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = flush()
    }

    private companion object {
        const val PASS_ENTRY = "pass.json"
        const val MANIFEST_ENTRY = "manifest.json"
        const val ICON_ENTRY = "icon.png"
        const val LOGO_ENTRY = "logo.png"
        const val STRIP_ENTRY = "strip.png"
        const val APP_DIR = "passporta"
        const val APP_NAME = "PassPorta"
        const val PASS_TYPE_IDENTIFIER = "pass.org.shadowgrove.passporta"
        const val TEAM_IDENTIFIER = "PASSPORTA"
        const val PK_FORMAT_QR = "PKBarcodeFormatQR"
        const val PK_FORMAT_DATA_MATRIX = "PKBarcodeFormatDataMatrix"
        const val PK_FORMAT_AZTEC = "PKBarcodeFormatAztec"
        const val PK_FORMAT_PDF417 = "PKBarcodeFormatPDF417"
        const val PK_FORMAT_CODE128 = "PKBarcodeFormatCode128"
        const val DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ssXXX"
        const val MAX_EXTENSION_LENGTH = 8
        const val DEFAULT_EXTENSION = "bin"
    }
}



