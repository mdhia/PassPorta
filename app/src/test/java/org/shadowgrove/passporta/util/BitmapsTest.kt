package org.shadowgrove.passporta.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the size calculation for images to be analyzed.
 *
 * `inSampleSize` is the only lever that shrinks an image already while decoding. If it is too
 * large, details are lost; if it is too small, a camera photo ends up in full resolution in
 * memory.
 */
class BitmapsTest {

    @Test
    fun `keeps the longest edge under the upper limit`() {
        // Typical 12 MP camera photo. This is where the bug was: with the previous condition,
        // the level stayed at 1 because half of it (2016) was already under 2048 - so the
        // photo wasn't downscaled at all.
        assertEquals(2, Bitmaps.calculateSampleSize(4032, 3024, 2048))
    }

    @Test
    fun `leaves small images unchanged`() {
        assertEquals(1, Bitmaps.calculateSampleSize(1080, 1920, 2048))
    }

    @Test
    fun `shrinks logos below the logo limit`() {
        assertEquals(8, Bitmaps.calculateSampleSize(4000, 3000, Bitmaps.MAX_LOGO_SIZE_PX))
    }

    @Test
    fun `yields an edge under the limit for every source size`() {
        val limit = 2048
        listOf(640, 1024, 2048, 2049, 3000, 4032, 8000, 12000).forEach { edge ->
            val scaled = edge / Bitmaps.calculateSampleSize(edge, edge, limit)
            assertTrue("$edge yielded $scaled", scaled <= limit)
        }
    }

    @Test
    fun `falls back to full resolution for a nonsensical limit`() {
        // Guards against an infinite loop should the limit ever come from a calculation.
        assertEquals(1, Bitmaps.calculateSampleSize(4000, 3000, 0))
        assertEquals(1, Bitmaps.calculateSampleSize(4000, 3000, -10))
    }
}
