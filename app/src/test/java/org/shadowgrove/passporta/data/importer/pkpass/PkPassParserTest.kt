package org.shadowgrove.passporta.data.importer.pkpass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.shadowgrove.passporta.data.importer.ImportFailure
import org.shadowgrove.passporta.data.importer.PassImportException
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.data.local.entity.PassSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Covers the mapping of realistic `.pkpass` archives to [org.shadowgrove.passporta.data.importer.PassDraft].
 */
class PkPassParserTest {

    private val parser = PkPassParser()

    @Test
    fun `reads a loyalty card with color, logo and fields`() {
        val archive = pkPassArchive(
            passJson = STORE_CARD,
            extraFiles = mapOf("logo.png" to LOGO_BYTES),
        )

        val result = parser.parse(ByteArrayInputStream(archive))
        val draft = result.draft

        assertEquals("Kundenkarten", draft.folderName)
        assertEquals("Beispiel Club", draft.title)
        assertEquals("Erika Mustermann", draft.ownerName)
        assertEquals("1234 5678 9012", draft.identifier)
        assertEquals("9012345678", draft.barcodeData)
        assertEquals(BarcodeType.PDF417, draft.barcodeType)
        assertEquals(PassSource.PKPASS, draft.source)
        assertEquals("SN-42", draft.externalId)
        assertEquals(0xFF5A3CC8.toInt(), draft.backgroundColor)
        assertTrue(LOGO_BYTES.contentEquals(draft.logoImage))
    }

    @Test
    fun `confirms correct manifest checksums`() {
        val archive = pkPassArchive(passJson = STORE_CARD, withManifest = true)

        assertTrue(parser.parse(ByteArrayInputStream(archive)).integrityVerified)
    }

    @Test
    fun `adopts additional fields, expiration date and location`() {
        val archive = pkPassArchive(passJson = RICH_EVENT_TICKET)

        val draft = parser.parse(ByteArrayInputStream(archive)).draft

        assertEquals("Tickets", draft.folderName)
        assertNotNull(draft.expirationDate)
        assertEquals("Grosse Halle", draft.location)
        assertEquals(52.5163, draft.locationLatitude!!, 0.0001)
        assertEquals(13.3777, draft.locationLongitude!!, 0.0001)

        // The character set determines the ECI header and thus the module pattern.
        assertEquals("iso-8859-1", draft.barcodeEncoding)

        // Fields keep their label and order.
        val seat = draft.fields.first { it.label == "Sitzplatz" }
        assertEquals("14A", seat.value)
        assertNotNull(draft.fields.firstOrNull { it.label == "Hinweis" })
    }

    @Test
    fun `does not show values twice when already displayed prominently`() {
        val archive = pkPassArchive(passJson = RICH_EVENT_TICKET)

        val draft = parser.parse(ByteArrayInputStream(archive)).draft

        // The owner name is shown large above the barcode - it would be noise in the field list.
        assertTrue(draft.fields.none { it.value == draft.ownerName })
    }

    @Test
    fun `treats withdrawn passes as expired`() {
        val archive = pkPassArchive(passJson = VOIDED_PASS)

        val draft = parser.parse(ByteArrayInputStream(archive)).draft

        val expiration = requireNotNull(draft.expirationDate)
        assertTrue(
            "A voided pass must be immediately invalid regardless of its expiration date",
            expiration < System.currentTimeMillis(),
        )
    }

    @Test
    fun `detects tampered archives`() {
        val archive = pkPassArchive(
            passJson = STORE_CARD,
            withManifest = true,
            manifestOverride = mapOf("pass.json" to "0000000000000000000000000000000000000000"),
        )

        assertFalse(parser.parse(ByteArrayInputStream(archive)).integrityVerified)
    }

