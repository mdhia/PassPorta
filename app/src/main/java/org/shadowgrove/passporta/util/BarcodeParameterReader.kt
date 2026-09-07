package org.shadowgrove.passporta.util

import android.graphics.Bitmap
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ResultMetadataType
import com.google.zxing.common.HybridBinarizer

/**
 * Encoding parameters read from a scanned barcode.
 *
 * @param errorCorrection Error correction level (QR: `L`/`M`/`Q`/`H`, PDF417: `0`-`8`).
 */
data class BarcodeParameters(val errorCorrection: String?)

/**
 * A barcode read by ZXing, including its encoding parameters.
 *
 * @param value Payload.
 * @param format ZXing format, mapped to an [org.shadowgrove.passporta.data.local.entity.BarcodeType].
 * @param errorCorrection Error correction level, if the format reports one.
 */
data class DecodedBarcode(
    val value: String,
    val format: BarcodeFormat,
    val errorCorrection: String?,
)

/**
 * Reads printed barcodes with ZXing.
 *
 * Two tasks: first, ML Kit does provide the content, but not the error correction level. That
 * level, however, determines how many modules a QR code occupies - if a different level is
 * chosen when redrawing, a visibly different pattern than the original results. ZXing's decoder
 * exposes the level as metadata.
 *
 * Second, the same decoder serves as a second opinion: the two libraries fail on different
 * images, and ZXing with `TRY_HARDER` sometimes finds codes that ML Kit misses.
 */
object BarcodeParameterReader {

    private const val TAG = "BarcodeParameterReader"

    /**
     * Upper limit for the image size. The decoder internally creates copies of the pixels;
     * for very large pages, the memory requirement would be disproportionate.
     */
    private const val MAX_PIXELS = 4_200_000

    private val HINTS = mapOf<DecodeHintType, Any>(DecodeHintType.TRY_HARDER to true)

    /** Only the encoding parameters of an already known code. */
    fun read(bitmap: Bitmap): BarcodeParameters? =
        decode(bitmap)?.errorCorrection?.let { BarcodeParameters(errorCorrection = it) }

    /**
     * Searches for a barcode across the entire image.
     *
     * @return the match or `null` if nothing is readable - this is not an error case.
     */
    fun decode(bitmap: Bitmap): DecodedBarcode? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_PIXELS) return null

        return try {
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val result = MultiFormatReader().decode(binaryBitmap, HINTS)
            val value = result.text?.takeIf { it.isNotBlank() } ?: return null

            DecodedBarcode(
                value = value,
                format = result.barcodeFormat,
                errorCorrection = result.resultMetadata
                    ?.get(ResultMetadataType.ERROR_CORRECTION_LEVEL)
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
            )
        } catch (error: Exception) {
            // ZXing throws a ReaderException when no code is found; other errors must not
            // abort the scan either - this pass is only a supplement.
            Log.d(TAG, "No code readable: ${error.javaClass.simpleName}")
            null
        }
    }
}
