package org.shadowgrove.passporta.util

import androidx.annotation.ColorInt
import kotlin.math.roundToInt

/**
 * Parser for color values from external formats.
 *
 * `.pkpass` uses CSS-like values (`rgb(90, 60, 200)`), whereas the Google Wallet API uses hex
 * codes (`#5a3cc8`). Both variants are mapped here to an ARGB `Int`.
 *
 * Deliberately without `android.graphics.Color`: this keeps the class usable in plain JVM unit
 * tests.
 */
object ColorParser {

    private const val ALPHA_OPAQUE = 0xFF

    private val RGB_PATTERN = Regex(
        """rgba?\(\s*(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*(?:,\s*(-?[\d.]+)\s*)?\)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Converts [raw] into an ARGB color value.
     *
     * Supported are `#RGB`, `#RRGGBB`, `#AARRGGBB` (each also without `#`) as well as
     * `rgb(r, g, b)` and `rgba(r, g, b, a)`.
     *
     * @return the color value or `null` if [raw] is empty or not interpretable.
     */
    @ColorInt
    fun parseOrNull(raw: String?): Int? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return parseFunctional(value) ?: parseHex(value)
    }

    /** Like [parseOrNull], but falls back to [fallback]. */
    @ColorInt
    fun parseOrDefault(raw: String?, @ColorInt fallback: Int): Int = parseOrNull(raw) ?: fallback

    @ColorInt
    private fun parseFunctional(value: String): Int? {
        val match = RGB_PATTERN.matchEntire(value) ?: return null
        val red = match.groupValues[1].toChannel() ?: return null
        val green = match.groupValues[2].toChannel() ?: return null
        val blue = match.groupValues[3].toChannel() ?: return null
        val alpha = match.groupValues[4]
            .takeIf { it.isNotEmpty() }
            ?.toAlphaChannel()
            ?: ALPHA_OPAQUE
        return argb(alpha, red, green, blue)
    }

    @ColorInt
    private fun parseHex(value: String): Int? {
        val digits = value.removePrefix("#").trim()
        if (digits.isEmpty() || !digits.all { it.isHexDigit() }) return null
        return when (digits.length) {
            // #RGB -> each digit is doubled
            3 -> argb(
                alpha = ALPHA_OPAQUE,
                red = digits[0].hexPair(),
                green = digits[1].hexPair(),
                blue = digits[2].hexPair(),
            )

            6 -> argb(
                alpha = ALPHA_OPAQUE,
                red = digits.hexAt(0),
                green = digits.hexAt(2),
                blue = digits.hexAt(4),
            )

            8 -> argb(
                alpha = digits.hexAt(0),
                red = digits.hexAt(2),
                green = digits.hexAt(4),
                blue = digits.hexAt(6),
            )

            else -> null
        }
    }

    @ColorInt
    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    /** Numeric channel 0..255; also accepts floating-point values. */
    private fun String.toChannel(): Int? =
        toDoubleOrNull()?.let { it.roundToInt().coerceIn(0, 255) }

    /** Alpha is given either as a fraction (0..1) or as 0..255. */
    private fun String.toAlphaChannel(): Int? {
        val parsed = toDoubleOrNull() ?: return null
        val scaled = if (parsed <= 1.0) parsed * 255.0 else parsed
        return scaled.roundToInt().coerceIn(0, 255)
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun Char.hexPair(): Int = digitToInt(16) * 0x11

    private fun String.hexAt(index: Int): Int = substring(index, index + 2).toInt(16)
}
