package org.shadowgrove.passporta.data.backup

import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.data.local.entity.PassBarcodeEntity
import org.shadowgrove.passporta.data.local.entity.PassEntity
import org.shadowgrove.passporta.data.local.entity.PassFieldEntity
import org.shadowgrove.passporta.data.local.entity.PassFieldSection
import org.shadowgrove.passporta.data.local.entity.PassSource
import org.shadowgrove.passporta.data.local.model.PassWithFields
import org.shadowgrove.passporta.data.settings.AppSettings
import org.shadowgrove.passporta.data.settings.AppThemeColor
import org.shadowgrove.passporta.data.settings.AppThemeMode
import org.shadowgrove.passporta.data.settings.SettingsStore

/** Converts a loaded pass into its backup representation. */
internal fun PassWithFields.toBackupPass(): BackupPass = BackupPass(
    id = pass.id,
    folderName = pass.folderName,
    isFavorite = pass.isFavorite,
    title = pass.title,
    subtitle = pass.subtitle,
    ownerName = pass.ownerName,
    identifier = pass.identifier,
    barcodeData = pass.barcodeData,
    barcodeType = pass.barcodeType.storageKey,
    barcodeAltText = pass.barcodeAltText,
    barcodeEcc = pass.barcodeEcc,
    barcodeEncoding = pass.barcodeEncoding,
    barcodes = orderedBarcodes.map { barcode ->
        BackupBarcode(
            barcodeData = barcode.barcodeData,
            barcodeType = barcode.barcodeType.storageKey,
            barcodeAltText = barcode.barcodeAltText,
            barcodeEcc = barcode.barcodeEcc,
            barcodeEncoding = barcode.barcodeEncoding,
            position = barcode.position,
        )
    },
    backgroundColor = pass.backgroundColor,
    logoPath = pass.logoPath,
    iconKey = pass.iconKey,
    heroImagePath = pass.heroImagePath,
    originalFilePath = pass.originalFilePath,
    originalFileName = pass.originalFileName,
    originalMimeType = pass.originalMimeType,
    expirationDate = pass.expirationDate,
    startDate = pass.startDate,
    location = pass.location,
    locationLatitude = pass.locationLatitude,
    locationLongitude = pass.locationLongitude,
    source = pass.source.storageKey,
    createdAt = pass.createdAt,
    updatedAt = pass.updatedAt,
    fields = orderedFields.map { field ->
        BackupField(
            label = field.label,
            value = field.value,
            section = field.section.storageKey,
            position = field.position,
        )
    },
)

/**
 * Rebuilds the [PassEntity] for a restored pass.
 *
 * Asset paths are passed in separately rather than taken from the backup as-is: the archive's
 * paths are only a hint of where the file *used to* live, restoring an asset may reject or
 * relocate them, and a failed asset write must not silently claim a path that isn't there.
 */
internal fun BackupPass.toPassEntity(
    logoPath: String?,
    heroImagePath: String?,
    originalFilePath: String?,
): PassEntity = PassEntity(
    id = id,
    folderName = folderName,
    isFavorite = isFavorite,
    title = title,
    subtitle = subtitle,
    ownerName = ownerName,
    identifier = identifier,
    barcodeData = barcodeData,
    barcodeType = BarcodeType.fromKey(barcodeType),
    barcodeAltText = barcodeAltText,
    barcodeEcc = barcodeEcc,
    barcodeEncoding = barcodeEncoding,
    backgroundColor = backgroundColor,
    logoPath = logoPath,
    iconKey = iconKey,
    heroImagePath = heroImagePath,
    originalFilePath = originalFilePath,
    originalFileName = originalFileName,
    originalMimeType = originalMimeType,
    expirationDate = expirationDate,
    startDate = startDate,
    location = location,
    locationLatitude = locationLatitude,
    locationLongitude = locationLongitude,
    source = PassSource.fromKey(source),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun BackupBarcode.toPassBarcodeEntity(passId: String, position: Int): PassBarcodeEntity =
    PassBarcodeEntity(
        passId = passId,
        barcodeData = barcodeData,
        barcodeType = BarcodeType.fromKey(barcodeType),
        barcodeAltText = barcodeAltText,
        barcodeEcc = barcodeEcc,
        barcodeEncoding = barcodeEncoding,
        position = position,
    )

internal fun BackupField.toPassFieldEntity(passId: String): PassFieldEntity = PassFieldEntity(
    passId = passId,
    label = label,
    value = value,
    section = PassFieldSection.fromKey(section),
    position = position,
)

internal fun AppSettings.toBackupSettings(): BackupSettings = BackupSettings(
    themeColor = themeColor.key,
    themeMode = themeMode.key,
    useDynamicColor = useDynamicColor,
    openBarcodeFullscreen = openBarcodeFullscreen,
    showExpiredInFolders = showExpiredInFolders,
    showUpcomingInFolders = showUpcomingInFolders,
    defaultOwnerName = defaultOwnerName,
    defaultFolderName = defaultFolderName,
    rollingBackupCount = rollingBackupCount,
)

/** Applies a restored settings block through the store's normal setters. */
internal fun SettingsStore.restore(settings: BackupSettings) {
    settings.themeColor?.let { setThemeColor(AppThemeColor.fromKey(it)) }
    settings.themeMode?.let { setThemeMode(AppThemeMode.fromKey(it)) }
    setUseDynamicColor(settings.useDynamicColor)
    setOpenBarcodeFullscreen(settings.openBarcodeFullscreen)
    setShowExpiredInFolders(settings.showExpiredInFolders)
    setShowUpcomingInFolders(settings.showUpcomingInFolders)
    settings.defaultOwnerName?.let { setDefaultOwnerName(it) }
    settings.defaultFolderName?.let { setDefaultFolderName(it) }
    setRollingBackupCount(settings.rollingBackupCount)
}


