package org.shadowgrove.passporta.data.importer

import java.io.IOException

/**
 * Internal exception of the parsers. The [PassImporter] catches it and translates it into a
 * [PassImportResult.Failure].
 */
class PassImportException(
    val failure: ImportFailure,
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message ?: failure.name, cause)
