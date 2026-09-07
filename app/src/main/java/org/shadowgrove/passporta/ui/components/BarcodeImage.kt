package org.shadowgrove.passporta.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.shadowgrove.passporta.R
import org.shadowgrove.passporta.data.local.entity.BarcodeType
import org.shadowgrove.passporta.ui.model.PassUi
import org.shadowgrove.passporta.util.BarcodeRenderer

/** State of barcode generation. */
@Immutable
private sealed interface BarcodeState {
    data object Rendering : BarcodeState
    data class Ready(val bitmap: Bitmap) : BarcodeState

    /** The data cannot be encoded in the chosen format. */
    data object Failed : BarcodeState
}

/**
 * Shows the barcode of a pass.
 *
 * Generation runs via [produceState] on a background dispatcher; PDF417 and large QR codes
 * otherwise take noticeable time within the frame.
 *
 * @param width target width - together with the format, determines the bitmap resolution.
 * @param errorCorrection error correction level of the original, so the module grid matches.
 * @param characterSet character set of the payload, if the source specifies it.
 * @param accessibilityLabel description for screen readers, e.g. the pass title.
 */
@Composable
fun BarcodeImage(
    data: String,
    type: BarcodeType,
    width: Dp,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    errorCorrection: String? = null,
    characterSet: String? = null,
) {
    val widthPx = with(LocalDensity.current) { width.roundToPx() }

    val state by produceState<BarcodeState>(
        BarcodeState.Rendering,
        data,
        type,
        widthPx,
        errorCorrection,
        characterSet,
    ) {
        // When regenerating - e.g. after a rotation where only the width changes - the
        // previous image stays displayed. Resetting to `Rendering` would leave the area empty
        // in between and cause a visible flash on every size change. `produceState` keeps the
        // value across key changes, so here it's still the last rendered code.
        if (value !is BarcodeState.Ready) value = BarcodeState.Rendering

        val bitmap = withContext(Dispatchers.Default) {
            BarcodeRenderer.render(
                data = data,
                type = type,
                widthPx = widthPx,
                errorCorrection = errorCorrection,
                characterSet = characterSet,
            )
        }
        value = bitmap?.let(BarcodeState::Ready) ?: BarcodeState.Failed
    }

    Box(
        modifier = modifier
            // Fixed width instead of fillMaxWidth: only this way can the code be deliberately
            // rendered smaller than the surrounding area allows.
            .width(width)
            .aspectRatio(BarcodeRenderer.aspectRatio(type)),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = state) {
            // While rendering the area stays empty - it's already correctly sized, a
            // placeholder would only flicker.
            BarcodeState.Rendering -> Unit

            is BarcodeState.Ready -> Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = null,
                // No smoothing: barcode modules must stay sharply defined, otherwise some
                // scanners fail.
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = accessibilityLabel },
            )

            BarcodeState.Failed -> Text(
                text = stringResource(R.string.detail_barcode_error),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** Shorthand that takes all encoding parameters directly from the pass. */
@Composable
fun BarcodeImage(
    pass: PassUi,
    width: Dp,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
) {
    BarcodeImage(
        data = pass.barcodeData,
        type = pass.barcodeType,
        width = width,
        accessibilityLabel = accessibilityLabel,
        modifier = modifier,
        errorCorrection = pass.barcodeEcc,
        characterSet = pass.barcodeEncoding,
    )
}



