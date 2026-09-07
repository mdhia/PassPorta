package org.shadowgrove.passporta.data.importer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.annotation.ColorInt
import androidx.core.content.IntentCompat
import androidx.palette.graphics.Palette
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.shadowgrove.passporta.data.importer.pkpass.PkPassParser
import org.shadowgrove.passporta.data.importer.wallet.WalletLinkParser
import org.shadowgrove.passporta.data.local.entity.PassEntity
import org.shadowgrove.passporta.data.local.entity.PassFieldEntity
import org.shadowgrove.passporta.data.repository.PassRepository
import org.shadowgrove.passporta.util.Bitmaps
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.UUID

/** Unchanged original file of an import. */
private class OriginalDocument(
    val bytes: ByteArray,
    val fileName: String?,
    val mimeType: String?,
)

/**
 * Central entry point for all import paths: `.pkpass` files, intercepted wallet links, and
 * (from phase 5) scans.
 *
 * Division of labor: the parsers produce plain [PassDraft]s, this importer takes care of id
 * assignment, duplicates, logo files and the fallback color derived from the logo.
 */
class PassImporter(
    context: Context,
    private val repository: PassRepository,
    private val assetStore: PassAssetStore = PassAssetStore(context.filesDir),
    private val documentSource: LocalDocumentSource = LocalDocumentSource(context),
    private val pkPassParser: PkPassParser = PkPassParser(),
    private val walletLinkParser: WalletLinkParser = WalletLinkParser(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val appContext: Context = context.applicationContext

    /** Preferred language for localized `.pkpass` content. */
    private val preferredLanguage: String?
        get() = appContext.resources.configuration.locales
            .takeIf { !it.isEmpty }
            ?.get(0)
            ?.language

    /**
     * Evaluates an incoming intent (`VIEW` of a file or a link, `SEND` from a share action).
     */
    suspend fun importFromIntent(intent: Intent?): PassImportResult {
        val unsupported = PassImportResult.Failure(ImportFailure.UNSUPPORTED_SOURCE)
        if (intent == null) return unsupported

        val sharedStream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

        return when {
            intent.data != null -> importFromUri(intent.data!!)
            sharedStream != null -> importFromUri(sharedStream)
            !sharedText.isNullOrBlank() -> importFromLink(sharedText)
            else -> unsupported
        }
    }

    /**
     * Imports from a URI. Web URIs are treated as a wallet link, everything else as a
     * `.pkpass` archive.
     */
    suspend fun importFromUri(uri: Uri): PassImportResult {
        if (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
            return importFromLink(uri.toString())
        }
        return importPkPass(uri)
    }

    /** Reads a `.pkpass` archive behind [uri] and saves it. */
    suspend fun importPkPass(uri: Uri): PassImportResult = withContext(dispatcher) {
        runImport {
            // The data stream is needed twice: once for the parser and once to keep the
            // archive as the original document. URIs are not reliably re-readable, so the
            // content is fetched completely once.
            val bytes = documentSource.readBytes(uri)
                ?: throw PassImportException(ImportFailure.READ_ERROR, "Uri provided no data")

            val import = pkPassParser.parse(ByteArrayInputStream(bytes), preferredLanguage)

            val original = OriginalDocument(
                bytes = bytes,
                fileName = documentSource.displayName(uri) ?: "pass.pkpass",
                mimeType = documentSource.mimeType(uri) ?: PKPASS_MIME_TYPE,
            )

            val persisted = persist(listOf(import.draft), original)
            PassImportResult.Success(
                passIds = persisted.ids,
                updatedCount = persisted.updatedCount,
                integrityVerified = import.integrityVerified,
            )
        }
    }

    /** Evaluates an "Add to Google Wallet" link or the JWT it contains. */
    suspend fun importFromLink(rawLink: String): PassImportResult = withContext(dispatcher) {
        runImport {
            val persisted = persist(walletLinkParser.parse(rawLink), original = null)
            PassImportResult.Success(passIds = persisted.ids, updatedCount = persisted.updatedCount)
        }
    }

    /** Saves [drafts]; already known barcodes update the existing entry. */
    private suspend fun persist(
        drafts: List<PassDraft>,
        original: OriginalDocument?,
    ): PersistResult {
        val now = System.currentTimeMillis()
        val ids = mutableListOf<String>()
        var updatedCount = 0

        drafts.forEach { draft ->
            val existing = repository.findDuplicate(draft.barcodeData)
            if (existing != null) updatedCount++

            val id = existing?.id ?: UUID.randomUUID().toString()
            val logo = draft.logoImage?.let { Bitmaps.decodeSampled(it) }
            val logoPath = logo?.let { assetStore.saveLogo(id, it) } ?: existing?.logoPath
            val background = draft.backgroundColor
                ?: logo?.let { dominantColorOf(it) }
                ?: existing?.backgroundColor
                ?: PassEntity.DEFAULT_BACKGROUND_COLOR
            logo?.recycle()

            val hero = draft.heroImage?.let { Bitmaps.decodeSampled(it, HERO_MAX_SIZE_PX) }
            val heroPath = hero?.let { assetStore.saveHeroImage(id, it) } ?: existing?.heroImagePath
            hero?.recycle()

            // Only a single pass may claim the original - with a wallet token containing
            // multiple objects, the assignment would otherwise be arbitrary.
            val originalPath = original
                ?.takeIf { drafts.size == 1 }
                ?.let { assetStore.saveOriginal(id, it.bytes, it.fileName) }
                ?: existing?.originalFilePath

            repository.saveWithFields(
                pass = PassEntity(
                    id = id,
                    folderName = draft.folderName,
                    // A repeated import must not clear the star - it's a user decision and
                    // is not part of any import source.
                    isFavorite = existing?.isFavorite == true,
                    title = draft.title,
                    subtitle = draft.subtitle,
                    ownerName = draft.ownerName,
                    identifier = draft.identifier,
                    barcodeData = draft.barcodeData,
                    barcodeType = draft.barcodeType,
                    barcodeAltText = draft.barcodeAltText,
                    barcodeEcc = draft.barcodeEcc ?: existing?.barcodeEcc,
                    barcodeEncoding = draft.barcodeEncoding ?: existing?.barcodeEncoding,
                    backgroundColor = background,
                    logoPath = logoPath,
                    heroImagePath = heroPath,
                    originalFilePath = originalPath,
                    originalFileName = original?.fileName ?: existing?.originalFileName,
                    originalMimeType = original?.mimeType ?: existing?.originalMimeType,
                    expirationDate = draft.expirationDate ?: existing?.expirationDate,
                    location = draft.location ?: existing?.location,
                    locationLatitude = draft.locationLatitude ?: existing?.locationLatitude,
                    locationLongitude = draft.locationLongitude ?: existing?.locationLongitude,
                    source = draft.source,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
                fields = draft.fields.mapIndexed { index, field ->
                    PassFieldEntity(
                        passId = id,
                        label = field.label,
                        value = field.value,
                        section = field.section,
                        position = index,
                    )
                },
                now = now,
            )
            ids += id
        }
        return PersistResult(ids, updatedCount)
    }

    /**
     * Prominent color of the logo as the card background when the source doesn't provide a
     * color. Prefers strong tones, so the card stays recognizable in the overview.
     */
    @ColorInt
    private fun dominantColorOf(bitmap: Bitmap): Int? = runCatching {
        val palette = Palette.from(bitmap).maximumColorCount(PALETTE_COLOR_COUNT).generate()
        val swatch = palette.vibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.dominantSwatch
        swatch?.rgb
    }.getOrNull()

    /** Translates parser exceptions into a [PassImportResult.Failure]. */
    private inline fun runImport(block: () -> PassImportResult): PassImportResult = try {
        block()
    } catch (error: PassImportException) {
        Log.w(TAG, "Import failed: ${error.failure}", error)
        PassImportResult.Failure(error.failure, error)
    } catch (error: SecurityException) {
        Log.w(TAG, "No access to the source", error)
        PassImportResult.Failure(ImportFailure.READ_ERROR, error)
    } catch (error: IOException) {
        Log.w(TAG, "Source unreadable", error)
        PassImportResult.Failure(ImportFailure.READ_ERROR, error)
    }

    private class PersistResult(val ids: List<String>, val updatedCount: Int)

    private companion object {
        const val TAG = "PassImporter"

        /** A small palette is enough: logos have few dominant colors. */
        const val PALETTE_COLOR_COUNT = 16

        /** Hero images are wide banners and need more resolution than a logo. */
        const val HERO_MAX_SIZE_PX = 1024

        const val PKPASS_MIME_TYPE = "application/vnd.apple.pkpass"
    }
}
