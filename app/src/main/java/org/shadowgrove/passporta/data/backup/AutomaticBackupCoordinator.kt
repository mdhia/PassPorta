package org.shadowgrove.passporta.data.backup

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.shadowgrove.passporta.data.settings.SettingsStore
import kotlin.time.Duration.Companion.milliseconds

/** Schedules a quiet, serialized backup after a successful data mutation. */
class AutomaticBackupCoordinator(
    private val settingsStore: SettingsStore,
    private val backupManager: BackupManager,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backupMutex = Mutex()
    private var pendingJob: Job? = null

    /** Coalesces rapid successive mutations into one backup of the final state. */
    fun requestBackup() {
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(DEBOUNCE_MILLIS.milliseconds)
            backupMutex.withLock {
                val settings = settingsStore.current
                val folderUri = settings.automaticBackupFolderUri
                    ?.takeIf { it.isNotBlank() }
                    ?.let(Uri::parse)
                    ?: return@withLock
                when (val result = backupManager.exportAutomatic(folderUri, settings.rollingBackupCount)) {
                    is BackupResult.ExportSuccess -> Unit
                    else -> Log.w(TAG, "Automatic backup failed: $result")
                }
            }
        }
    }

    private companion object {
        const val TAG = "AutomaticBackup"
        const val DEBOUNCE_MILLIS = 500L
    }
}

