package org.shadowgrove.passporta.util

/**
 * A recognized, clickable section within a text.
 *
 * @param range Position in the original text.
 * @param uri finished target uri (`https:`, `mailto:` or `tel:`).
 */
data class TextLink(val range: IntRange, val uri: String)

/**
 * Finds web addresses, email addresses and phone numbers in free-text fields.
 *
 * Deliberately narrow, custom expressions instead of `android.util.Patterns`: its `PHONE`
 * already recognizes any longer digit sequence with separators as a phone number - a customer
 * number like "1234 5678 9012" would then become a call link. In addition, this version can be
 * tested without the Android runtime.
 */
object TextLinks {

    private val EMAIL = Regex("""[\w.%+\-]+@[\w\-]+(?:\.[\w\-]+)+""")

    /**
     * Requires a scheme or "www." prefix.
     *
     * A bare "irgendwas.de" would be too greedy: field values like "Gate B.12" or "Row 3.OG"
     * would otherwise turn into links.
     */
    private val URL = Regex("""(?:https?://|www\.)[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** Internationally formatted phone numbers are unambiguous - the "+" doesn't appear in any other field. */
    private val INTERNATIONAL_PHONE = Regex("""\+\d[\d\s()./\-]{5,}\d""")

    /**
     * National notation, only with a matching field label.
     *
     * Without this additional hint, confusion with card, booking or seat numbers would be
     * too likely.
     */
    private val LOCAL_PHONE = Regex("""0[\d\s()./\-]{5,}\d""")

    private val PHONE_LABELS = listOf(
        "telefon", "phone", "tel.", "tel ", "mobil", "handy", "fax", "hotline", "rufnummer",
    )

    /** Punctuation that almost always belongs to the sentence at the end of an address, not the address itself. */
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}\"'»"

    /**
     * @param label Field label; also decides whether a national phone number is recognized.
     * @return Non-overlapping matches in order of their occurrence.
     */
    fun find(text: String, label: String? = null): List<TextLink> {
        if (text.isBlank()) return emptyList()

        val candidates = buildList {
            EMAIL.findAll(text).forEach { add(it.range to "mailto:${it.value}") }

            URL.findAll(text).forEach { match ->
                val trimmed = match.value.trimEnd { it in TRAILING_PUNCTUATION }
                if (trimmed.isEmpty()) return@forEach
                val range = match.range.first..(match.range.first + trimmed.length - 1)
                val uri = if (trimmed.startsWith("http", ignoreCase = true)) {
                    trimmed
                } else {
                    "https://$trimmed"
                }
                add(range to uri)
            }

            INTERNATIONAL_PHONE.findAll(text).forEach { add(it.range to it.value.toTelUri()) }

            if (label.hintsAtPhone()) {
                LOCAL_PHONE.findAll(text).forEach { add(it.range to it.value.toTelUri()) }
            }
        }

        // Resolve overlaps: the longer match wins - otherwise an email would quickly get
        // caught in a greedier address pattern.
        return candidates
            .sortedWith(compareBy({ it.first.first }, { it.first.first - it.first.last }))
            .fold(mutableListOf<TextLink>()) { accepted, (range, uri) ->
                val overlaps = accepted.any { it.range.last >= range.first }
                if (!overlaps) accepted += TextLink(range, uri)
                accepted
            }
    }

    /**
     * "tel" as its own word or a common label.
     *
     * Deliberately not as a plain substring: "Hotel" would otherwise contain a phone hint.
     */
    private fun String?.hintsAtPhone(): Boolean {
        val normalized = this?.lowercase()?.trim() ?: return false
        return normalized == "tel" || PHONE_LABELS.any { normalized.contains(it) }
    }

    /** Reduces to what a dialer understands: a leading plus and digits. */
    private fun String.toTelUri(): String =
        "tel:" + filterIndexed { index, char -> char.isDigit() || (index == 0 && char == '+') }
}
