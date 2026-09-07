package org.shadowgrove.passporta.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies the color formats found in `.pkpass` (CSS notation) and in the Google Wallet API
 * (hex notation).
 */
class ColorParserTest {

    @Test
    fun `parses rgb notation from pass json`() {
        assertEquals(0xFF5A3CC8.toInt(), ColorParser.parseOrNull("rgb(90, 60, 200)"))
    }

    @Test
    fun `parses rgb notation without spaces`() {
        assertEquals(0xFF000000.toInt(), ColorParser.parseOrNull("rgb(0,0,0)"))
    }

    @Test
    fun `parses rgba notation with fractional alpha`() {
        assertEquals(0x805A3CC8.toInt(), ColorParser.parseOrNull("rgba(90, 60, 200, 0.5)"))
    }

    @Test
    fun `parses hex notation from the wallet api`() {
        assertEquals(0xFF1A73E8.toInt(), ColorParser.parseOrNull("#1a73e8"))
    }

    @Test
    fun `parses short hex and hex with alpha`() {
        assertEquals(0xFFFF0000.toInt(), ColorParser.parseOrNull("#f00"))
        assertEquals(0x801A73E8.toInt(), ColorParser.parseOrNull("#801a73e8"))
    }

    @Test
    fun `parses hex without a hash`() {
        assertEquals(0xFF1A73E8.toInt(), ColorParser.parseOrNull("1a73e8"))
    }

    @Test
    fun `clamps overly large channels`() {
        assertEquals(0xFFFFFFFF.toInt(), ColorParser.parseOrNull("rgb(300, 999, 255)"))
    }

    @Test
    fun `returns null for unusable input`() {
        assertNull(ColorParser.parseOrNull(null))
        assertNull(ColorParser.parseOrNull("   "))
        assertNull(ColorParser.parseOrNull("blau"))
        assertNull(ColorParser.parseOrNull("#12345"))
    }

    @Test
    fun `uses the fallback when nothing is recognized`() {
        assertEquals(42, ColorParser.parseOrDefault("keine Farbe", fallback = 42))
    }
}
