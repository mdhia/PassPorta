package org.shadowgrove.passporta.ui.document

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.shadowgrove.passporta.data.importer.PassAssetStore
import org.shadowgrove.passporta.data.repository.PassRepository
import org.shadowgrove.passporta.data.scanner.PageRenderer
import org.shadowgrove.passporta.ui.passPortaApplication
import java.io.File

@Immutable
data class PassDocumentUiState(
    val fileName: String? = null,
    val file: File? = null,
    val mimeType: String? = null,
    val pageCount: Int = 0,
    val pageIndex: Int = 0,
    val page: Bitmap? = null,
    val isLoading: Boolean = true,
) {

    /** True if the file can be rendered in the app itself (image or PDF). */
    val isRenderable: Boolean get() = pageCount > 0

    val hasPrevious: Boolean get() = pageIndex > 0
    val hasNext: Boolean get() = pageIndex < pageCount - 1
}

/**
 * Shows the preserved original document of a pass.
 *
 * Only the currently visible page is ever rendered - keeping a multi-page PDF entirely in
 * bitmaps could be several hundred megabytes depending on its length.
 */
class PassDocumentViewModel(
    private val repository: PassRepository,
    private val assetStore: PassAssetStore,
    private val pageRenderer: PageRenderer,
    private val passId: String,
) : ViewModel() {

    private val state = MutableStateFlow(PassDocumentUiState())
    val uiState: StateFlow<PassDocumentUiState> = state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val pass = repository.getPass(passId)
            val file = assetStore.resolve(pass?.originalFilePath)

            if (file == null) {
                state.update { it.copy(isLoading = false) }
                return@launch
            }

            val uri = Uri.fromFile(file)
            val count = withContext(Dispatchers.IO) { pageRenderer.pageCount(uri) }

            state.update {
                it.copy(
                    fileName = pass?.originalFileName ?: file.name,
                    file = file,
                    mimeType = pass?.originalMimeType,
                    pageCount = count,
                    isLoading = count > 0,
                )
            }
            if (count > 0) renderPage(0)
        }
    }

    fun showPage(index: Int) {
        val current = state.value
        if (index !in 0 until current.pageCount || index == current.pageIndex) return
        renderPage(index)
    }

    private fun renderPage(index: Int) {
        viewModelScope.launch {
            val file = state.value.file ?: return@launch
            state.update { it.copy(isLoading = true) }

            val bitmap = withContext(Dispatchers.IO) {
                pageRenderer.renderPage(Uri.fromFile(file), index)
            }

            state.update { current ->
                // Release the previous page once the new one is set.
                current.page?.takeIf { it != bitmap }?.recycle()
                current.copy(page = bitmap, pageIndex = index, isLoading = false)
            }
        }
    }

    override fun onCleared() {
        // Release the last rendered page; ViewModel.onCleared() itself is empty.
        state.value.page?.recycle()
    }

    companion object {

        fun factory(passId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = passPortaApplication()
                PassDocumentViewModel(
                    repository = app.passRepository,
                    assetStore = app.passAssetStore,
                    pageRenderer = app.pageRenderer,
                    passId = passId,
                )
            }
        }
    }
}

