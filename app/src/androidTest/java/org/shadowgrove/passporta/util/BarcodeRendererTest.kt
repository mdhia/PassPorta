package org.shadowgrove.passporta.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.shadowgrove.passporta.data.local.entity.BarcodeType

/**
 * Verifies the ZXing integration for all supported formats.
 */
@RunWith(AndroidJUnit4::class)
class BarcodeRendererTest {

    @Test
    fun rendersAllFormats() {
        BarcodeType.entries.forEach { type ->
            val bitmap = BarcodeRenderer.render(data = sampleFor(type), type = type, widthPx = 512)

            assertNotNull("Format $type was not rendered", bitmap)
            assertTrue("Format $type has no width", bitmap!!.width > 0)
            assertTrue("Format $type has no height", bitmap.height > 0)
            bitmap.recycle()
        }
    }

    @Test
    fun twoDimensionalFormatsAreSquare() {
        assertEquals(1f, BarcodeRenderer.aspectRatio(BarcodeType.QR))
        assertEquals(1f, BarcodeRenderer.aspectRatio(BarcodeType.AZTEC))
        assertEquals(512, BarcodeRenderer.heightFor(BarcodeType.QR, 512))
    }

    @Test
    fun oneDimensionalFormatsAreFlatter() {
        assertTrue(BarcodeRenderer.heightFor(BarcodeType.CODE128, 512) < 512)
        assertTrue(BarcodeRenderer.heightFor(BarcodeType.PDF417, 512) < 512)
        assertTrue(BarcodeRenderer.heightFor(BarcodeType.ITF, 512) < 512)
        assertTrue(BarcodeRenderer.aspectRatio(BarcodeType.CODE128) > 1f)
        assertTrue(BarcodeRenderer.aspectRatio(BarcodeType.ITF) > 1f)
    }

    /**
     * ITF encodes digit pairs. Odd lengths and letters simply cannot be represented in the
     * format - PassPorta must neither pad them nor crash, but let the rendering honestly fail.
     */
    @Test
    fun itfOnlyTakesDigitPairs() {
        assertNotNull(BarcodeRenderer.render(data = "12345678", type = BarcodeType.ITF, widthPx = 512))
        assertNull(BarcodeRenderer.render(data = "1234567", type = BarcodeType.ITF, widthPx = 512))
        assertNull(BarcodeRenderer.render(data = "ABCD", type = BarcodeType.ITF, widthPx = 512))
    }

    @Test
    fun returnsNullInsteadOfCrashingForUnencodableData() {
        // CODE128 can only handle Latin-1 - emojis must fail cleanly, not crash.
        assertNull(BarcodeRenderer.render(data = "", type = BarcodeType.CODE128, widthPx = 512))
        assertNull(BarcodeRenderer.render(data = "", type = BarcodeType.QR, widthPx = 512))
        assertNull(BarcodeRenderer.render(data = "abc", type = BarcodeType.QR, widthPx = 0))
    }

    /** ITF is purely numeric; all other formats can encode the text. */
    private fun sampleFor(type: BarcodeType): String = when (type) {
        BarcodeType.ITF -> "12345678901231"
        else -> "PASSPORTA123"
    }
}