    @Test
    fun `derives folder and subtitle of a boarding pass`() {
        val archive = pkPassArchive(passJson = BOARDING_PASS)

        val draft = parser.parse(ByteArrayInputStream(archive)).draft

        assertEquals("Flüge", draft.folderName)
        assertEquals("FRA → JFK", draft.subtitle)
        assertEquals("MUSTERMANN/ERIKA", draft.ownerName)
        assertEquals(BarcodeType.AZTEC, draft.barcodeType)
    }

    @Test
    fun `uses pass-strings for localized values`() {
        val archive = pkPassArchive(
            passJson = LOCALIZED_STORE_CARD,
            extraFiles = mapOf(
                "de.lproj/pass.strings" to """"OWNER" = "Erika Mustermann";""".toByteArray(),
                "en.lproj/pass.strings" to """"OWNER" = "Jane Doe";""".toByteArray(),
            ),
        )

        val draft = parser.parse(ByteArrayInputStream(archive), preferredLanguage = "de").draft

        assertEquals("Erika Mustermann", draft.ownerName)
    }

    @Test
    fun `prefers the first entry from barcodes`() {
        val archive = pkPassArchive(passJson = MULTI_BARCODE)

        val draft = parser.parse(ByteArrayInputStream(archive)).draft

        assertEquals(BarcodeType.QR, draft.barcodeType)
        assertEquals("QR-NUTZDATEN", draft.barcodeData)
    }

    @Test
    fun `reports a missing barcode`() {
        val archive = pkPassArchive(passJson = """{"formatVersion":1,"storeCard":{}}""")

        assertFailure(ImportFailure.NO_BARCODE) { parser.parse(ByteArrayInputStream(archive)) }
    }

    @Test
    fun `reports a missing pass json`() {
        val archive = pkPassArchive(passJson = null, extraFiles = mapOf("logo.png" to LOGO_BYTES))

        assertFailure(ImportFailure.INVALID_PKPASS) { parser.parse(ByteArrayInputStream(archive)) }
    }

    @Test
    fun `reports broken archives`() {
        assertFailure(ImportFailure.INVALID_PKPASS) {
            parser.parse(ByteArrayInputStream("kein zip".toByteArray()))
        }
    }

    @Test
    fun `ignores entries with path traversal`() {
        val archive = pkPassArchive(
            passJson = STORE_CARD,
            extraFiles = mapOf("../../evil.png" to LOGO_BYTES),
        )

        val read = PkPassArchive.read(ByteArrayInputStream(archive))

        assertEquals(setOf("pass.json"), read.entryNames)
    }

    private inline fun assertFailure(expected: ImportFailure, block: () -> Unit) {
        try {
            block()
            fail("Expected PassImportException with $expected")
        } catch (error: PassImportException) {
            assertEquals(expected, error.failure)
        }
    }

