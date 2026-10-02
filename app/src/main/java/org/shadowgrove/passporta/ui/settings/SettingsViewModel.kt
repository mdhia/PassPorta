package org.shadowgrove.passporta.ui.settings

import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shadowgrove.passporta.data.backup.BackupFailure
import org.shadowgrove.passporta.data.backup.BackupManager
import org.shadowgrove.passporta.data.backup.BackupResult
import org.shadowgrove.passporta.data.local.entity.PassEntity
import org.shadowgrove.passporta.data.repository.PassRepository
import org.shadowgrove.passporta.data.settings.AppSettings
import org.shadowgrove.passporta.data.settings.AppThemeColor
import org.shadowgrove.passporta.data.settings.AppThemeMode
import org.shadowgrove.passporta.data.settings.SettingsStore
import org.shadowgrove.passporta.data.security.BackupPasswordStore
import org.shadowgrove.passporta.ui.passPortaApplication

/**
 * Display language of the app. `SYSTEM` means: follow the device language.
 *
 * Deliberately not part of [AppSettings]/[SettingsStore]: the selection isn't stored in our own
 * SharedPreferences file but delegated entirely to [AppCompatDelegate]'s per-app language
 * support, which already persists and restores it (see the `autoStoreLocales` manifest flag) and
 * is what actually switches the resources - a second, independent copy of the same state would
 * only risk drifting apart from it.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    GERMAN("de"),
    ENGLISH("en"),
    ;

    companion object {
        val DEFAULT = SYSTEM

        /** Reads the language currently applied via [AppCompatDelegate]. */
        fun current(): AppLanguage {
            val language = AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.get(0)?.language
            return entries.firstOrNull { it.tag == language } ?: DEFAULT
        }
    }
}

/**
 * State of the settings.
 *
 * The store writes immediately and reports the change back via its listener - a separate
 * intermediate state in the ViewModel would only be a second source of the same truth.
 */
class SettingsViewModel(
    private val store: SettingsStore,
    private val backupManager: BackupManager,
    private val passRepository: PassRepository,
    private val backupPasswordStore: BackupPasswordStore,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = store.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = store.current,
    )

    /**
     * Existing folders for the default-folder picker.
     *
     * Always includes [PassEntity.DEFAULT_FOLDER], even before the first pass exists - the
     * dropdown must never be empty.
     */
    val folderSuggestions: StateFlow<List<String>> = passRepository.observeFolders()
        .map { folders ->
            (listOf(PassEntity.DEFAULT_FOLDER) + folders.map { it.folderName }).distinct()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = listOf(PassEntity.DEFAULT_FOLDER),
        )

    /** One-off backup export/import messages (snackbar) - deliberately not part of [settings]. */
    private val backupResultChannel = Channel<BackupResult>(Channel.BUFFERED)
    val backupResults: Flow<BackupResult> = backupResultChannel.receiveAsFlow()

    /** Whether backups are password-protected; reading it needs no Keystore operation. */
    private val _backupPasswordConfigured = MutableStateFlow(backupPasswordStore.isConfigured())
    val backupPasswordConfigured: StateFlow<Boolean> = _backupPasswordConfigured.asStateFlow()

    fun setThemeColor(value: AppThemeColor) = store.setThemeColor(value)

    fun setThemeMode(value: AppThemeMode) = store.setThemeMode(value)

    fun setUseDynamicColor(value: Boolean) = store.setUseDynamicColor(value)

    fun setOpenBarcodeFullscreen(value: Boolean) = store.setOpenBarcodeFullscreen(value)

    fun setShowExpiredInFolders(value: Boolean) = store.setShowExpiredInFolders(value)

    fun setShowUpcomingInFolders(value: Boolean) = store.setShowUpcomingInFolders(value)

    fun setDefaultOwnerName(value: String) = store.setDefaultOwnerName(value)

    fun setDefaultFolderName(value: String) = store.setDefaultFolderName(value)

    fun setAutomaticBackupFolderUri(value: String?) = store.setAutomaticBackupFolderUri(value)

    fun setRollingBackupCount(value: Int) = store.setRollingBackupCount(value)

    /**
     * Stores the backup password (Keystore operation, hence off the main thread).
     * The array is wiped afterwards.
     */
    fun setBackupPassword(password: CharArray) {
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) {
                runCatching { backupPasswordStore.set(password) }
                    .onFailure { Log.w(TAG, "Backup password could not be stored", it) }
                    .isSuccess
            }
            _backupPasswordConfigured.value = backupPasswordStore.isConfigured()
            if (!stored) {
                backupResultChannel.send(BackupResult.Failure(BackupFailure.PASSWORD_UNAVAILABLE))
            }
        }
    }

    fun clearBackupPassword() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { backupPasswordStore.clear() }
            _backupPasswordConfigured.value = backupPasswordStore.isConfigured()
        }
    }

    /**
     * Switches the app's display language.
     *
     * Triggers an immediate locale change; on an `AppCompatActivity` this automatically
     * recreates the activity so every screen picks up the new resources.
     */
    fun setLanguage(value: AppLanguage) {
        val locales = value.tag?.let { LocaleListCompat.forLanguageTags(it) }
            ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** Exports all passes and settings as a `.zip` archive at [destination]. */
    fun exportBackup(destination: Uri) {
        viewModelScope.launch {
            backupResultChannel.send(backupManager.export(destination))
        }
    }

    /**
     * Restores passes and settings from a previously exported archive at [source].
     *
     * @param password password for an encrypted backup; `null` tries the stored backup
     *   password. The array is wiped afterwards.
     */
    fun importBackup(source: Uri, password: CharArray? = null) {
        viewModelScope.launch {
            try {
                backupResultChannel.send(backupManager.import(source, password))
            } finally {
                password?.fill('\u0000')
            }
        }
    }

    companion object {

        private const val TAG = "SettingsViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = passPortaApplication()
                SettingsViewModel(
                    app.settingsStore,
                    app.backupManager,
                    app.passRepository,
                    app.backupPasswordStore,
                )
            }
        }
    }
}
