package org.shadowgrove.passporta

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shadowgrove.passporta.data.importer.PdfImportStaging
import org.shadowgrove.passporta.ui.toUserMessage

/**
 * Invisible gateway for incoming passes.
 *
 * Intercepts `.pkpass` files (VIEW/SEND) as well as "Add to Google Wallet" links, imports them
 * fully offline and briefly reports the result back. PDFs are handled differently: instead of
 * importing right away, the file is handed to [MainActivity] for a preview, where the user
 * decides whether to import it into the wallet.
 *
 * [AppCompatActivity] instead of a plain `ComponentActivity` so the toast text respects a
 * language chosen in the settings, not just the device's system language.
 */
class ImportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Don't re-import on configuration changes.
        if (savedInstanceState != null) {
            finish()
            return
        }

        val application = application as PassPortaApplication

        val pdfUri = pdfSourceUriFrom(intent)
        if (pdfUri != null) {
            lifecycleScope.launch {
                openPdfPreview(application, pdfUri)
                finish()
            }
            return
        }

        lifecycleScope.launch {
            val result = application.passImporter.importFromIntent(intent)
            Toast.makeText(
                this@ImportActivity,
                result.toUserMessage(resources),
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }

    /**
     * Detects a PDF opened via `VIEW` (file manager, browser download, "Open with") or shared
     * via `SEND` (share sheet).
     */
    private fun pdfSourceUriFrom(intent: Intent): Uri? {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)

            else -> null
        } ?: return null

        // MIME type first (reliable for VIEW/SEND), file extension as a fallback for sources
        // that report a generic type such as application/octet-stream.
        val mimeType = intent.type ?: contentResolver.getType(uri)
        val isPdf = mimeType == PDF_MIME_TYPE || uri.toString().endsWith(".pdf", ignoreCase = true)
        return uri.takeIf { isPdf }
    }

    /**
     * Stages [source] into the app's own cache and forwards it to [MainActivity] for preview.
     *
     * This activity has no UI of its own, so the actual preview - and the decision whether to
     * import - happens in [MainActivity]'s Compose UI instead.
     */
    private suspend fun openPdfPreview(application: PassPortaApplication, source: Uri) {
        val staged = withContext(Dispatchers.IO) {
            PdfImportStaging.stage(application, source, application.documentSource)
        }
        if (staged == null) {
            Toast.makeText(this, R.string.import_error_read_failed, Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putPdfPreviewUri(staged)
        }
        startActivity(intent)
    }

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
    }
}

