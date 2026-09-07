package org.shadowgrove.passporta.data.importer.pkpass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkPassStringsTest {

    @Test
    fun `reads key-value pairs`() {
        val content = """
            /* Mitgliedschaft */
            "MEMBER_NAME" = "Erika Mustermann";
            "CARD_LABEL"  =  "Kundennummer" ;
        """.trimIndent()

        val strings = PkPassStrings.parse(content)

        assertEquals("Erika Mustermann", strings["MEMBER_NAME"])
        assertEquals("Kundennummer", strings["CARD_LABEL"])
    }

    @Test
    fun `resolves escape sequences`() {
        val strings = PkPassStrings.parse(""""KEY" = "Zeile1\nZeile2 \"zitiert\" \u00fc";""")

        assertEquals("Zeile1\nZeile2 \"zitiert\" ü", strings["KEY"])
    }

    @Test
    fun `ignores incomplete entries`() {
        val strings = PkPassStrings.parse(""""OHNE_WERT" = ;""")

        assertTrue(strings.isEmpty())
    }
}
