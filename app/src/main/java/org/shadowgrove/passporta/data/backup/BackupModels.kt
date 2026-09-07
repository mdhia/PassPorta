package org.shadowgrove.passporta.data.backup

import kotlinx.serialization.Serializable

/** A single label-value field of a pass, as stored in a backup archive. */
@Serializable
internal data class BackupField(
    val label: String? = null,
    val value: String,
    val section: String,
    val position: Int = 0,
)

/**
 * A pass as stored in a backup archive.
 *
 * Mirrors [org.shadowgrove.passporta.data.local.entity.PassEntity] field for field, except that
 * enums are stored as their stable `storageKey` string (readable, and independent of any later
 * reordering of the enum constants) and asset paths point at entries inside the same archive
 * rather than at `Context.filesDir`.
 */
@Serializable
internal data class BackupPass(
    val id: String,
    val folderName: String,
    val isFavorite: Boolean = false,
    val title: String,
    val subtitle: String? = null,
    val ownerName: String,
    val identifier: String? = null,
    val barcodeData: String,
    val barcodeType: String,
    val barcodeAltText: String? = null,
    val barcodeEcc: String? = null,
    val barcodeEncoding: String? = null,
    val backgroundColor: Int,
    val logoPath: String? = null,
    val iconKey: String? = null,
    val heroImagePath: String? = null,
    val originalFilePath: String? = null,
    val originalFileName: String? = null,
    val originalMimeType: String? = null,
    val expirationDate: Long? = null,
    val startDate: Long? = null,
    val location: String? = null,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val fields: List<BackupField> = emptyList(),
)

/** The handful of app settings worth carrying over to another device. */
@Serializable
internal data class BackupSettings(
    val themeColor: String? = null,
    val themeMode: String? = null,
    val useDynamicColor: Boolean = false,
    val openBarcodeFullscreen: Boolean = false,
    val showExpiredInFolders: Boolean = false,
    val showUpcomingInFolders: Boolean = false,
)

/**
 * Top-level content of a backup archive's `backup.json` entry.
 *
 * @param formatVersion lets a later app version detect and, if needed, migrate older backups.
 */
@Serializable
internal data class BackupManifest(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAt: Long,
    val passes: List<BackupPass>,
    val settings: BackupSettings? = null,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION: Int = 1
    }
}