    /** Builds a `.pkpass` archive in memory. */
    private fun pkPassArchive(

        passJson: String?,
        extraFiles: Map<String, ByteArray> = emptyMap(),
        withManifest: Boolean = false,
        manifestOverride: Map<String, String> = emptyMap(),
    ): ByteArray {
        val files = buildMap {
            passJson?.let { put("pass.json", it.toByteArray()) }
            putAll(extraFiles)
        }
        val entries = files.toMutableMap()

        if (withManifest) {
            val hashes = files.mapValues { (name, bytes) -> manifestOverride[name] ?: sha1Hex(bytes) }
            val manifest = hashes.entries.joinToString(prefix = "{", postfix = "}") { (name, hash) ->
                """"$name":"$hash""""
            }
            entries["manifest.json"] = manifest.toByteArray()
        }

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {

        val LOGO_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        /** Event ticket with all additional information: fields, expiration date and location. */
        val RICH_EVENT_TICKET = """
            {
              "formatVersion": 1,
              "serialNumber": "SN-99",
              "organizationName": "Konzerthaus",
              "description": "Konzertticket",
              "logoText": "Konzerthaus",
              "expirationDate": "2030-09-01T22:00:00+02:00",
              "locations": [
                {
                  "latitude": 52.5163,
                  "longitude": 13.3777,
                  "relevantText": "Grosse Halle"
                }
              ],
              "barcodes": [
                {
                  "format": "PKBarcodeFormatQR",
                  "message": "TICKET-99",
                  "messageEncoding": "iso-8859-1"
                }
              ],
              "eventTicket": {
                "headerFields": [
                  { "key": "seat", "label": "Sitzplatz", "value": "14A" }
                ],
                "primaryFields": [
                  { "key": "event", "label": "Veranstaltung", "value": "Jahreskonzert" }
                ],
                "secondaryFields": [
                  { "key": "guestName", "label": "Gast", "value": "Erika Mustermann" }
                ],
                "backFields": [
                  { "key": "note", "label": "Hinweis", "value": "Einlass ab 19 Uhr" }
                ]
              }
            }
        """.trimIndent()

        /** Pass withdrawn by the issuer with an expiration date far in the future. */
        val VOIDED_PASS = """
            {
              "formatVersion": 1,
              "serialNumber": "SN-VOID",
              "organizationName": "Beispiel GmbH",
              "description": "Storniert",
              "voided": true,
              "expirationDate": "2099-01-01T00:00:00Z",
              "barcodes": [
                { "format": "PKBarcodeFormatQR", "message": "VOID-1" }
              ],
              "generic": {
                "primaryFields": [
                  { "key": "info", "label": "Info", "value": "Storniert" }
                ]
              }
            }
        """.trimIndent()

        val STORE_CARD = """
            {
              "formatVersion": 1,
              "serialNumber": "SN-42",
              "organizationName": "Beispiel GmbH",
              "description": "Kundenkarte",
              "logoText": "Beispiel Club",
              "backgroundColor": "rgb(90, 60, 200)",
              "foregroundColor": "rgb(255, 255, 255)",
              "barcodes": [
                {
                  "format": "PKBarcodeFormatPDF417",
                  "message": "9012345678",
                  "messageEncoding": "iso-8859-1",
                  "altText": "9012 3456 78"
                }
              ],
              "storeCard": {
                "headerFields": [
                  { "key": "membershipNumber", "label": "Kundennummer", "value": "1234 5678 9012" }
                ],
                "primaryFields": [
                  { "key": "memberName", "label": "Inhaber", "value": "Erika Mustermann" }
                ],
                "secondaryFields": [
                  { "key": "points", "label": "Punkte", "value": 1250 }
                ]
              }
            }
        """.trimIndent()

        val BOARDING_PASS = """
            {
              "formatVersion": 1,
              "organizationName": "Beispiel Air",
              "description": "Bordkarte",
              "barcode": { "format": "PKBarcodeFormatAztec", "message": "BOARDING-DATA" },
              "boardingPass": {
                "transitType": "PKTransitTypeAir",
                "primaryFields": [
                  { "key": "origin", "label": "Frankfurt", "value": "FRA" },
                  { "key": "destination", "label": "New York", "value": "JFK" }
                ],
                "secondaryFields": [
                  { "key": "passenger", "label": "Passagier", "value": "MUSTERMANN/ERIKA" }
                ]
              }
            }
        """.trimIndent()

        val LOCALIZED_STORE_CARD = """
            {
              "formatVersion": 1,
              "organizationName": "Beispiel GmbH",
              "barcode": { "format": "PKBarcodeFormatQR", "message": "X" },
              "storeCard": {
                "primaryFields": [
                  { "key": "memberName", "label": "Inhaber", "value": "OWNER" }
                ]
              }
            }
        """.trimIndent()

        val MULTI_BARCODE = """
            {
              "formatVersion": 1,
              "organizationName": "Beispiel GmbH",
              "barcodes": [
                { "format": "PKBarcodeFormatQR", "message": "QR-NUTZDATEN" },
                { "format": "PKBarcodeFormatCode128", "message": "128-NUTZDATEN" }
              ],
              "barcode": { "format": "PKBarcodeFormatCode128", "message": "128-NUTZDATEN" },
              "generic": {}
            }
        """.trimIndent()
    }
}





