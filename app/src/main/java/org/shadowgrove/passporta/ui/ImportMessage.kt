package org.shadowgrove.passporta.ui

import android.content.res.Resources
import org.shadowgrove.passporta.R
import org.shadowgrove.passporta.data.importer.PassImportResult

/**
 * Formats an import result as a user message.
 *
 * Used both by [org.shadowgrove.passporta.ImportActivity] (toast) and by the overview (snackbar).
 */
fun PassImportResult.toUserMessage(resources: Resources): String = when (this) {
    is PassImportResult.Failure -> resources.getString(failure.messageRes)

    is PassImportResult.Success -> buildString {
        append(resources.getQuantityString(R.plurals.import_success, importedCount, importedCount))

        if (updatedCount > 0) {
            append(' ')
            append(
                resources.getQuantityString(R.plurals.import_updated, updatedCount, updatedCount),
            )
        }
        if (integrityVerified == false) {
            append('\n')
            append(resources.getString(R.string.import_integrity_warning))
        }
    }
}
