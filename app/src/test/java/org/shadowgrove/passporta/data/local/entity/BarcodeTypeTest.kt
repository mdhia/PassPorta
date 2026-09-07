package org.shadowgrove.passporta.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM tests for the format detection (no Android dependencies needed).
 */
class BarcodeTypeTest {

    @Test
    fun `recognizes pkpass formats`() {
        assertEquals(BarcodeType.QR, BarcodeType.fromKeyOrNull("PKBarcodeFormatQR"))
        assertEquals(BarcodeType.AZTEC, BarcodeType.fromKeyOrNull("PKBarcodeFormatAztec"))
        assertEquals(BarcodeType.PDF417, BarcodeType.fromKeyOrNull("PKBarcodeFormatPDF417"))
        assertEquals(BarcodeType.CODE128, BarcodeType.fromKeyOrNull("PKBarcodeFormatCode128"))
    }

    @Test
    fun `unknown values return null or default`() {
        assertNull(BarcodeType.fromKeyOrNull("PKBarcodeFormatEAN13"))
        assertNull(BarcodeType.fromKeyOrNull(null))
        assertEquals(BarcodeType.DEFAULT, BarcodeType.fromKey("irgendwas"))
    }

    /** ITF appears under very different names depending on the source. */
    @Test
    fun `recognizes ITF in all common notations`() {
        assertEquals(BarcodeType.ITF, BarcodeType.fromKeyOrNull("ITF"))
        assertEquals(BarcodeType.ITF, BarcodeType.fromKeyOrNull("ITF_14"))
        assertEquals(BarcodeType.ITF, BarcodeType.fromKeyOrNull("Interleaved 2 of 5"))
        assertEquals(BarcodeType.ITF, BarcodeType.fromKeyOrNull("2of5"))
    }

    @Test
    fun `storageKey stays stable`() {
        assertEquals(
            listOf("QR", "AZTEC", "PDF417", "CODE128", "ITF"),
            BarcodeType.entries.map { it.storageKey },
        )
    }
}
