package org.shadowgrove.passporta.ui.document

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.shadowgrove.passporta.R
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Bounds of the zoom in the document view. */
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 6f

/** Magnification a double tap sets. */
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Display of the preserved original document.
 *
 * Images and PDFs are rendered directly in the app - this keeps everything offline and no
 * external app is needed. Everything else (e.g. the `.pkpass` archive itself) can be handed off
 * to a suitable app via a FileProvider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassDocumentScreen(
    passId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PassDocumentViewModel = viewModel(
        key = passId,
        factory = PassDocumentViewModel.factory(passId),
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
                        text = state.fileName ?: stringResource(R.string.document_title),
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
                    val file = state.file
                    if (file != null) {
                        TextButton(onClick = { openExternally(context, file, state.mimeType) }) {
                            Text(stringResource(R.string.document_open_external))
                        }
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

                state.file != null -> UnsupportedFormat(
                    onOpen = { openExternally(context, state.file!!, state.mimeType) },
                )

                else -> Text(
                    text = stringResource(R.string.document_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

/**
 * Page view with zoom.
 *
 * At scale 1, the whole page is visible; two fingers zoom, one finger pans. A double tap
 * switches between overview and detail crop.
 */
@Composable
internal fun ZoomableImage(
    bitmap: Bitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    // Zoom and crop apply per page: switching pages starts again at full overview.
    var scale by remember(bitmap) { mutableFloatStateOf(MIN_ZOOM) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    /**
     * Limits the pan to the actually available overhang, so the page cannot be pushed out of
     * view.
     */
    fun bounded(candidate: Offset, forScale: Float): Offset {
        val width = viewport.width.toFloat()
        val height = viewport.height.toFloat()
        if (width <= 0f || height <= 0f) return Offset.Zero

        // Size at which the page is drawn at scale 1 (ContentScale.Fit).
        val fit = min(width / bitmap.width, height / bitmap.height)
        val maxX = ((bitmap.width * fit * forScale - width) / 2f).coerceAtLeast(0f)
        val maxY = ((bitmap.height * fit * forScale - height) / 2f).coerceAtLeast(0f)

        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    /** Zooms around a point instead of around the image center. */
    fun zoomAround(focusPoint: Offset, target: Float) {
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val focus = focusPoint - center
        val factor = target / scale
        offset = bounded(offset * factor + focus * (1f - factor), target)
        scale = target
    }

    /**
     * Magnification for the double tap.
     *
     * For tall pages - receipts, long tickets - it zooms exactly to page width: that's the view
     * in which a document can be read. If the page already fits in width, a fixed factor applies.
     */
    fun readingZoom(): Float {
        val width = viewport.width.toFloat()
        val height = viewport.height.toFloat()
        if (width <= 0f || height <= 0f) return DOUBLE_TAP_ZOOM

        val fit = min(width / bitmap.width, height / bitmap.height)
        val fillWidth = width / (bitmap.width * fit)
        return max(fillWidth, DOUBLE_TAP_ZOOM).coerceAtMost(MAX_ZOOM)
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { viewport = it }
            // The gestures deliberately sit on the unchanged container and not on the image: a
            // `graphicsLayer` would also scale the pointer coordinates, which would break the
            // zoom point calculation.
            .pointerInput(bitmap) {
                detectTapGestures(
                    onDoubleTap = { position ->
                        if (scale > MIN_ZOOM) {
                            scale = MIN_ZOOM
                            offset = Offset.Zero
                        } else {
                            zoomAround(position, readingZoom())
                        }
                    },
                )
            }
            .pointerInput(bitmap) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    val target = (scale * gestureZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    zoomAround(centroid, target)
                    offset = bounded(offset + pan, target)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

@Composable
private fun UnsupportedFormat(onOpen: () -> Unit) {
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

@Composable
internal fun PageNavigation(
    pageIndex: Int,
    pageCount: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(enabled = hasPrevious, onClick = { onSelect(pageIndex - 1) }) {
            Text(stringResource(R.string.document_previous_page))
        }
        Text(
            text = stringResource(R.string.document_page_of, pageIndex + 1, pageCount),
            style = MaterialTheme.typography.labelLarge,
        )
        TextButton(enabled = hasNext, onClick = { onSelect(pageIndex + 1) }) {
            Text(stringResource(R.string.document_next_page))
        }
    }
}

/**
 * Hands the file off to another app.
 *
 * Access goes through a FileProvider: `filesDir` is private, a direct `file://` path would not
 * be readable for the target app (and would trigger a FileUriExposedException from Android 7
 * onward).
 */
private fun openExternally(context: Context, file: File, mimeType: String?) {
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: return

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.document_no_app, Toast.LENGTH_SHORT).show()
    }
}


