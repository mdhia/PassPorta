package org.shadowgrove.passporta.ui.pdfimport

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
import org.shadowgrove.passporta.data.importer.LocalDocumentSource
import org.shadowgrove.passporta.data.scanner.PageRenderer
import org.shadowgrove.passporta.ui.passPortaApplication

@Immutable
data class PdfPreviewUiState(
    val fileName: String? = null,
    val pageCount: Int = 0,
    val pageIndex: Int = 0,
    val page: Bitmap? = null,
    val isLoading: Boolean = true,
) {
    val hasPrevious: Boolean get() = pageIndex > 0
    val hasNext: Boolean get() = pageIndex < pageCount - 1
}

/**
 * Renders a PDF handed to PassPorta by another app, before the user decides whether to import
 * it into the wallet.
 *
 * Mirrors [org.shadowgrove.passporta.ui.document.PassDocumentViewModel], but works on a raw
 * staged `content://` URI instead of an already-saved pass - nothing is persisted merely by
 * looking at the file.
 */
class PdfPreviewViewModel(
    private val documentSource: LocalDocumentSource,
    private val pageRenderer: PageRenderer,
    private val uri: Uri,
) : ViewModel() {

    private val state = MutableStateFlow(PdfPreviewUiState())
    val uiState: StateFlow<PdfPreviewUiState> = state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { documentSource.displayName(uri) }
            val count = withContext(Dispatchers.IO) { pageRenderer.pageCount(uri) }

            state.update { it.copy(fileName = name, pageCount = count, isLoading = count > 0) }
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
            state.update { it.copy(isLoading = true) }

            val bitmap = withContext(Dispatchers.IO) { pageRenderer.renderPage(uri, index) }

            state.update { current ->
                // Release the previous page once the new one is set.
                current.page?.takeIf { it != bitmap }?.recycle()
                current.copy(page = bitmap, pageIndex = index, isLoading = false)
            }
        }
    }

    override fun onCleared() {
        state.value.page?.recycle()
    }

    companion object {

        fun factory(uri: Uri): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = passPortaApplication()
                PdfPreviewViewModel(
                    documentSource = app.documentSource,
                    pageRenderer = app.pageRenderer,
                    uri = uri,
                )
            }
        }
    }
}

