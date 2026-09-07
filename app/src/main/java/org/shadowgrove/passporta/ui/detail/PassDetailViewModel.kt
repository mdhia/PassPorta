package org.shadowgrove.passporta.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.shadowgrove.passporta.data.importer.PassAssetStore
import org.shadowgrove.passporta.data.repository.PassRepository
import org.shadowgrove.passporta.data.settings.SettingsStore
import org.shadowgrove.passporta.ui.model.PassUi
import org.shadowgrove.passporta.ui.model.toUi
import org.shadowgrove.passporta.ui.passPortaApplication

/**
 * State of the detail view.
 *
 * [pass] is `null` while loading **or** when the pass has been deleted - [isLoading]
 * distinguishes the two.
 */
data class PassDetailUiState(
    val pass: PassUi? = null,
    val isLoading: Boolean = true,

    /** Setting: open the detail view directly in the full-screen barcode. */
    val openBarcodeFullscreen: Boolean = false,
)

class PassDetailViewModel(
    private val repository: PassRepository,
    private val assetStore: PassAssetStore,
    private val passId: String,
    settingsStore: SettingsStore,
) : ViewModel() {

    val uiState: StateFlow<PassDetailUiState> = combine(
        repository.observePassWithFields(passId),
        settingsStore.settings,
    ) { entry, settings ->
        PassDetailUiState(
            pass = entry?.toUi(assetStore),
            isLoading = false,
            openBarcodeFullscreen = settings.openBarcodeFullscreen,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PassDetailUiState(),
    )

    /**
     * Toggles the favorite marker.
     *
     * The new value is derived from the currently displayed pass instead of being passed in:
     * this keeps the caller stateless, and a double tap cannot trigger two contradictory
     * writes.
     */
    fun toggleFavorite() {
        val pass = uiState.value.pass ?: return
        viewModelScope.launch {
            repository.setFavorite(pass.id, !pass.isFavorite)
        }
    }

    /** Deletes the pass along with its logo, hero image and preserved original document. */
    fun delete() {
        viewModelScope.launch {
            // Remember the paths first, then remove the row - otherwise the files are orphaned.
            val pass = repository.getPass(passId)
            if (repository.deleteById(passId)) {
                assetStore.deleteAllFor(
                    pass?.logoPath,
                    pass?.heroImagePath,
                    pass?.originalFilePath,
                )
            }
        }
    }

    companion object {

        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(passId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = passPortaApplication()
                PassDetailViewModel(
                    repository = app.passRepository,
                    assetStore = app.passAssetStore,
                    passId = passId,
                    settingsStore = app.settingsStore,
                )
            }
        }
    }
}

