package org.shadowgrove.passporta.ui.model

import androidx.annotation.ColorInt
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.shadowgrove.passporta.data.importer.PassAssetStore
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.data.local.entity.PassEntity
import org.shadowgrove.passporta.data.local.entity.PassFieldEntity
import org.shadowgrove.passporta.data.local.entity.isExpired
import org.shadowgrove.passporta.data.local.model.PassWithFields
import org.shadowgrove.passporta.ui.icons.PassIcon
import org.shadowgrove.passporta.ui.icons.PassIconLibrary
import org.shadowgrove.passporta.util.PassColors
import java.io.File

/**
 * Color roles derived from a pass's background color.
 *
 * The contrast rule (light surface -> black text, dark surface -> white text) lives in
 * [PassColors] and is applied here once per pass instead of repeatedly in every composable.
 */
@Immutable
data class PassPalette(
    val background: Color,
    val content: Color,
    val secondaryContent: Color,
    val surfaceVariant: Color,
) {

    companion object {

        fun from(@ColorInt backgroundColor: Int): PassPalette {
            val opaque = PassColors.opaque(backgroundColor)
            return PassPalette(
                background = Color(opaque),
                content = Color(PassColors.contentColorFor(opaque)),
                secondaryContent = Color(PassColors.secondaryContentColorFor(opaque)),
                surfaceVariant = Color(PassColors.surfaceVariantFor(opaque)),
            )
        }
    }
}

/** A row of the field list in the detail view. */
@Immutable
data class PassFieldUi(
    val label: String?,
    val value: String,
)

/**
 * Display model of a pass.
 *
 * Keeps the UI free of Room entities and resolves logo path and color roles exactly once.
 */
@Immutable
data class PassUi(
    val id: String,
    val folderName: String,
    val title: String,
    val subtitle: String?,    val ownerName: String,
    val identifier: String?,
    val barcodeData: String,
    val barcodeType: BarcodeType,
    val barcodeAltText: String?,
    val barcodeEcc: String?,
    val barcodeEncoding: String?,
    val logoFile: File?,

    /** Chosen symbol from the icon library; applies when no logo is set. */
    val icon: PassIcon? = null,

    val heroImageFile: File?,
    val palette: PassPalette,

    /** Additional fields in display order; deliberately empty in the overview. */
    val fields: List<PassFieldUi> = emptyList(),

    val expirationDate: Long? = null,
    val isExpired: Boolean = false,

    /** Marked as favorite - additionally shows the pass on the favorites page. */
    val isFavorite: Boolean = false,

    val location: String? = null,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,

    /** Original document kept unchanged, if present. */
    val originalFile: File? = null,
    val originalFileName: String? = null,
    val originalMimeType: String? = null,
) {

    /** Fallback for the logo circle when no image is present. */
    val initials: String = title.toInitials()

    val hasSubtitle: Boolean = !subtitle.isNullOrBlank()

    /** True if a location can be displayed or opened in a maps app. */
    val hasLocation: Boolean =
        !location.isNullOrBlank() || (locationLatitude != null && locationLongitude != null)
}

fun PassEntity.toUi(
    assetStore: PassAssetStore,
    fields: List<PassFieldEntity> = emptyList(),
    now: Long = System.currentTimeMillis(),
): PassUi = PassUi(
    id = id,
    folderName = folderName,
    title = title,
    subtitle = subtitle,
    ownerName = ownerName,
    identifier = identifier,
    barcodeData = barcodeData,
    barcodeType = barcodeType,
    barcodeAltText = barcodeAltText,
    barcodeEcc = barcodeEcc,
    barcodeEncoding = barcodeEncoding,
    logoFile = assetStore.resolve(logoPath),
    icon = PassIconLibrary.byKey(iconKey),
    heroImageFile = assetStore.resolve(heroImagePath),
    palette = PassPalette.from(backgroundColor),
    fields = fields.map { PassFieldUi(label = it.label, value = it.value) },
    expirationDate = expirationDate,
    isExpired = isExpired(now),
    isFavorite = isFavorite,
    location = location,
    locationLatitude = locationLatitude,
    locationLongitude = locationLongitude,
    originalFile = assetStore.resolve(originalFilePath),
    originalFileName = originalFileName,
    originalMimeType = originalMimeType,
)

/** Converts a pass along with its fields into the display model of the detail view. */
fun PassWithFields.toUi(
    assetStore: PassAssetStore,
    now: Long = System.currentTimeMillis(),
): PassUi = pass.toUi(assetStore, orderedFields, now)

/** Up to two initial letters, e.g. "Beispiel Club" -> "BC". */
private fun String.toInitials(): String = trim()
    .split(' ', '-', '_')
    .filter { it.isNotBlank() }
    .take(2)
    .map { it.first().uppercaseChar() }
    .joinToString(separator = "")
    .ifEmpty { "?" }


