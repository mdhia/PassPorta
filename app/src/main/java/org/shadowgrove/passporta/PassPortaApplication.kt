package org.shadowgrove.passporta

import android.app.Application
import org.shadowgrove.passporta.data.backup.BackupManager
import org.shadowgrove.passporta.data.backup.AutomaticBackupCoordinator
import org.shadowgrove.passporta.data.importer.LocalDocumentSource
import org.shadowgrove.passporta.data.importer.PassAssetStore
import org.shadowgrove.passporta.data.importer.PassImporter
import org.shadowgrove.passporta.data.local.PassDatabase
import org.shadowgrove.passporta.data.repository.PassRepository
import org.shadowgrove.passporta.data.scanner.PageRenderer
import org.shadowgrove.passporta.data.scanner.PassScanner
import org.shadowgrove.passporta.data.settings.SettingsStore
import org.shadowgrove.passporta.data.security.BackupPasswordStore

/**
 * Application class acting as a lightweight service locator.
 *
 * Deliberately without a DI framework: the app has few dependencies and is meant to stay small
 * and offline.
 */
class PassPortaApplication : Application() {

    val database: PassDatabase by lazy { PassDatabase.getInstance(this) }

    val passRepository: PassRepository by lazy { PassRepository(database.passDao()) }

    /** Storage for imported logos and original documents under `filesDir`. */
    val passAssetStore: PassAssetStore by lazy { PassAssetStore(filesDir) }

    /** Read access to files chosen by the user (logo upload, original storage). */
    val documentSource: LocalDocumentSource by lazy { LocalDocumentSource(this) }

    /** Renders images and PDF pages - for scanning as well as the document view. */
    val pageRenderer: PageRenderer by lazy { PageRenderer(this) }

    val passImporter: PassImporter by lazy {
        PassImporter(
            context = this,
            repository = passRepository,
            assetStore = passAssetStore,
            documentSource = documentSource,
            automaticBackupCoordinator = automaticBackupCoordinator,
        )
    }

    /** Offline extraction from images and PDFs (ML Kit, bundled models). */
    val passScanner: PassScanner by lazy { PassScanner(pageRenderer) }

    /** User settings (color, appearance, behavior). */
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    /** Optional backup password, persisted only in Keystore-encrypted form. */
    val backupPasswordStore: BackupPasswordStore by lazy { BackupPasswordStore(this) }

    /** Full PKPASS-based data export/import (passes and settings) as a single `.zip` archive. */
    val backupManager: BackupManager by lazy {
        BackupManager(this, passRepository, passAssetStore, settingsStore, backupPasswordStore)
    }

    val automaticBackupCoordinator: AutomaticBackupCoordinator by lazy {
        AutomaticBackupCoordinator(settingsStore, backupManager)
    }
}
