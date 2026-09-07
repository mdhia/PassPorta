package org.shadowgrove.passporta.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.graphics.createBitmap

/**
 * Small helpers around bitmaps during import and scanning.
 *
 * Logos from `.pkpass` archives can be several megapixels large (retina assets). For the card
 * view, a much smaller variant is enough - this saves memory and decoding time.
 */
object Bitmaps {

    /** Edge length to which imported logos are scaled down at most. */
    const val MAX_LOGO_SIZE_PX: Int = 512

    /**
     * Decodes [bytes] and scales down via `inSampleSize` to at most [maxSize] pixels edge
     * length.
     *
     * @return the bitmap or `null` if the data is not a decodable image.
     */
    fun decodeSampled(bytes: ByteArray, maxSize: Int = MAX_LOGO_SIZE_PX): Bitmap? {
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
    }

    /**
     * Smallest power of two with which the longer edge is at most [maxSize].
     *
     * The termination condition deliberately checks the **original edge** against [maxSize] and
     * not half of it: otherwise a 4000 pixel wide camera photo with an upper limit of 2048 would
     * remain completely unscaled - halved it would be 2000 and thus already under the limit, so
     * no halving happens at all. Practically all phone photos land exactly in this gap; they
     * would then arrive at ML Kit in full resolution and with quadruple the memory footprint.
     */
    fun calculateSampleSize(width: Int, height: Int, maxSize: Int): Int {
        if (maxSize <= 0) return 1
        var sampleSize = 1
        var longestEdge = maxOf(width, height)
        while (longestEdge > maxSize) {
            longestEdge /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    /** Centered crop with [fraction] of the original edge length. */
    fun centerCrop(source: Bitmap, fraction: Float): Bitmap? = crop(source, fraction, 0.5f, 0.5f)

    /**
     * Crops a sub-region.
     *
     * Background: on a camera photo, the barcode often only takes up a small part of the image.
     * ML Kit internally downscales the input, which causes the modules of a small QR code to
     * drop below the recognizable size. A crop contains the same pixels at a much smaller
     * overall size - the code survives the internal downscaling this way.
     *
     * @param fraction Edge length of the crop as a fraction of the original (between 0 and 1).
     * @param centerX Center of the crop as a fraction of the width.
     * @param centerY Center of the crop as a fraction of the height.
     * @return the crop or `null` if it would be too small to evaluate.
     */
    fun crop(source: Bitmap, fraction: Float, centerX: Float, centerY: Float): Bitmap? {
        if (fraction <= 0f || fraction >= 1f) return null

        val width = (source.width * fraction).toInt()
        val height = (source.height * fraction).toInt()
        if (width < MIN_CROP_PX || height < MIN_CROP_PX) return null

        val left = (source.width * centerX - width / 2f).toInt().coerceIn(0, source.width - width)
        val top = (source.height * centerY - height / 2f).toInt().coerceIn(0, source.height - height)

        return runCatching { Bitmap.createBitmap(source, left, top, width, height) }
            .getOrNull()
            // createBitmap can return the same instance; otherwise the caller would recycle
            // the original page while cleaning up.
            ?.takeIf { it !== source }
    }

    /**
     * Grayscale with boosted contrast.
     *
     * Camera photos suffer from shadows, reflections and tinted paper. ML Kit's binarizer
     * doesn't always cope with this; a desaturated image with stretched tonal values brings out
     * codes that are too low-contrast in the original.
     */
    fun highContrastGray(source: Bitmap, contrast: Float = DEFAULT_CONTRAST): Bitmap? = runCatching {
        val target = createBitmap(source.width, source.height)
        val filter = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(ColorMatrix(contrastMatrix(contrast)))
        }
        val paint = Paint().apply {
            isFilterBitmap = true
            colorFilter = ColorMatrixColorFilter(filter)
        }
        Canvas(target).drawBitmap(source, 0f, 0f, paint)
        target
    }.getOrNull()

    /** Stretches the tonal values around the image midpoint so the average brightness is preserved. */
    private fun contrastMatrix(scale: Float): FloatArray {
        val translate = (1f - scale) * MID_TONE
        return floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Below this edge length, a crop is too small to evaluate. */
    private const val MIN_CROP_PX = 160

    private const val DEFAULT_CONTRAST = 1.8f
    private const val MID_TONE = 128f
}
