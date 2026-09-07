package org.shadowgrove.passporta.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests of link detection in additional fields.
 *
 * The focus is on false positives: pass fields are full of digit sequences and abbreviations
 * with periods that are not links.
 */
class TextLinksTest {

    @Test
    fun `recognizes web addresses with and without scheme`() {
        assertEquals(
            listOf("https://beispiel.de/tickets"),
            TextLinks.find("Details: https://beispiel.de/tickets").map { it.uri },
        )
        // Without a scheme, https is added - http would be the wrong assumption today.
        assertEquals(
            listOf("https://www.beispiel.de"),
            TextLinks.find("www.beispiel.de").map { it.uri },
        )
    }

    @Test
    fun `recognizes email addresses`() {
        assertEquals(
            listOf("mailto:service@beispiel.de"),
            TextLinks.find("Fragen an service@beispiel.de").map { it.uri },
        )
    }

    @Test
    fun `recognizes internationally formatted phone numbers without a field hint`() {
        assertEquals(
            listOf("tel:+4930123456"),
            TextLinks.find("+49 30 123456").map { it.uri },
        )
    }

    /**
     * The most important case: card and booking numbers must not become call links.
     */
    @Test
    fun `treats digit sequences without phone context as not a phone number`() {
        assertTrue(TextLinks.find("1234 5678 9012", label = "Kundennummer").isEmpty())
        assertTrue(TextLinks.find("0123456789", label = "Bestellnummer").isEmpty())
        assertTrue(TextLinks.find("4711", label = "Sitzplatz").isEmpty())
    }

    /** National phone numbers only when the label suggests it. */
    @Test
    fun `recognizes national phone numbers only with a matching label`() {
        assertTrue(TextLinks.find("030 123456", label = "Kundennummer").isEmpty())
        assertEquals(
            listOf("tel:030123456"),
            TextLinks.find("030 123456", label = "Telefon").map { it.uri },
        )
        assertEquals(
            listOf("tel:089987654"),
            TextLinks.find("089 987654", label = "Hotline").map { it.uri },
        )
    }

    @Test
    fun `treats abbreviations with a period as not an address`() {
        assertTrue(TextLinks.find("Gate B.12").isEmpty())
        assertTrue(TextLinks.find("Reihe 3.OG").isEmpty())
        assertTrue(TextLinks.find("Beispiel GmbH & Co. KG").isEmpty())
    }

    @Test
    fun `trims trailing punctuation`() {
        val link = TextLinks.find("Mehr unter https://beispiel.de/agb.").single()

        assertEquals("https://beispiel.de/agb", link.uri)
        assertEquals("https://beispiel.de/agb", "Mehr unter https://beispiel.de/agb.".substring(link.range))
    }

    @Test
    fun `returns multiple matches in text order`() {
        val text = "Web www.beispiel.de oder Mail an hallo@beispiel.de"

        val links = TextLinks.find(text)

        assertEquals(2, links.size)
        assertEquals("https://www.beispiel.de", links[0].uri)
        assertEquals("mailto:hallo@beispiel.de", links[1].uri)
        assertTrue(links[0].range.last < links[1].range.first)
    }

    /** Overlapping patterns must not lead to duplicated or truncated links. */
    @Test
    fun `resolves overlaps`() {
        // The address itself contains something that looks like an email.
        val links = TextLinks.find("https://beispiel.de/formular?an=service@beispiel.de")

        assertEquals(1, links.size)
        assertTrue(links.single().uri.startsWith("https://"))
    }

    /** "Hotel" contains "tel" - this must not trigger a phone hint. */
    @Test
    fun `does not confuse Hotel with Telefon`() {
        assertTrue(TextLinks.find("030 123456", label = "Hotel").isEmpty())
    }

    @Test
    fun `returns nothing for empty text`() {
        assertTrue(TextLinks.find("").isEmpty())
        assertTrue(TextLinks.find("   ").isEmpty())
    }
}
