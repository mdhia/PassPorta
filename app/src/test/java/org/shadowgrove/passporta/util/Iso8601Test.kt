package org.shadowgrove.passporta.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

/**
 * Verifies parsing of expiration dates from Apple and Google passes.
 */
class Iso8601Test {

    @Test
    fun `reads timestamps with timezone`() {
        val utc = Iso8601.parseOrNull("2030-06-01T20:00:00Z")
        val plusOne = Iso8601.parseOrNull("2030-06-01T21:00:00+01:00")

        assertEquals(utc, plusOne)
    }

    @Test
    fun `accounts for the timezone offset instead of discarding it`() {
        val withOffset = requireNotNull(Iso8601.parseOrNull("2030-06-01T20:00:00+02:00"))
        val asUtc = requireNotNull(Iso8601.parseOrNull("2030-06-01T20:00:00Z"))

        // Two hours difference - if the offset were ignored, both would be equal.
        assertEquals(-2 * 60 * 60 * 1000L, withOffset - asUtc)
    }

    @Test
    fun `reads milliseconds`() {
        val withMillis = Iso8601.parseOrNull("2030-06-01T20:00:00.500Z")
        val withoutMillis = requireNotNull(Iso8601.parseOrNull("2030-06-01T20:00:00Z"))

        assertEquals(withoutMillis + 500, withMillis)
    }

    @Test
    fun `reads plain dates as UTC`() {
        val parsed = requireNotNull(Iso8601.parseOrNull("2030-06-01"))

        // Always midnight UTC, regardless of the device timezone.
        val expected = TimeZone.getTimeZone("UTC").let {
            java.util.GregorianCalendar(it).apply {
                clear()
                set(2030, 5, 1)
            }.timeInMillis
        }
        assertEquals(expected, parsed)
    }

    @Test
    fun `rejects incomplete and foreign formats`() {
        assertNull(Iso8601.parseOrNull(null))
        assertNull(Iso8601.parseOrNull(""))
        assertNull(Iso8601.parseOrNull("   "))
        assertNull(Iso8601.parseOrNull("morgen"))
        assertNull(Iso8601.parseOrNull("01.06.2030"))
        // Partial matches must not slip through.
        assertNull(Iso8601.parseOrNull("2030-06-01T20:00:00Z und noch mehr"))
    }

    @Test
    fun `rejects impossible date values`() {
        assertNull(Iso8601.parseOrNull("2030-13-01"))
        assertNull(Iso8601.parseOrNull("2030-02-30"))
    }
}
