package org.shadowgrove.passporta

import android.app.Application
import org.shadowgrove.passporta.data.backup.BackupManager
import org.shadowgrove.passporta.data.importer.LocalDocumentSource
import org.shadowgrove.passporta.data.importer.PassAssetStore
import org.shadowgrove.passporta.data.importer.PassImporter
import org.shadowgrove.passporta.data.local.PassDatabase
import org.shadowgrove.passporta.data.repository.PassRepository
import org.shadowgrove.passporta.data.scanner.PageRenderer
import org.shadowgrove.passporta.data.scanner.PassScanner
import org.shadowgrove.passporta.data.settings.SettingsStore

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
        PassImporter(this, passRepository, passAssetStore, documentSource)
    }

    /** Offline extraction from images and PDFs (ML Kit, bundled models). */
    val passScanner: PassScanner by lazy { PassScanner(pageRenderer) }

    /** User settings (color, appearance, behavior). */
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    /** Full data export/import (passes, assets and settings) as a single `.zip` archive. */
    val backupManager: BackupManager by lazy {
        BackupManager(this, passRepository, passAssetStore, settingsStore)
    }
}
