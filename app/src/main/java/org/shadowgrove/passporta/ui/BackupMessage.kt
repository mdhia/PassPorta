package org.shadowgrove.passporta.ui

import android.content.res.Resources
import org.shadowgrove.passporta.R
import org.shadowgrove.passporta.data.backup.BackupFailure
import org.shadowgrove.passporta.data.backup.BackupResult

/** Formats a backup export/import result as a user message for a snackbar. */
fun BackupResult.toUserMessage(resources: Resources): String = when (this) {
    is BackupResult.ExportSuccess ->
        resources.getString(R.string.settings_backup_export_success, passCount)

    is BackupResult.ImportSuccess ->
        resources.getString(R.string.settings_backup_import_success, importedCount, updatedCount)

    is BackupResult.Failure -> resources.getString(
        when (reason) {
            BackupFailure.WRITE_FAILED -> R.string.settings_backup_error_write_failed
            BackupFailure.READ_FAILED -> R.string.settings_backup_error_read_failed
            BackupFailure.INVALID_FILE -> R.string.settings_backup_error_invalid_file
        },
    )
}

