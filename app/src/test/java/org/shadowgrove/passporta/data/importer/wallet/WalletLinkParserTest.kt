package org.shadowgrove.passporta.data.importer.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.shadowgrove.passporta.data.importer.ImportFailure
import org.shadowgrove.passporta.data.importer.PassImportException
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.data.local.entity.PassSource
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Verifies that "Add to Google Wallet" links can be read fully offline.
 */
class WalletLinkParserTest {

    private val parser = WalletLinkParser()

    @Test
    fun `reads a loyalty card from a save link`() {
        val link = "https://pay.google.com/gp/v/save/${jwt(LOYALTY_PAYLOAD)}"

        val drafts = parser.parse(link)

        assertEquals(1, drafts.size)
        with(drafts.first()) {
            assertEquals("Kundenkarten", folderName)
            assertEquals("Beispiel Bonus", title)
            assertEquals("Erika Mustermann", ownerName)
            assertEquals("1234567890", identifier)
            assertEquals("LOYALTY-BARCODE", barcodeData)
            assertEquals(BarcodeType.QR, barcodeType)
            assertEquals(PassSource.WALLET_LINK, source)
            assertEquals(0xFF1A73E8.toInt(), backgroundColor)
            assertEquals("issuer.beispiel/loyaltyObject/1", externalId)
            assertNull(logoImage)
        }
    }

    @Test
    fun `reads LocalizedString structures of generic objects`() {
        val drafts = parser.parse("https://pay.google.com/gp/v/save/${jwt(GENERIC_PAYLOAD)}")

        with(drafts.first()) {
            assertEquals("Allgemein", folderName)
            assertEquals("Werkstattkarte", title)
            // `header` is the large main line in Google's generic layout.
            assertEquals("Mustermann GmbH", ownerName)
            assertEquals(BarcodeType.CODE128, barcodeType)
        }
    }

    @Test
    fun `maps a complete generic Google object`() {
        val drafts = parser.parse(jwt(FULL_GENERIC_PAYLOAD))

        with(drafts.first()) {
            assertEquals("[TEST ONLY] Google I/O", title)
            assertEquals("Alex McJacobs", ownerName)
            assertEquals("Attendee", subtitle)
            assertEquals("BARCODE_VALUE", barcodeData)
            assertEquals(0xFF4285F4.toInt(), backgroundColor)

            // textModulesData become label-value rows.
            assertEquals(2, fields.size)
            assertEquals("POINTS", fields[0].label)
            assertEquals("1112", fields[0].value)
            assertEquals("CONTACTS", fields[1].label)
            assertEquals("79", fields[1].value)
        }
    }

    @Test
    fun `reads expiration date, location and additional fields`() {
        val drafts = parser.parse(jwt(RICH_EVENT_PAYLOAD))

        with(drafts.first()) {
            assertNotNull(expirationDate)
            assertEquals(37.4220, locationLatitude!!, 0.0001)
            assertEquals(-122.0841, locationLongitude!!, 0.0001)
            assertEquals("Shoreline Amphitheatre, Mountain View", location)

            // infoModuleData and linksModuleData also end up in the field list.
            assertEquals("14A", fields.first { it.label == "Sitzplatz" }.value)
            assertNotNull(fields.firstOrNull { it.value == "https://beispiel.test/agb" })
        }
    }

    @Test
    fun `reads multiple objects from one token`() {
        val drafts = parser.parse(jwt(MULTI_OBJECT_PAYLOAD))

        assertEquals(2, drafts.size)
        assertEquals(setOf("Kundenkarten", "Tickets"), drafts.map { it.folderName }.toSet())
    }

    @Test
    fun `finds the token even in shared text`() {
        val shared = "Schau mal: https://pay.google.com/gp/v/save/${jwt(LOYALTY_PAYLOAD)} bis dann!"

        assertEquals(1, parser.parse(shared).size)
    }

    @Test
    fun `reports short links as not resolvable offline`() {
        assertFailure(ImportFailure.SHORT_LINK_REQUIRES_NETWORK) {
            parser.parse("https://pay.app.goo.gl/abc123")
        }
    }

    @Test
    fun `reports links without a token`() {
        assertFailure(ImportFailure.INVALID_JWT) { parser.parse("https://example.org/kein-pass") }
    }

    @Test
    fun `reports tokens without a static barcode`() {
        assertFailure(ImportFailure.NO_BARCODE) { parser.parse(jwt(ROTATING_BARCODE_PAYLOAD)) }
    }

    @Test
    fun `decodes the JWT payload without signature verification`() {
        val payload = JwtDecoder.decodePayload(jwt(LOYALTY_PAYLOAD))

        assertNotNull(payload)
        assertEquals("savetowallet", payload?.get("typ")?.toString()?.trim('"'))
    }

