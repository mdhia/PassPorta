package org.shadowgrove.passporta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.shadowgrove.passporta.R
import org.shadowgrove.passporta.ui.model.PassUi
import org.shadowgrove.passporta.util.BarcodeRenderer

/** Border around the maximized code. */
private val ZoomPadding = 24.dp

/** Spacing between the code and the caption. */
private val CaptionSpacing = 20.dp

/**
 * Full-screen overlay with a maximized barcode.
 *
 * As long as it's visible, the screen runs at full brightness ([KeepScreenBright]). A tap
 * anywhere closes the view.
 */
@Composable
fun BarcodeZoomDialog(
    pass: PassUi,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Must be inside the dialog: only its window sits on top.
        KeepScreenBright()

        val interactionSource = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // Scanners need dark modules on a light background - therefore always white,
                // regardless of pass color and dark mode.
                .background(Color.White)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(ZoomPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The code gets the space that remains after the caption.
                //
                // What matters here is that the height is dictated from above: previously the
                // width was computed from the full window height, while the code and caption
                // together in turn determined the height. In landscape, the height became the
                // limiting dimension - and both sizes fed back on each other, visible as
                // flickering. With `weight`, the content can no longer influence its own
                // constraint.
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val aspectRatio = BarcodeRenderer.aspectRatio(pass.barcodeType)
                    val barcodeWidth = minOf(maxWidth, maxHeight * aspectRatio)

                    if (barcodeWidth > 0.dp) {
                        BarcodeImage(
                            pass = pass,
                            width = barcodeWidth,
                            accessibilityLabel = stringResource(
                                R.string.detail_barcode,
                                pass.title,
                            ),
                        )
                    }
                }

                val caption = pass.barcodeAltText ?: pass.identifier
                if (caption != null) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = CaptionSpacing),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.zoom_close),
                        tint = Color.Black,
                    )
                }
            }
        }
    }
}

