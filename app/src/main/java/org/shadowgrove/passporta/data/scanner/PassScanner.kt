package org.shadowgrove.passporta.data.scanner

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.util.BarcodeParameterReader
import org.shadowgrove.passporta.util.Bitmaps
import org.shadowgrove.passporta.util.DecodedBarcode
import com.google.zxing.BarcodeFormat as ZxingFormat

/**
 * Extracts barcode and text data from images and PDFs - exclusively on-device.
 *
 * The **bundled** ML Kit models are deliberately used
 * (`com.google.mlkit:barcode-scanning`, not `play-services-mlkit-*`): only these work without
 * network access and without downloading additional data via Play services.
 */
class PassScanner(
    private val pageRenderer: PageRenderer,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val barcodeScanner: BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build(),
        )
    }

    private val textRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Analyzes the source behind [uri].
     *
     * The first page with a barcode is evaluated; if none is found, the first page serves as
     * the text source. This keeps the effort low even for multi-page PDFs.
     */
    suspend fun scan(uri: Uri): ScannedPass = withContext(dispatcher) {
        val pages = pageRenderer.render(uri)
        if (pages.isEmpty()) {
            // Mostly happens with camera captures: several manufacturer cameras report success
            // but create an empty target file. Without its own message, the user would just see
            // an empty form and not know why.
            Log.w(TAG, "Source produced no evaluable page: $uri")
            return@withContext ScannedPass(sourceUnreadable = true)
        }

        try {
            val hits = findBarcodes(pages)
            val hit = hits.firstOrNull()
            val page = hit?.page ?: pages.first()
            val lines = recognizeOriented(page, preferred = hit?.rotationDegrees ?: UPRIGHT)

            ScanTextInterpreter
                .interpret(lines, barcodeData = hit?.value)
                .copy(
                    barcodeData = hit?.value,
                    barcodeType = hit?.type,
                    barcodeEcc = hit?.errorCorrection,
                    barcodes = hits
                        .map { ScannedBarcode(data = it.value, type = it.type, ecc = it.errorCorrection) }
                        .distinct(),
                )
        } catch (error: Exception) {
            Log.w(TAG, "Analysis failed", error)
            ScannedPass(sourceUnreadable = true)
        } finally {
            pages.forEach { it.recycle() }
        }
    }

    /**
     * Location of a found barcode.
     *
     * [page] is always the full page - even when the code was only found in a crop. Text
     * recognition needs the whole document after all.
     */
    private class BarcodeHit(
        val page: Bitmap,
        val value: String,
        val type: BarcodeType,
        val rotationDegrees: Int,
        val errorCorrection: String?,
    )

    /** A search region as a fraction of the image area. */
    private class CropRegion(val fraction: Float, val centerX: Float, val centerY: Float)

    /**
     * Searches for all readable barcodes in several stages.
     *
     * The order is sorted by hit probability and cost; later stages only run if nothing was
     * found before. A single stage may already yield several distinct barcodes at once (e.g. a
     * pass with multiple codes on one page); in that case the remaining, more expensive stages
     * are skipped entirely.
     *
     * 1. All pages upright - the normal case for PDFs and screenshots.
     * 2. The common misrotations of the first page. This mostly helps 1D codes; ML Kit
     *    recognizes QR and Aztec codes regardless of orientation anyway.
     * 3. Crops. This is the most important step for camera photos: there, the code often only
     *    takes up a fraction of the image and gets lost in ML Kit's internal downscaling.
     * 4. Grayscale with boosted contrast - against shadows and reflections.
     * 5. ZXing as a second opinion; the two libraries fail on different images.
     */
    private suspend fun findBarcodes(pages: List<Bitmap>): List<BarcodeHit> {
        val fromPages = pages.flatMap { page -> analyse(page, page, UPRIGHT) }
        if (fromPages.isNotEmpty()) return dedupeHits(fromPages)

        val first = pages.first()

        FALLBACK_ROTATIONS.forEach { rotation ->
            val hits = analyse(first, first, rotation)
            if (hits.isNotEmpty()) return dedupeHits(hits)
        }

        CROP_REGIONS.forEach { region ->
            val crop = Bitmaps.crop(first, region.fraction, region.centerX, region.centerY)
                ?: return@forEach
            try {
                val hits = analyse(crop, first, UPRIGHT)
                if (hits.isNotEmpty()) return dedupeHits(hits)
            } finally {
                crop.recycle()
            }
        }

        Bitmaps.highContrastGray(first)?.let { enhanced ->
            try {
                val hits = analyse(enhanced, first, UPRIGHT)
                if (hits.isNotEmpty()) return dedupeHits(hits)
            } finally {
                enhanced.recycle()
            }
        }

        return decodeWithZxing(first)?.let { listOf(it) } ?: emptyList()
    }

    /** Collapses barcodes with identical payload and format across pages/regions. */
    private fun dedupeHits(hits: List<BarcodeHit>): List<BarcodeHit> {
        val seen = mutableSetOf<Pair<String, BarcodeType>>()
        return hits.filter { seen.add(it.value to it.type) }
    }

    /** An ML Kit pass over [candidate]; every hit is attributed to [page]. */
    private suspend fun analyse(candidate: Bitmap, page: Bitmap, rotationDegrees: Int): List<BarcodeHit> {
        val found = barcodeScanner
            .process(InputImage.fromBitmap(candidate, rotationDegrees))
            .await()
            .mapNotNull { barcode -> barcode.value()?.let { barcode.format to it } }
        if (found.isEmpty()) return emptyList()

        // The crop in which the codes were found is also the best template for ZXing's
        // error-correction reader - and it only lives until the end of this call.
        val errorCorrection = BarcodeParameterReader.read(candidate)?.errorCorrection

        return found.map { (format, value) ->
            BarcodeHit(
                page = page,
                value = value,
                type = toBarcodeType(format),
                rotationDegrees = rotationDegrees,
                errorCorrection = errorCorrection,
            )
        }
    }

    /** Last attempt with ZXing, on the full page and on its center. */
    private fun decodeWithZxing(page: Bitmap): BarcodeHit? {
        BarcodeParameterReader.decode(page)?.let { return it.toHit(page) }

        val crop = Bitmaps.centerCrop(page, ZXING_CROP_FRACTION) ?: return null
        return try {
            BarcodeParameterReader.decode(crop)?.toHit(page)
        } finally {
            crop.recycle()
        }
    }

    private fun DecodedBarcode.toHit(page: Bitmap) = BarcodeHit(
        page = page,
        value = value,
        type = toBarcodeType(format),
        rotationDegrees = UPRIGHT,
        errorCorrection = errorCorrection,
    )

    /**
     * Text recognition in the most plausible reading direction.
     *
     * [preferred] is only a starting value, not a fixed choice: ML Kit finds QR and Aztec codes
     * regardless of image orientation, so a sideways photo doesn't even show up in the barcode
     * step. If text recognition simply adopted this orientation, it would run sideways across
     * the lines and only yield disconnected characters - the barcode would be there, but all
     * other data would be missing. That's why the text always relies on comparing all four
     * orientations.
     */
    private suspend fun recognizeOriented(page: Bitmap, preferred: Int): List<ScannedLine> {
        var best = emptyList<ScannedLine>()
        var bestScore = 0

        (listOf(preferred) + ALL_ROTATIONS.toList()).distinct().forEach { rotation ->
            val lines = recognizeLines(page, rotation)
            val score = ScanTextInterpreter.orientationScore(lines)
            if (score > bestScore) {
                best = lines
                bestScore = score
            }
            // A clearly readable document already yields enough text in the first orientation.
            if (bestScore >= CONFIDENT_TEXT_SCORE) return best
        }
        return best
    }

    private suspend fun recognizeLines(page: Bitmap, rotationDegrees: Int): List<ScannedLine> {
        val text = textRecognizer
            .process(InputImage.fromBitmap(page, rotationDegrees))
            .await()
        return text.textBlocks
            .asSequence()
            .flatMap { block -> block.lines.asSequence() }
            .mapNotNull { line ->
                // The bounding boxes are already in the upright coordinate system, because ML
                // Kit applies the rotation itself.
                val box = line.boundingBox ?: return@mapNotNull null
                ScannedLine(text = line.text, height = box.height(), top = box.top)
            }
            .sortedBy { it.top }
            .toList()
    }

    private companion object {
        const val TAG = "PassScanner"

        /** Unchanged orientation - how PDF pages and screenshots are provided. */
        const val UPRIGHT = 0

        /** Common misrotations for camera photos. */
        val FALLBACK_ROTATIONS = intArrayOf(90, 270, 180)

        val ALL_ROTATIONS = intArrayOf(UPRIGHT) + FALLBACK_ROTATIONS

        /**
         * Search regions for camera photos: first the center at two zoom levels, then the four
         * overlapping quadrants.
         */
        val CROP_REGIONS = listOf(
            CropRegion(0.55f, 0.5f, 0.5f),
            CropRegion(0.35f, 0.5f, 0.5f),
            CropRegion(0.6f, 0.3f, 0.3f),
            CropRegion(0.6f, 0.7f, 0.3f),
            CropRegion(0.6f, 0.3f, 0.7f),
            CropRegion(0.6f, 0.7f, 0.7f),
        )

        const val ZXING_CROP_FRACTION = 0.6f

        /** From this score onward, the orientation is unambiguous enough. */
        const val CONFIDENT_TEXT_SCORE = 80
    }
}

