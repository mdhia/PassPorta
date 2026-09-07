package org.shadowgrove.passporta.data.importer

import androidx.annotation.StringRes
import org.shadowgrove.passporta.R

/**
 * Result of an import operation.
 */
sealed interface PassImportResult {

    /**
     * At least one pass was saved.
     *
     * @param passIds ids of the newly created or updated passes.
     * @param updatedCount number of passes that replaced an existing entry.
     * @param integrityVerified `true`/`false` for `.pkpass` (manifest checksums), otherwise `null`.
     */
    data class Success(
        val passIds: List<String>,
        val updatedCount: Int = 0,
        val integrityVerified: Boolean? = null,
    ) : PassImportResult {

        val importedCount: Int get() = passIds.size
    }

    /** The import failed; [failure] describes the cause in a user-readable way. */
    data class Failure(
        val failure: ImportFailure,
        val cause: Throwable? = null,
    ) : PassImportResult
}

/**
 * Failure causes of the import including the corresponding user message.
 */
enum class ImportFailure(@get:StringRes val messageRes: Int) {

    /** Intent or URI contains nothing importable. */
    UNSUPPORTED_SOURCE(R.string.import_error_unsupported_source),

    /** File could not be read (permission revoked, URI expired, ...). */
    READ_ERROR(R.string.import_error_read_failed),

    /** Archive is not a valid `.pkpass` (missing or broken `pass.json`). */
    INVALID_PKPASS(R.string.import_error_invalid_pkpass),

    /** The wallet link contains no decodable JWT. */
    INVALID_JWT(R.string.import_error_invalid_jwt),

    /**
     * Short links like `https://pay.app.goo.gl/...` are pure redirects. Resolving them
     * requires network access - which PassPorta deliberately does not have.
     */
    SHORT_LINK_REQUIRES_NETWORK(R.string.import_error_short_link),

    /** Source was readable but contained no barcode - without it a pass is worthless. */
    NO_BARCODE(R.string.import_error_no_barcode),
}
