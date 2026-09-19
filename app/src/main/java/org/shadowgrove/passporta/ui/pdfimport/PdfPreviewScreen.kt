package org.shadowgrove.passporta.ui.pdfimport

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.shadowgrove.passporta.R
import org.shadowgrove.passporta.ui.document.PageNavigation
import org.shadowgrove.passporta.ui.document.ZoomableImage

/**
 * Preview of a PDF handed to PassPorta by another app (e.g. via "Open with" or the share menu).
 *
 * Nothing is saved yet - the pass is only created once the user explicitly taps the import
 * action, which hands the same source off to the regular scan-and-edit flow
 * ([org.shadowgrove.passporta.ui.PassEditorRoute]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    uri: Uri,
    onBack: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PdfPreviewViewModel = viewModel(
        key = uri.toString(),
        factory = PdfPreviewViewModel.factory(uri),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.fileName ?: stringResource(R.string.pdf_preview_title),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                actions = {
                    // Top-right: the only action on this screen - hand the PDF off to the
                    // scan-and-edit flow to actually create a pass from it.
                    IconButton(onClick = onImport) {
                        Icon(
                            imageVector = Icons.Filled.Wallet,
                            contentDescription = stringResource(R.string.pdf_preview_import),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state.pageCount > 1) {
                PageNavigation(
                    pageIndex = state.pageIndex,
                    pageCount = state.pageCount,
                    hasPrevious = state.hasPrevious,
                    hasNext = state.hasNext,
                    onSelect = viewModel::showPage,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            val page = state.page
            when {
                state.isLoading && page == null -> CircularProgressIndicator()

                page != null -> ZoomableImage(
                    bitmap = page,
                    contentDescription = state.fileName,
                    modifier = Modifier.fillMaxSize(),
                )

                // Rare fallback for a corrupted/unreadable PDF: nothing to preview, but the
                // source can still be handed to another app via its staged content URI.
                else -> UnreadablePdf(
                    onOpen = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, R.string.document_no_app, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun UnreadablePdf(onOpen: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.document_not_renderable),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOpen) {
            Text(stringResource(R.string.document_open_external))
        }
    }
}



