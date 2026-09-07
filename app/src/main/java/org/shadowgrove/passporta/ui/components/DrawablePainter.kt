package org.shadowgrove.passporta.ui.components

import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

/**
 * Loads an arbitrary drawable resource as a [Painter].
 *
 * `painterResource` only handles VectorDrawables and bitmaps. From API 26, launcher icons are
 * `<adaptive-icon>` XML (`mipmap-anydpi-v26`), though, and cause an `IllegalArgumentException`
 * there. This function instead rasterizes the drawable itself - covering adaptive icons,
 * layer-lists and all other drawable types.
 *
 * Adaptive icons deliberately draw beyond their visible area (bleed area). The result
 * therefore belongs in a mask, e.g. `Modifier.clip(CircleShape)`.
 *
 * @param size edge length to rasterize at - ideally the later display size.
 * @return `null` if the resource cannot be resolved.
 */
@Composable
fun rememberDrawablePainter(
    @DrawableRes id: Int,
    size: Dp,
): Painter? {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }

    return remember(context, id, sizePx) {
        val drawable: Drawable = ContextCompat.getDrawable(context, id) ?: return@remember null

        // Preserve aspect ratio. Adaptive icons report -1 when no layer size is fixed - then
        // rasterize square.
        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        val height = if (intrinsicWidth > 0 && intrinsicHeight > 0) {
            (sizePx.toFloat() * intrinsicHeight / intrinsicWidth).roundToInt().coerceAtLeast(1)
        } else {
            sizePx
        }

        BitmapPainter(drawable.toBitmap(width = sizePx, height = height).asImageBitmap())
    }
}
