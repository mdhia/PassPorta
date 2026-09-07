package org.shadowgrove.passporta.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import org.shadowgrove.passporta.data.settings.AppThemeColor
import org.shadowgrove.passporta.data.settings.AppThemeMode

/**
 * Base color per setting.
 *
 * The app lives off the colors of the individual passes - the frame UI therefore stays
 * restrained and only picks up the chosen color as a light tint.
 */
private fun AppThemeColor.seed(): Color = when (this) {
    AppThemeColor.INDIGO -> Color(0xFF3F51B5)
    AppThemeColor.TEAL -> Color(0xFF00796B)
    AppThemeColor.GREEN -> Color(0xFF2E7D32)
    AppThemeColor.AMBER -> Color(0xFFF9A825)
    AppThemeColor.ORANGE -> Color(0xFFE65100)
    AppThemeColor.RED -> Color(0xFFC62828)
    AppThemeColor.VIOLET -> Color(0xFF6A1B9A)
    AppThemeColor.SLATE -> Color(0xFF37474F)
}

/** Secondary color: same tone, significantly desaturated - otherwise both compete for attention. */
private fun Color.asSecondary(): Color = lerp(this, Color(0xFF607D8B), 0.55f)

/**
 * Light scheme.
 *
 * Background and surfaces are heavily lightened variants of the base color. The default
 * `onBackground`/`onSurface` values remain valid because the tint at 94% white content is very
 * light.
 */
private fun AppThemeColor.lightScheme(): ColorScheme {
    val seed = seed()
    return lightColorScheme(
        primary = seed,
        secondary = seed.asSecondary(),
        background = lerp(seed, Color.White, 0.95f),
        surface = lerp(seed, Color.White, 0.95f),
        surfaceVariant = lerp(seed, Color.White, 0.88f),
        surfaceContainer = lerp(seed, Color.White, 0.92f),
        surfaceContainerHigh = lerp(seed, Color.White, 0.89f),
        surfaceContainerHighest = lerp(seed, Color.White, 0.86f),
        surfaceContainerLow = lerp(seed, Color.White, 0.93f),
        surfaceContainerLowest = Color.White,
    )
}

/** Dark scheme - same logic, just blended against black. */
private fun AppThemeColor.darkScheme(): ColorScheme {
    val seed = seed()
    // In the dark, the saturated base tone feels too heavy; lightened it stays legible.
    val accent = lerp(seed, Color.White, 0.45f)
    return darkColorScheme(
        primary = accent,
        secondary = accent.asSecondary(),
        background = lerp(seed, Color.Black, 0.90f),
        surface = lerp(seed, Color.Black, 0.90f),
        surfaceVariant = lerp(seed, Color.Black, 0.78f),
        surfaceContainer = lerp(seed, Color.Black, 0.86f),
        surfaceContainerHigh = lerp(seed, Color.Black, 0.82f),
        surfaceContainerHighest = lerp(seed, Color.Black, 0.78f),
        surfaceContainerLow = lerp(seed, Color.Black, 0.88f),
        surfaceContainerLowest = lerp(seed, Color.Black, 0.93f),
    )
}

/**
 * PassPorta's Material 3 theme.
 *
 * [dynamicColor] adopts the system colors from Android 12 onward (Material You). If active, it
 * wins over [themeColor] - both at once would produce no visible difference.
 */
@Composable
fun PassPortaTheme(
    themeColor: AppThemeColor = AppThemeColor.DEFAULT,
    themeMode: AppThemeMode = AppThemeMode.DEFAULT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> themeColor.darkScheme()
        else -> themeColor.lightScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

