package org.shadowgrove.passporta.data.scanner

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Covers the image processing of [PageRenderer].
 *
 * The trigger was a bug that went unnoticed for a long time: [android.graphics.BitmapFactory.decodeStream]
 * always returns `null` in `inJustDecodeBounds` mode. If this return value was used as a success
 * check, decoding of every image would abort immediately - visible both in the document preview
 * and when evaluating photos.
 */
@RunWith(AndroidJUnit4::class)
class PageRendererTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val renderer = PageRenderer(context)
    private val created = mutableListOf<File>()

    @After
    fun tearDown() {
        created.forEach { it.delete() }
    }

    @Test
    fun countsAnImageAsOnePage() {
        val uri = Uri.fromFile(writePng("bild.png", width = 200, height = 120))

        assertEquals(1, renderer.pageCount(uri))
    }

    @Test
    fun rendersAnImageAsPageZero() {
        val uri = Uri.fromFile(writePng("vorschau.png", width = 200, height = 120))

        val page = renderer.renderPage(uri, index = 0)

        assertNotNull("Image could not be rendered", page)
        assertTrue(page!!.width > 0 && page.height > 0)
        page.recycle()
    }

    /** Images only have one page - any further request must return nothing. */
    @Test
    fun knowsImagesHaveOnlyOnePage() {
        val uri = Uri.fromFile(writePng("einzeln.png", width = 64, height = 64))

        assertNull(renderer.renderPage(uri, index = 1))
    }

    @Test
    fun deliversImagesAlsoViaRender() {
        val uri = Uri.fromFile(writePng("analyse.png", width = 300, height = 200))

        val pages = renderer.render(uri)

        assertEquals(1, pages.size)
        pages.forEach { it.recycle() }
    }

    /**
     * A `.pkpass` archive is neither an image nor a PDF. It must not count as a displayable
     * page, otherwise the preview would first show a loading spinner and then fall back.
     */
    @Test
    fun reportsNonDisplayableFilesAsZeroPages() {
        val file = File(context.cacheDir, "kein-bild.pkpass").also {
            it.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00))
            created += it
        }

        assertEquals(0, renderer.pageCount(Uri.fromFile(file)))
    }

    /**
     * Preserved originals get the extension `.bin` if the source didn't provide a filename.
     * Format detection must therefore not rely on the extension.
     */
    @Test
    fun recognizesImagesRegardlessOfExtension() {
        val uri = Uri.fromFile(writePng("ohne-endung.bin", width = 120, height = 80))

        assertEquals(1, renderer.pageCount(uri))
        renderer.renderPage(uri, index = 0).also {
            assertNotNull("Extension must not decide the format", it)
            it?.recycle()
        }
    }

    private fun writePng(name: String, width: Int, height: Int): File {
        val bitmap = createBitmap(width, height).apply { eraseColor(Color.WHITE) }
        val file = File(context.cacheDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        created += file
        return file
    }
}

