package org.shadowgrove.passporta.data.local.entity

import androidx.annotation.ColorInt
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A saved pass (ticket, boarding pass, loyalty card, ...).
 *
 * All content is exclusively local: images are stored as a relative path under
 * `Context.filesDir`, so the database stays small and backups/deletions stay simple.
 */
@Entity(
    tableName = "passes",
    indices = [
        Index(value = ["folder_name"]),
        Index(value = ["updated_at"]),
        Index(value = ["barcode_data"]),
    ],
)
data class PassEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    /** Logical folder of the overview, e.g. "Loyalty Cards" or "Flights". */
    @ColumnInfo(name = "folder_name")
    val folderName: String = DEFAULT_FOLDER,

    /**
     * Marked as favorite.
     *
     * Favorites form their own, folder-spanning page of the overview and are therefore
     * deliberately a marker on the pass and not another folder: a pass stays in its folder and
     * is additionally reachable under favorites.
     */
    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "subtitle")
    val subtitle: String? = null,

    @ColumnInfo(name = "owner_name")
    val ownerName: String,

    /** Card/customer number or other visible identifier. */
    @ColumnInfo(name = "identifier")
    val identifier: String? = null,

    /** Barcode payload (exactly as it needs to be encoded). */
    @ColumnInfo(name = "barcode_data")
    val barcodeData: String,

    @ColumnInfo(name = "barcode_type")
    val barcodeType: BarcodeType = BarcodeType.DEFAULT,

    /** Optional plain text shown below the barcode (pkpass: `altText`). */
    @ColumnInfo(name = "barcode_alt_text")
    val barcodeAltText: String? = null,

    /**
     * Error correction level of the original (QR: `L`/`M`/`Q`/`H`, PDF417/Aztec: number).
     *
     * Read during import or scan, so the generated code follows the same module grid as the
     * scanned one.
     */
    @ColumnInfo(name = "barcode_ecc")
    val barcodeEcc: String? = null,

    /** Character set of the barcode payload (pkpass: `messageEncoding`, usually `iso-8859-1`). */
    @ColumnInfo(name = "barcode_encoding")
    val barcodeEncoding: String? = null,

    /** Card background as an ARGB value; always stored opaque. */
    @ColumnInfo(name = "background_color")
    @ColorInt
    val backgroundColor: Int = DEFAULT_BACKGROUND_COLOR,

    /** Relative path of the locally stored logo, e.g. `logos/<id>.png`. */
    @ColumnInfo(name = "logo_path")
    val logoPath: String? = null,

    /**
     * Key of a symbol from the icon library, if no own logo is set.
     *
     * Only the key is stored, not the graphic: this keeps the entry tiny and the display
     * automatically follows the theme (size, color, later icon updates).
     */
    @ColumnInfo(name = "icon_key")
    val iconKey: String? = null,

    /** Relative path of the hero/strip image, e.g. `heroes/<id>.png`. */
    @ColumnInfo(name = "hero_image_path")
    val heroImagePath: String? = null,

    /**
     * Relative path of the original file (`.pkpass`, PDF or image) the pass was created from.
     *
     * It is kept unchanged, so the user can view the source document at any time - e.g. terms
     * or a seating plan that PassPorta itself doesn't render.
     */
    @ColumnInfo(name = "original_file_path")
    val originalFilePath: String? = null,

    /** Original file name, only for display. */
    @ColumnInfo(name = "original_file_name")
    val originalFileName: String? = null,

    @ColumnInfo(name = "original_mime_type")
    val originalMimeType: String? = null,

    /** Expiration time in milliseconds; `null` = valid indefinitely. */
    @ColumnInfo(name = "expiration_date")
    val expirationDate: Long? = null,

    /**
     * Start of validity in milliseconds; `null` = valid from the start.
     *
     * A pass with a future [startDate] is not yet usable and therefore gets its own page
     * ("Bevorstehend") instead of showing up among the currently valid passes - the mirror
     * image of [expirationDate] and the archive.
     */
    @ColumnInfo(name = "start_date")
    val startDate: Long? = null,

    /** Location in plain text, e.g. venue address. */
    @ColumnInfo(name = "location")
    val location: String? = null,

    @ColumnInfo(name = "location_latitude")
    val locationLatitude: Double? = null,

    @ColumnInfo(name = "location_longitude")
    val locationLongitude: Double? = null,

    @ColumnInfo(name = "source")
    val source: PassSource = PassSource.DEFAULT,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = createdAt,
) {

    companion object {
        const val DEFAULT_FOLDER: String = "Allgemein"

        /** Folder where expired passes are collected. */
        const val ARCHIVE_FOLDER: String = "Archiv"

        /** Neutral blue-gray as a fallback if no color could be extracted. */
        @ColorInt
        val DEFAULT_BACKGROUND_COLOR: Int = 0xFF37474F.toInt()
    }
}

/** True if a subtitle should be displayed (controls the card layout in phase 3). */
val PassEntity.hasSubtitle: Boolean
    get() = !subtitle.isNullOrBlank()

/**
 * True if the pass is expired at time [now].
 *
 * Passes without an expiration date always count as valid.
 */
fun PassEntity.isExpired(now: Long = System.currentTimeMillis()): Boolean {
    val expiresAt = expirationDate ?: return false
    return expiresAt < now
}

/**
 * True if the pass's validity only starts in the future at time [now].
 *
 * Once [startDate] has passed, the pass behaves like any other - it no longer counts as
 * upcoming even if it stays selected here.
 */
fun PassEntity.isUpcoming(now: Long = System.currentTimeMillis()): Boolean {
    val startsAt = startDate ?: return false
    return startsAt > now
}

