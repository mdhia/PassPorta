package org.shadowgrove.passporta.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.shadowgrove.passporta.data.local.entity.BarcodeType

/**
 * Generates barcode bitmaps with ZXing - fully offline and without extra Android bindings
 * (`zxing-android-embedded` would be overkill for pure display purposes).
 *
 * The goal is not just a readable code, but one that matches the original: error correction
 * level and character set determine the module grid for 2D formats. Both are therefore taken
 * from the source whenever they are known.
 */
object BarcodeRenderer {

    /** Quiet zone in modules. ZXing otherwise sets very large margins depending on the format. */
    private const val QUIET_ZONE_MODULES = 2

    /** Aspect ratio (width to height) of the 1D or stacked formats. */
    private const val CODE128_ASPECT_RATIO = 3f
    private const val PDF417_ASPECT_RATIO = 2.5f

    /**
     * ITF inherently carries fewer characters than CODE128 and is therefore drawn narrower -
     * at the same width, the bars would otherwise be unnecessarily wide and the code would
     * look stretched.
     */
    private const val ITF_ASPECT_RATIO = 2.5f

    /**
     * Default when the level of the original is unknown.
     *
     * Apple Wallet and Google Wallet typically generate their QR codes with "M". Without a
     * value, ZXing would choose "L" and thus produce a visibly coarser pattern.
     */
    private val DEFAULT_QR_ERROR_CORRECTION = ErrorCorrectionLevel.M

    /**
     * Renders [data] in format [type].
     *
     * @param widthPx desired width in pixels; the height results from the format.
     * @param errorCorrection error correction level of the original, if known.
     * @param characterSet character set of the payload, if the source specifies it.
     * @return the bitmap or `null` if the data cannot be encoded in the chosen format
     *   (e.g. umlauts in a CODE128).
     */
    fun render(
        data: String,
        type: BarcodeType,
        widthPx: Int,
        errorCorrection: String? = null,
        characterSet: String? = null,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE,
    ): Bitmap? {
        if (data.isBlank() || widthPx <= 0) return null

        val heightPx = heightFor(type, widthPx)
        val hints = buildMap<EncodeHintType, Any> {
            put(EncodeHintType.MARGIN, QUIET_ZONE_MODULES)
            resolveCharacterSet(data, characterSet)?.let { put(EncodeHintType.CHARACTER_SET, it) }
            resolveErrorCorrection(type, errorCorrection)?.let {
                put(EncodeHintType.ERROR_CORRECTION, it)
            }
        }

        val matrix = runCatching {
            MultiFormatWriter().encode(data, type.toZxingFormat(), widthPx, heightPx, hints)
        }.getOrNull() ?: return null

        return matrix.toBitmap(foregroundColor, backgroundColor)
    }

    /**
     * Character set for the encoding.
     *
     * Decisive for the similarity to the original: ZXing prepends an ECI header to a QR code as
     * soon as a character set other than ISO-8859-1 is requested. A blanket "UTF-8" would
     * therefore produce a different - usually larger - pattern than the scanned code, even for
     * pure ASCII data. The character set is therefore only set if the source specifies it or the
     * data mandatorily requires it.
     */
    private fun resolveCharacterSet(data: String, requested: String?): String? {
        requested?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        // ISO-8859-1 covers exactly the code points up to 0xFF.
        return if (data.all { it.code <= 0xFF }) null else "UTF-8"
    }

    /**
     * Translates the stored level into the format-dependent hint value: ZXing expects an enum
     * value (QR) or a number (PDF417/Aztec) depending on the format.
     */
    private fun resolveErrorCorrection(type: BarcodeType, level: String?): Any? {
        val raw = level?.trim()?.takeIf { it.isNotEmpty() }

        return when (type) {
            BarcodeType.QR -> raw
                ?.let { value ->
                    runCatching { ErrorCorrectionLevel.valueOf(value.uppercase()) }.getOrNull()
                }
                ?: DEFAULT_QR_ERROR_CORRECTION

            // 0-8 per specification; without a value, ZXing's default applies.
            BarcodeType.PDF417 -> raw?.toIntOrNull()?.coerceIn(0, 8)

            // Percentage share of check data.
            BarcodeType.AZTEC -> raw?.toIntOrNull()?.coerceIn(0, 100)

            // 1D formats without selectable error correction.
            BarcodeType.CODE128, BarcodeType.ITF -> null
        }
    }

    /** Height matching the format: square for 2D, flat for 1D. */
    fun heightFor(type: BarcodeType, widthPx: Int): Int =
        (widthPx / aspectRatio(type)).toInt().coerceAtLeast(1)

    /** Aspect ratio for the Compose layout, so the space can be reserved in advance. */
    fun aspectRatio(type: BarcodeType): Float = when (type) {
        BarcodeType.QR, BarcodeType.AZTEC -> 1f
        BarcodeType.PDF417 -> PDF417_ASPECT_RATIO
        BarcodeType.CODE128 -> CODE128_ASPECT_RATIO
        BarcodeType.ITF -> ITF_ASPECT_RATIO
    }

    private fun BarcodeType.toZxingFormat(): BarcodeFormat = when (this) {
        BarcodeType.QR -> BarcodeFormat.QR_CODE
        BarcodeType.AZTEC -> BarcodeFormat.AZTEC
        BarcodeType.PDF417 -> BarcodeFormat.PDF_417
        BarcodeType.CODE128 -> BarcodeFormat.CODE_128
        BarcodeType.ITF -> BarcodeFormat.ITF
    }

    /**
     * Converts the module matrix into a bitmap.
     *
     * Written row by row via [Bitmap.setPixels] - noticeably faster than `setPixel` per module.
     */
    private fun BitMatrix.toBitmap(foregroundColor: Int, backgroundColor: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (this[x, y]) foregroundColor else backgroundColor
            }
        }
        return createBitmap(width, height).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
