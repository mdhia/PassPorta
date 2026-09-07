package org.shadowgrove.passporta.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the heuristics that turn recognized text into title, name and number.
 *
 * The lines correspond to typical screenshots and ticket PDFs.
 */
class ScanTextInterpreterTest {

    @Test
    fun `recognizes title by largest font at the top`() {
        val result = ScanTextInterpreter.interpret(
            listOf(
                line("Beispiel Club", height = 48, top = 20),
                line("Willkommen", height = 20, top = 90),
                line("Erika Mustermann", height = 24, top = 400),
            ),
        )

        assertEquals("Beispiel Club", result.title)
    }

    @Test
    fun `takes the value after a name label`() {
        val result = ScanTextInterpreter.interpret(
            listOf(
                line("Ticketshop", height = 40, top = 10),
                line("Name: Erika Mustermann", height = 20, top = 200),
            ),
        )

        assertEquals("Erika Mustermann", result.ownerName)
    }

    @Test
    fun `takes the following line when the label stands alone`() {
        val result = ScanTextInterpreter.interpret(
            listOf(
                line("Konzerthaus", height = 40, top = 10),
                line("Inhaber", height = 16, top = 300),
                line("Max Mustermann", height = 22, top = 330),
            ),
        )

        assertEquals("Max Mustermann", result.ownerName)
    }

    @Test
    fun `recognizes names in boarding pass format`() {
        val result = ScanTextInterpreter.interpret(
            listOf(
                line("Beispiel Air", height = 40, top = 10),
                line("MUSTERMANN/ERIKA", height = 26, top = 250),
            ),
        )

        assertEquals("MUSTERMANN/ERIKA", result.ownerName)
    }

    @Test
    fun `takes the longest digit sequence as card number`() {
        val result = ScanTextInterpreter.interpret(
            listOf(
                line("Beispiel Club", height = 40, top = 10),
                line("Gültig bis 12/2027", height = 18, top = 200),
                line("1234 5678 9012 3456", height = 20, top = 260),
            ),
        )

        assertEquals("1234 5678 9012 3456", result.identifier)
    }

    @Test
    fun `prefers the labelled number`() {
        val result = ScanTextInterpreter.interpret(
            listOf(
                line("Beispiel Club", height = 40, top = 10),
                line("Kundennummer: 998877", height = 18, top = 220),
                line("0000 1111 2222 3333 4444", height = 18, top = 280),
            ),
        )

        assertEquals("998877", result.identifier)
    }

    @Test
    fun `filters the barcode payload out of the suggestions`() {
        val result = ScanTextInterpreter.interpret(
            lines = listOf(
                line("Beispiel Club", height = 40, top = 10),
                line("ABC-BARCODE-123", height = 14, top = 600),
            ),
            barcodeData = "ABC-BARCODE-123",
        )

        assertTrue(result.textLines.none { it == "ABC-BARCODE-123" })
    }

    @Test
    fun `returns an empty result without usable lines`() {
        val result = ScanTextInterpreter.interpret(
            listOf(
                line("---", height = 10, top = 5),
                line("***", height = 10, top = 30),
            ),
        )

        assertNull(result.title)
        assertNull(result.ownerName)
        assertTrue(result.textLines.isEmpty())
    }

    @Test
    fun `reports a missing barcode`() {
        assertTrue(ScannedPass(barcodeData = "X").hasBarcode)
        assertTrue(!ScannedPass().hasBarcode)
    }

    // --- Automatically recognized additional fields ---

    @Test
    fun `adopts label-value pairs from a single line`() {
        val result = ScanTextInterpreter.interpret(
            listOf(
                line("Konzerthaus", height = 40, top = 10),
                line("Sitzplatz: 14A", height = 18, top = 200),
                line("Block: C", height = 18, top = 230),
                line("Einlass: 19:00", height = 18, top = 260),
            ),
        )

        assertEquals(
            listOf("Sitzplatz" to "14A", "Block" to "C", "Einlass" to "19:00"),
            result.fields.map { it.label to it.value },
        )
    }

    @Test
    fun `adopts a field with the value on the following line`() {
        val result = ScanTextInterpreter.interpret(
            listOf(
                line("Konzerthaus", height = 40, top = 10),
                line("Reihe:", height = 16, top = 300),
                line("12", height = 22, top = 330),
            ),
        )

        assertEquals("Reihe", result.fields.single().label)
        assertEquals("12", result.fields.single().value)
    }

    @Test
    fun `does not use a label when the next one follows directly`() {
        val result = ScanTextInterpreter.interpret(
            listOf(
                line("Konzerthaus", height = 40, top = 10),
                line("Reihe:", height = 16, top = 300),
                line("Block:", height = 16, top = 330),
            ),
        )

        assertTrue("A label is not a value", result.fields.isEmpty())
    }

    @Test
    fun `does not repeat values already displayed prominently`() {
        val result = ScanTextInterpreter.interpret(
            lines = listOf(
                line("Konzerthaus", height = 40, top = 10),
                line("Name: Erika Mustermann", height = 18, top = 200),
                line("Kundennummer: 998877", height = 18, top = 230),
                line("Sitzplatz: 14A", height = 18, top = 260),
            ),
        )

        // Name and number are already shown large/small above - only the seat remains.
        assertEquals(listOf("Sitzplatz"), result.fields.map { it.label })
    }

    @Test
    fun `does not mistake URLs for label-value pairs`() {
        val result = ScanTextInterpreter.interpret(
            listOf(
                line("Konzerthaus", height = 40, top = 10),
                line("https://beispiel.test/agb", height = 14, top = 500),
            ),
        )

        assertTrue(
            "The scheme must not become a label: ${result.fields}",
            result.fields.isEmpty(),
        )
    }

    @Test
    fun `takes each label only once`() {
        val result = ScanTextInterpreter.interpret(
            listOf(
                line("Konzerthaus", height = 40, top = 10),
                line("Sitzplatz: 14A", height = 18, top = 200),
                line("Sitzplatz: 15B", height = 18, top = 230),
            ),
        )

        assertEquals(1, result.fields.size)
        assertEquals("14A", result.fields.single().value)
    }

    @Test
    fun `rates upright text higher than sideways-read fragments`() {
        val upright = listOf(
            line("Konzerthaus Berlin", height = 40, top = 10),
            line("Erika Mustermann", height = 24, top = 200),
            line("Sitzplatz 14A", height = 18, top = 260),
        )
        // This is what the same image looks like when ML Kit reads sideways across the lines.
        val sideways = listOf(
            line("Ko", height = 40, top = 10),
            line("nz", height = 40, top = 60),
            line("e t", height = 24, top = 120),
            line("1 4", height = 18, top = 180),
        )

        assertTrue(
            "The upright orientation must be rated significantly better",
            ScanTextInterpreter.orientationScore(upright) >
                ScanTextInterpreter.orientationScore(sideways) * 3,
        )
    }

    @Test
    fun `weighs coherent words more than individual characters`() {
        // Both lines have exactly the same number of letters - only the grouping differs.
        // Without a word bonus they'd be equal and the orientation undeterminable.
        val words = listOf(line("abc def", height = 20, top = 0))
        val fragments = listOf(line("ab cd ef", height = 20, top = 0))

        assertTrue(
            ScanTextInterpreter.orientationScore(words) >
                ScanTextInterpreter.orientationScore(fragments),
        )
    }

    @Test
    fun `rates an empty recognition as zero`() {
        assertEquals(0, ScanTextInterpreter.orientationScore(emptyList()))
    }

    private fun line(text: String, height: Int, top: Int) =
        ScannedLine(text = text, height = height, top = top)
}
