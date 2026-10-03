package org.shadowgrove.passporta.ui.detail
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shadowgrove.passporta.data.backup.AutomaticBackupCoordinator
import org.shadowgrove.passporta.data.importer.LocalDocumentSource
import org.shadowgrove.passporta.data.importer.PassAssetStore
import org.shadowgrove.passporta.data.importer.pkpass.PkPassParser
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.data.local.entity.PassBarcodeEntity
import org.shadowgrove.passporta.data.repository.PassRepository
import org.shadowgrove.passporta.data.scanner.PassScanner
import org.shadowgrove.passporta.data.settings.SettingsStore
import org.shadowgrove.passporta.ui.model.PassUi
import org.shadowgrove.passporta.ui.model.toUi
import org.shadowgrove.passporta.ui.passPortaApplication
import java.io.ByteArrayInputStream
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
/** A barcode found in a source that could be added to the open pass. */
data class BarcodeCandidate(
    val data: String,
    val type: BarcodeType,
    val altText: String? = null,
    val ecc: String? = null,
    val encoding: String? = null,
    val added: Boolean = false,
)
/** State of the "add barcodes" dialog. */
data class BarcodeSearchState(
    val isLoading: Boolean = true,
    val candidates: List<BarcodeCandidate> = emptyList(),
    val failed: Boolean = false,
)
class PassDetailViewModel(
    private val repository: PassRepository,
    private val assetStore: PassAssetStore,
    private val passId: String,
    settingsStore: SettingsStore,
    private val automaticBackupCoordinator: AutomaticBackupCoordinator,
    private val passScanner: PassScanner,
    private val documentSource: LocalDocumentSource,
    private val pkPassParser: PkPassParser = PkPassParser(),
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
    private val _barcodeSearch = MutableStateFlow<BarcodeSearchState?>(null)
    /** Dialog with barcodes found in an additional source; `null` = closed. */
    val barcodeSearch: StateFlow<BarcodeSearchState?> = _barcodeSearch.asStateFlow()
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
    /** Searches an image or PDF (also camera photos) for barcodes. */
    fun findBarcodesInImage(uri: Uri) = findBarcodes {
        val scanned = passScanner.scan(uri)
        if (scanned.sourceUnreadable) {
            null
        } else {
            buildList {
                scanned.barcodes.forEach { add(BarcodeCandidate(it.data, it.type, ecc = it.ecc)) }
                val primaryData = scanned.barcodeData
                val primaryType = scanned.barcodeType
                if (primaryData != null && primaryType != null) {
                    add(BarcodeCandidate(primaryData, primaryType, ecc = scanned.barcodeEcc))
                }
            }
        }
    }
    /** Reads the barcodes of a `.pkpass` archive. */
    fun findBarcodesInPkPass(uri: Uri) = findBarcodes {
        val draft = withContext(Dispatchers.IO) {
            val bytes = documentSource.readBytes(uri) ?: return@withContext null
            pkPassParser.parse(ByteArrayInputStream(bytes)).draft
        } ?: return@findBarcodes null
        buildList {
            draft.barcodes.forEach {
                add(BarcodeCandidate(it.data, it.type, it.altText, it.ecc, it.encoding))
            }
            add(
                BarcodeCandidate(
                    data = draft.barcodeData,
                    type = draft.barcodeType,
                    altText = draft.barcodeAltText,
                    ecc = draft.barcodeEcc,
                    encoding = draft.barcodeEncoding,
                ),
            )
        }
    }
    private fun findBarcodes(source: suspend () -> List<BarcodeCandidate>?) {
        _barcodeSearch.value = BarcodeSearchState()
        viewModelScope.launch {
            val found = try {
                source()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Barcode search failed", e)
                null
            }
            // Filter out barcodes the pass already has - compared by payload and type.
            val pass = uiState.value.pass
            val known = pass?.barcodes.orEmpty()
                .ifEmpty { listOfNotNull(pass?.primaryBarcode) }
                .map { it.data to it.type }
                .toSet()
            val candidates = found.orEmpty()
                .filter { it.data.isNotBlank() }
                .filterNot { (it.data to it.type) in known }
                .distinctBy { it.data to it.type }
            // The dialog may have been closed in the meantime.
            if (_barcodeSearch.value != null) {
                _barcodeSearch.value = BarcodeSearchState(
                    isLoading = false,
                    candidates = candidates,
                    failed = found == null,
                )
            }
        }
    }
    /** Adds [candidate] to the open pass and marks it as added in the dialog. */
    fun addBarcode(candidate: BarcodeCandidate) {
        if (candidate.added) return
        markAdded(candidate, true)
        viewModelScope.launch {
            val ok = repository.addBarcode(
                passId,
                PassBarcodeEntity(
                    passId = passId,
                    barcodeData = candidate.data,
                    barcodeType = candidate.type,
                    barcodeAltText = candidate.altText,
                    barcodeEcc = candidate.ecc,
                    barcodeEncoding = candidate.encoding,
                ),
            )
            if (ok) automaticBackupCoordinator.requestBackup() else markAdded(candidate, false)
        }
    }
    private fun markAdded(candidate: BarcodeCandidate, added: Boolean) {
        _barcodeSearch.update { state ->
            state?.copy(
                candidates = state.candidates.map {
                    if (it.data == candidate.data && it.type == candidate.type) it.copy(added = added) else it
                },
            )
        }
    }
    fun closeBarcodeSearch() {
        _barcodeSearch.value = null
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
                automaticBackupCoordinator.requestBackup()
            }
        }
    }
    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val TAG = "PassDetailViewModel"
        fun factory(passId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = passPortaApplication()
                PassDetailViewModel(
                    repository = app.passRepository,
                    assetStore = app.passAssetStore,
                    passId = passId,
                    settingsStore = app.settingsStore,
                    automaticBackupCoordinator = app.automaticBackupCoordinator,
                    passScanner = app.passScanner,
                    documentSource = app.documentSource,
                )
            }
        }
    }
}