    private inline fun assertFailure(expected: ImportFailure, block: () -> Unit) {
        try {
            block()
            fail("Expected PassImportException with $expected")
        } catch (error: PassImportException) {
            assertEquals(expected, error.failure)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun jwt(payload: String): String {
        val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val header = encoder.encode("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        return "$header.${encoder.encode(payload.toByteArray())}.SIGNATURPLATZHALTER"
    }

    private companion object {

        val LOYALTY_PAYLOAD = """
            {
              "iss": "beispiel@beispiel.iam.gserviceaccount.com",
              "aud": "google",
              "typ": "savetowallet",
              "payload": {
                "loyaltyClasses": [
                  {
                    "id": "issuer.beispiel/loyaltyClass/1",
                    "issuerName": "Beispiel GmbH",
                    "programName": "Beispiel Bonus",
                    "hexBackgroundColor": "#1a73e8"
                  }
                ],
                "loyaltyObjects": [
                  {
                    "id": "issuer.beispiel/loyaltyObject/1",
                    "classId": "issuer.beispiel/loyaltyClass/1",
                    "state": "ACTIVE",
                    "accountName": "Erika Mustermann",
                    "accountId": "1234567890",
                    "barcode": {
                      "type": "QR_CODE",
                      "value": "LOYALTY-BARCODE",
                      "alternateText": "1234 5678 90"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val GENERIC_PAYLOAD = """
            {
              "typ": "savetowallet",
              "payload": {
                "genericObjects": [
                  {
                    "id": "issuer.beispiel/genericObject/7",
                    "cardTitle": { "defaultValue": { "language": "de", "value": "Werkstattkarte" } },
                    "header": { "defaultValue": { "language": "de", "value": "Mustermann GmbH" } },
                    "hexBackgroundColor": "#333333",
                    "barcode": { "type": "CODE_128", "value": "12345678" }
                  }
                ]
              }
            }
        """.trimIndent()

        val MULTI_OBJECT_PAYLOAD = """
            {
              "typ": "savetowallet",
              "payload": {
                "loyaltyObjects": [
                  {
                    "id": "a",
                    "accountName": "Erika Mustermann",
                    "barcode": { "type": "QR_CODE", "value": "A" }
                  }
                ],
                "eventTicketObjects": [
                  {
                    "id": "b",
                    "ticketHolderName": "Max Mustermann",
                    "ticketNumber": "T-1",
                    "barcode": { "type": "AZTEC", "value": "B" }
                  }
                ]
              }
            }
        """.trimIndent()

        val ROTATING_BARCODE_PAYLOAD = """
            {
              "typ": "savetowallet",
              "payload": {
                "loyaltyObjects": [
                  {
                    "id": "c",
                    "rotatingBarcode": { "type": "QR_CODE", "valuePattern": "{totp}" }
                  }
                ]
              }
            }
        """.trimIndent()

        /** The example from Google's pass builder - complete generic object. */
        val FULL_GENERIC_PAYLOAD = """
            {
              "typ": "savetowallet",
              "payload": {
                "genericObjects": [
                  {
                    "id": "ISSUER_ID.OBJECT_ID",
                    "classId": "ISSUER_ID.GENERIC_CLASS_ID",
                    "logo": {
                      "sourceUri": { "uri": "https://beispiel.test/logo.jpg" },
                      "contentDescription": {
                        "defaultValue": { "language": "en-US", "value": "LOGO" }
                      }
                    },
                    "cardTitle": {
                      "defaultValue": { "language": "en-US", "value": "[TEST ONLY] Google I/O" }
                    },
                    "subheader": {
                      "defaultValue": { "language": "en-US", "value": "Attendee" }
                    },
                    "header": {
                      "defaultValue": { "language": "en-US", "value": "Alex McJacobs" }
                    },
                    "textModulesData": [
                      { "id": "points", "header": "POINTS", "body": "1112" },
                      { "id": "contacts", "header": "CONTACTS", "body": "79" }
                    ],
                    "barcode": {
                      "type": "QR_CODE",
                      "value": "BARCODE_VALUE",
                      "alternateText": ""
                    },
                    "hexBackgroundColor": "#4285f4",
                    "heroImage": {
                      "sourceUri": { "uri": "https://beispiel.test/hero.png" }
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val RICH_EVENT_PAYLOAD = """
            {
              "typ": "savetowallet",
              "payload": {
                "eventTicketClasses": [
                  {
                    "id": "issuer.beispiel/eventTicketClass/9",
                    "eventName": { "defaultValue": { "language": "de", "value": "Konzert" } },
                    "venue": {
                      "name": { "defaultValue": { "language": "de", "value": "Shoreline Amphitheatre" } },
                      "address": { "defaultValue": { "language": "de", "value": "Mountain View" } }
                    },
                    "locations": [ { "latitude": 37.4220, "longitude": -122.0841 } ]
                  }
                ],
                "eventTicketObjects": [
                  {
                    "id": "issuer.beispiel/eventTicketObject/9",
                    "classId": "issuer.beispiel/eventTicketClass/9",
                    "ticketHolderName": "Erika Mustermann",
                    "validTimeInterval": { "end": { "date": "2030-06-01T20:00:00Z" } },
                    "infoModuleData": {
                      "labelValueRows": [
                        { "columns": [ { "label": "Sitzplatz", "value": "14A" } ] }
                      ]
                    },
                    "linksModuleData": {
                      "uris": [ { "uri": "https://beispiel.test/agb", "description": "AGB" } ]
                    },
                    "barcode": { "type": "AZTEC", "value": "EVENT-BARCODE" }
                  }
                ]
              }
            }
        """.trimIndent()
    }
}





