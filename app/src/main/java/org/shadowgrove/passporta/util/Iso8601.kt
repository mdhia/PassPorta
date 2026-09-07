package org.shadowgrove.passporta.util

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parses the ISO 8601 timestamps from Apple and Google passes.
 *
 * Deliberately based on [SimpleDateFormat] instead of `java.time`: the latter requires either
 * API 26 or core library desugaring - but PassPorta supports API 24 and up. The pattern `XXX`
 * (ISO timezone incl. `Z`) has been available since API 24 and covers the formats commonly used
 * in passes.
 *
 * The class has no Android dependencies and is therefore testable in JVM unit tests.
 */
object Iso8601 {

    /** From specific to unspecific - the first match wins. */
    private val PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )

    /**
     * @return the point in time in milliseconds, or `null` if [raw] is empty or unreadable.
     */
    fun parseOrNull(raw: String?): Long? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        for (pattern in PATTERNS) {
            val parsed = tryParse(value, pattern)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun tryParse(value: String, pattern: String): Long? = try {
        val format = SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            // Without a timezone in the pattern, the device timezone would apply and the
            // expiry time would shift depending on the country of travel.
            if (!pattern.contains("XXX")) timeZone = TimeZone.getTimeZone("UTC")
        }

        // Using ParsePosition instead of parse(String): the latter also accepts partial
        // matches. The pattern without a timezone would otherwise accept "…T23:59:59+01:00"
        // and silently discard the offset - the expiry time would then be off by hours.
        val position = ParsePosition(0)
        val parsed = format.parse(value, position)
        when {
            parsed == null -> null
            position.index != value.length -> null
            else -> parsed.time
        }
    } catch (_: IllegalArgumentException) {
        null
    }
}
