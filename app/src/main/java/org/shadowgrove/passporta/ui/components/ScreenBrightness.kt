package org.shadowgrove.passporta.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Sets the screen brightness to 100% for the duration of the composition and keeps the screen
 * awake.
 *
 * Intended for the zoom mode: at the checkout or at the gate, the code must be reliably
 * readable even with an automatically dimmed display.
 *
 * Important: this is a pure window property
 * ([WindowManager.LayoutParams.screenBrightness]) and does **not** change the system setting -
 * no `WRITE_SETTINGS` permission is therefore needed. On exit, the previous value is restored
 * exactly.
 */
@Composable
fun KeepScreenBright(enabled: Boolean = true) {
    val view = LocalView.current

    DisposableEffect(view, enabled) {
        val window = view.hostWindow()
        if (!enabled || window == null) return@DisposableEffect onDispose { }

        val previousBrightness = window.attributes.screenBrightness
        window.setScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            window.setScreenBrightness(previousBrightness)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/**
 * Window this view belongs to.
 *
 * Inside an [androidx.compose.ui.window.Dialog], that's its own window - and only there does
 * the brightness override take effect, because the system lets the topmost value win.
 */
private fun View.hostWindow(): Window? =
    (parent as? DialogWindowProvider)?.window ?: context.findActivity()?.window

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The LayoutParams must be reassigned for the change to take effect. */
private fun Window.setScreenBrightness(value: Float) {
    attributes = attributes.apply { screenBrightness = value }
}
