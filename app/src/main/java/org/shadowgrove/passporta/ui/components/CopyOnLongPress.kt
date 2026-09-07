package org.shadowgrove.passporta.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.core.content.getSystemService
import org.shadowgrove.passporta.R

/**
 * Copies [value] to the clipboard on a long press.
 *
 * The short press deliberately has no effect: in the detail view, some of the same lines
 * contain clickable addresses. If the short press were assigned, it would compete with them.
 *
 * @param label Name of the entry in the clipboard; without one, the value itself is used.
 */
@Composable
fun Modifier.copyOnLongPress(value: String, label: String? = null): Modifier {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    return combinedClickable(
        // Without a label, TalkBack only announces "hold down" - this names the action.
        onLongClickLabel = stringResource(R.string.detail_copy_action),
        onClick = {},
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            context.copyToClipboard(value = value, label = label ?: value)
        },
    )
}

/**
 * Puts a text on the clipboard.
 *
 * From Android 13 onward, the system shows its own confirmation - a custom message there would
 * be redundant and is therefore only shown on older versions.
 */
private fun Context.copyToClipboard(value: String, label: String) {
    val clipboard = getSystemService<ClipboardManager>() ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, R.string.detail_copied, Toast.LENGTH_SHORT).show()
    }
}