/**
 * Payload of a recognized code.
 *
 * `rawValue` is `null` if the content is not decodable as text; `displayValue` then often still
 * provides a usable version. Without this fallback, an actually recognized code would be
 * discarded.
 */
private fun Barcode.value(): String? =
    rawValue?.takeIf { it.isNotBlank() } ?: displayValue?.takeIf { it.isNotBlank() }

/**
 * Maps ML Kit formats to the types PassPorta can render.
 *
 * Formats outside the supported ones (Code 39, ...) are mapped to CODE128: it can encode the same
 * characters and is widely used at checkouts. The user can correct the type in the form.
 */
private fun toBarcodeType(format: Int): BarcodeType = when (format) {
    Barcode.FORMAT_QR_CODE -> BarcodeType.QR
    Barcode.FORMAT_AZTEC -> BarcodeType.AZTEC
    Barcode.FORMAT_PDF417 -> BarcodeType.PDF417
    Barcode.FORMAT_DATA_MATRIX -> BarcodeType.DATA_MATRIX
    Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E -> BarcodeType.UPC
    Barcode.FORMAT_EAN_8, Barcode.FORMAT_EAN_13 -> BarcodeType.EAN
    Barcode.FORMAT_ITF -> BarcodeType.ITF
    else -> BarcodeType.CODE128
}

/** Same for the ZXing formats of the second pass. */
private fun toBarcodeType(format: ZxingFormat): BarcodeType = when (format) {
    ZxingFormat.QR_CODE -> BarcodeType.QR
    ZxingFormat.DATA_MATRIX -> BarcodeType.DATA_MATRIX
    ZxingFormat.AZTEC -> BarcodeType.AZTEC
    ZxingFormat.PDF_417 -> BarcodeType.PDF417
    ZxingFormat.UPC_A, ZxingFormat.UPC_E -> BarcodeType.UPC
    ZxingFormat.EAN_8, ZxingFormat.EAN_13 -> BarcodeType.EAN
    ZxingFormat.ITF -> BarcodeType.ITF
    else -> BarcodeType.CODE128
}
