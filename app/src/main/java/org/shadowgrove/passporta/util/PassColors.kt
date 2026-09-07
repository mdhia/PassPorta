package org.shadowgrove.passporta.util

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.core.graphics.ColorUtils

/**
 * Contrast logic for pass cards.
 *
 * Rule: black text is used on light backgrounds, white on dark ones. Instead of a fixed
 * brightness threshold, the actual contrast ratio (WCAG) is compared - this yields noticeably
 * better results for saturated brand colors.
 */
object PassColors {

    /** WCAG minimum contrast for normal text. */
    const val MIN_CONTRAST_RATIO: Double = 4.5

    @ColorInt
    val ON_DARK: Int = Color.WHITE

    @ColorInt
    val ON_LIGHT: Int = Color.BLACK

    /** Opacity for secondary text (subtitle, card number). */
    private const val SECONDARY_ALPHA = 0xB3 // 70 %

    private const val ALPHA_OPAQUE = 0xFF

    /**
     * Forces an opaque color value. Transparent backgrounds are invalid for contrast
     * calculation.
     */
    @ColorInt
    fun opaque(@ColorInt color: Int): Int = ColorUtils.setAlphaComponent(color, ALPHA_OPAQUE)

    /** Relative luminance according to WCAG (0.0 = black, 1.0 = white). */
    @FloatRange(from = 0.0, to = 1.0)
    fun luminance(@ColorInt color: Int): Double = ColorUtils.calculateLuminance(opaque(color))

    /** True if the background counts as "light" and thus needs dark text. */
    fun isLight(@ColorInt color: Int): Boolean = contentColorFor(color) == ON_LIGHT

    /**
     * Returns black or white - whichever achieves higher contrast against [background].
     */
    @ColorInt
    fun contentColorFor(@ColorInt background: Int): Int {
        val solidBackground = opaque(background)
        val contrastWithWhite = ColorUtils.calculateContrast(ON_DARK, solidBackground)
        val contrastWithBlack = ColorUtils.calculateContrast(ON_LIGHT, solidBackground)
        return if (contrastWithBlack >= contrastWithWhite) ON_LIGHT else ON_DARK
    }

    /** Muted variant of [contentColorFor] for subtitles and metadata. */
    @ColorInt
    fun secondaryContentColorFor(@ColorInt background: Int): Int =
        ColorUtils.setAlphaComponent(contentColorFor(background), SECONDARY_ALPHA)

    /** Contrast ratio between foreground and background color (1.0 to 21.0). */
    fun contrastRatio(@ColorInt foreground: Int, @ColorInt background: Int): Double =
        ColorUtils.calculateContrast(opaque(foreground), opaque(background))

    /** True if the combination meets the WCAG minimum contrast for body text. */
    fun isAccessible(@ColorInt foreground: Int, @ColorInt background: Int): Boolean =
        contrastRatio(foreground, background) >= MIN_CONTRAST_RATIO

    /**
     * Ensures a readable background color: missing or extremely transparent values are replaced
     * by [fallback]; the result is always opaque.
     */
    @ColorInt
    fun sanitizeBackground(@ColorInt color: Int?, @ColorInt fallback: Int): Int =
        opaque(color?.takeIf { Color.alpha(it) > 0 } ?: fallback)

    /**
     * Slightly darkened or lightened variant of the background - e.g. for the circle behind
     * the logo or for dividers on the card.
     */
    @ColorInt
    fun surfaceVariantFor(@ColorInt background: Int, ratio: Float = 0.12f): Int =
        ColorUtils.blendARGB(opaque(background), contentColorFor(background), ratio)
}
