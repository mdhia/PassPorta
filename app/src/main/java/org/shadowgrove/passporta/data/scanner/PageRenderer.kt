package org.shadowgrove.passporta.data.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import org.shadowgrove.passporta.util.Bitmaps
import java.io.IOException

/**
 * Converts a user-selected source into bitmaps that ML Kit can analyze.
 *
 * Supports images (screenshots, photos) and PDFs (tickets from emails). PDFs are rendered with
 * Android's own [PdfRenderer] - no extra rendering framework needed.
 */
class PageRenderer(private val context: Context) {

    /**
     * @param maxPages upper limit so a 200-page PDF doesn't blow up memory.
     * @return rendered pages in order; empty if the source is unreadable.
     */
    fun render(uri: Uri, maxPages: Int = DEFAULT_MAX_PAGES): List<Bitmap> {
        return try {
            if (isPdf(uri)) {
                renderPdf(uri, maxPages)
            } else {
                listOfNotNull(renderImage(uri, ANALYSIS_MAX_EDGE_PX))
            }
        } catch (error: IOException) {
            Log.w(TAG, "Source could not be rendered", error)
            emptyList()
        } catch (error: SecurityException) {
            Log.w(TAG, "No access to the source", error)
            emptyList()
        }
    }

    /**
     * Number of pages; a displayable image counts as one page.
     *
     * @return 0 if the source is unreadable or not displayable (e.g. a `.pkpass` archive).
     *   Deliberately not a blanket 1 for anything that isn't a PDF: the UI derives from this
     *   whether it can render at all, and would otherwise only switch to the external "open"
     *   variant after a failed render attempt.
     */
    fun pageCount(uri: Uri): Int = try {
        if (isPdf(uri)) {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { it.pageCount }
            } ?: 0
        } else {
            if (imageBounds(uri) != null) 1 else 0
        }
    } catch (error: IOException) {
        Log.w(TAG, "Page count could not be determined", error)
        0
    } catch (error: SecurityException) {
        Log.w(TAG, "No access to the source", error)
        0
    }

    /**
     * Renders exactly one page.
     *
     * The display deliberately works page by page: holding a 20-page PDF entirely in bitmaps
     * would take up several hundred megabytes depending on resolution.
     *
     * @param maxEdgePx longest edge of the result.
     */
    fun renderPage(uri: Uri, index: Int, maxEdgePx: Int = VIEW_MAX_EDGE_PX): Bitmap? = try {
        if (isPdf(uri)) {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (index !in 0 until renderer.pageCount) {
                        null
                    } else {
                        renderer.renderPage(index, maxEdgePx)
                    }
                }
            }
        } else {
            if (index == 0) renderImage(uri, maxEdgePx) else null
        }
    } catch (error: IOException) {
        Log.w(TAG, "Page $index could not be rendered", error)
        null
    } catch (error: SecurityException) {
        Log.w(TAG, "No access to the source", error)
        null
    }

    /**
     * Detects a PDF by its signature.
     *
     * MIME type and file extension are not reliable: `ContentResolver.getType` often returns
     * nothing for `file://` URIs, and saved originals get the extension `.bin` if the source
     * didn't provide a filename (e.g. a camera capture). The first bytes, on the other hand, are
     * unambiguous - and reading five bytes is cheaper than any wrong decision.
     */
    private fun isPdf(uri: Uri): Boolean {
        val signature = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ByteArray(PDF_SIGNATURE.size).takeIf { stream.read(it) == it.size }
            }
        }.getOrNull()

        signature?.let { return it.contentEquals(PDF_SIGNATURE) }

        // Only if the file wasn't readable: fall back to the external characteristics.
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        return mimeType == PDF_MIME_TYPE || uri.toString().endsWith(".pdf", ignoreCase = true)
    }

    /**
     * Dimensions of an image without decoding it - or `null` if the source is not a readable
     * image.
     *
     * Note: [BitmapFactory.decodeStream] **always** returns `null` in `inJustDecodeBounds` mode.
     * The return value is therefore not usable here as a success check; only the determined
     * dimensions matter.
     */
    private fun imageBounds(uri: Uri): BitmapFactory.Options? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, options) }
        return options.takeIf { it.outWidth > 0 && it.outHeight > 0 }
    }

    private fun renderImage(uri: Uri, maxEdgePx: Int): Bitmap? {
        val bounds = imageBounds(uri) ?: return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = Bitmaps.calculateSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxSize = maxEdgePx,
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        return applyExifOrientation(uri, bitmap)
    }

    /**
     * Orients a camera photo based on its EXIF data.
     *
     * Cameras usually store the image in sensor orientation and only record the rotation as an
     * EXIF attribute. [BitmapFactory] ignores this attribute - a portrait photo would arrive at
     * ML Kit rotated by 90 degrees, which massively degrades text recognition.
     */
    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: return bitmap

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }

            else -> return bitmap
        }

        return runCatching {
            val rotated = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true,
            )
            // createBitmap can return the same instance if there is nothing to do.
            if (rotated != bitmap) bitmap.recycle()
            rotated
        }.getOrDefault(bitmap)
    }

    private fun renderPdf(uri: Uri, maxPages: Int): List<Bitmap> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()

        return descriptor.use { fileDescriptor ->
            PdfRenderer(fileDescriptor).use { renderer ->
                val pageCount = minOf(renderer.pageCount, maxPages)
                (0 until pageCount).mapNotNull { index ->
                    renderer.renderPage(index, ANALYSIS_MAX_EDGE_PX)
                }
            }
        }
    }

    private fun PdfRenderer.renderPage(index: Int, maxEdgePx: Int): Bitmap? = openPage(index).use { page ->
        if (page.width <= 0 || page.height <= 0) return null

        // Upscaling significantly improves barcode recognition: PDF pages are defined in points
        // (72 dpi) and are otherwise too coarse for ML Kit.
        val scale = (maxEdgePx.toFloat() / maxOf(page.width, page.height))
            .coerceIn(MIN_PDF_SCALE, MAX_PDF_SCALE)

        val bitmap = createBitmap((page.width * scale).toInt(), (page.height * scale).toInt())
        // PDF pages are transparent wherever nothing is drawn. Without a white background,
        // ML Kit would see black on black.
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap
    }

    private companion object {
        const val TAG = "PageRenderer"
        const val PDF_MIME_TYPE = "application/pdf"
        const val DEFAULT_MAX_PAGES = 5

        /** Signature at the start of every PDF file. */
        val PDF_SIGNATURE = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D) // "%PDF-"

        /** Longest edge of the analysis images - a compromise between recognition rate and memory. */
        const val ANALYSIS_MAX_EDGE_PX = 2048

        /** For pure display, much less resolution is enough. */
        const val VIEW_MAX_EDGE_PX = 1600

        const val MIN_PDF_SCALE = 1.5f
        const val MAX_PDF_SCALE = 4f
    }
}
