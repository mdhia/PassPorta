package org.shadowgrove.passporta

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * Carries a staged PDF from [ImportActivity] to [MainActivity].
 *
 * A plain extra key instead of `Intent.data`: [MainActivity] is also launched normally (app
 * icon, Quick Settings tile) and must not misinterpret its own launch intent as a pending PDF.
 */
private const val EXTRA_PDF_PREVIEW_URI = "org.shadowgrove.passporta.extra.PDF_PREVIEW_URI"

fun Intent.putPdfPreviewUri(uri: Uri): Intent = apply {
    putExtra(EXTRA_PDF_PREVIEW_URI, uri)
}

fun Intent.pdfPreviewUri(): Uri? =
    IntentCompat.getParcelableExtra(this, EXTRA_PDF_PREVIEW_URI, Uri::class.java)

