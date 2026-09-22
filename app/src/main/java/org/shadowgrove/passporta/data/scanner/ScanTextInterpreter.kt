package org.shadowgrove.passporta.data.scanner

import org.shadowgrove.passporta.data.local.entity.BarcodeType

/**
 * A text line recognized by ML Kit, along with the geometry data relevant for the heuristic.
 *
 * @param text recognized content.
 * @param height line height in pixels - serves as an approximation for the font size.
 * @param top distance from the top edge of the image.
 */
data class ScannedLine(
    val text: String,
    val height: Int,
    val top: Int,
)

/** An automatically recognized label-value pair, e.g. "Seat" / "14A". */
data class ScannedField(
    val label: String,
    val value: String,
)

/** A single barcode found on the scanned source. */
data class ScannedBarcode(
    val data: String,
    val type: BarcodeType,
    val ecc: String? = null,
) {

    /** Same visual code - decides deduplication regardless of minor format quirks. */
    override fun equals(other: Any?): Boolean =
        other is ScannedBarcode && data == other.data && type == other.type

    override fun hashCode(): Int = 31 * data.hashCode() + type.hashCode()
}

/** Pre-filled form values derived from an image or PDF. */
data class ScannedPass(
    val barcodeData: String? = null,
    val barcodeType: BarcodeType? = null,

    /**
     * Error correction level of the recognized code.
     *
     * Carried over so the redrawn barcode gets the same module grid as the scanned original.
     */
    val barcodeEcc: String? = null,

    /** All barcodes found on the source, deduplicated. Includes the primary code above. */
    val barcodes: List<ScannedBarcode> = emptyList(),

    val title: String? = null,
    val ownerName: String? = null,
    val identifier: String? = null,

    /** Automatically recognized additional fields in reading order. */
    val fields: List<ScannedField> = emptyList(),

    /** All recognized lines - serve as selection suggestions in the form. */
    val textLines: List<String> = emptyList(),

    /**
     * Set when the source itself could not be evaluated.
     *
     * Deliberately different from "nothing found": an empty camera file or a broken PDF
     * requires a different user reaction than an image without a barcode.
     */
    val sourceUnreadable: Boolean = false,
) {

    val hasBarcode: Boolean get() = !barcodeData.isNullOrBlank()
}

/**
 * Derives title, owner and card number from recognized text.
 *
 * Deliberately without Android dependencies, so the heuristics can be verified in JVM unit
 * tests. The results are suggestions - the user corrects them in the form.
 */
object ScanTextInterpreter {

    /** Two to three capitalized words, e.g. "Erika Mustermann". */
    private val NAME_PATTERN = Regex(
        """^\p{Lu}[\p{L}'\-]+(?:\s+\p{Lu}[\p{L}'\-]+){1,2}$""",
    )

    /** Names in uppercase with a slash, as on boarding passes: "MUSTERMANN/ERIKA". */
    private val AIRLINE_NAME_PATTERN = Regex("""^\p{Lu}[\p{L}\-]+/\p{Lu}[\p{L}\s\-]+$""")

    /** A digit sequence with optional separators, at least six digits. */
    private val NUMBER_PATTERN = Regex("""\d[\d\s./-]{4,}\d""")

    private val NAME_LABELS = listOf("name", "inhaber", "passagier", "passenger", "kunde", "holder")

    private val NUMBER_LABELS = listOf(
        "nummer", "number", "nr.", "karte", "card", "konto", "account", "mitglied", "member",
        "ticket", "buchung", "booking", "referenz", "reference",
    )

    /**
     * Lines with no letters or digits at all, e.g. divider lines like "---" or "***".
     *
     * Important: pure number lines must **not** be discarded - card numbers look exactly the
     * same.
     */
    private val NOISE_PATTERN = Regex("""^[^\p{L}\p{N}]+$""")

    /**
     * "Label: Value" in one line.
     *
     * Deliberately with numbered instead of named groups: named groups are only available in
     * `MatchResult` from API 26, but PassPorta supports API 24 and up.
     */
    private val LABEL_VALUE_PATTERN = Regex("""\s*([\p{L}][^:：]{0,29}?)\s*[:：]\s*(.+?)\s*""")

    /** "Label:" alone - the value is on the following line. */
    private val LABEL_ONLY_PATTERN = Regex("""\s*([\p{L}][^:：]{0,29}?)\s*[:：]\s*""")

    /**
     * Rates how plausible the reading direction of a recognition pass is.
     *
     * Needed to determine the orientation of a photo. The mere character count is not enough
     * for this: text read sideways does yield many characters, but falls apart into isolated
     * fragments. Coherent words are the more reliable indicator, so they count extra.
     */
    fun orientationScore(lines: List<ScannedLine>): Int = lines.sumOf { line ->
        line.text.count(Char::isLetterOrDigit) + WORD_PATTERN.findAll(line.text).count() * WORD_BONUS
    }

    /**
     * @param lines recognized lines in reading order.
     * @param barcodeData barcode payload - filtered out of the text suggestions because it
     *   often appears as plain text below the code and doesn't make a meaningful title.
     */
    fun interpret(lines: List<ScannedLine>, barcodeData: String? = null): ScannedPass {
        val usable = lines
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.length in MIN_LINE_LENGTH..MAX_LINE_LENGTH }
            .filterNot { it.text == barcodeData }
            .filterNot { NOISE_PATTERN.matches(it.text) }

        val title = findTitle(usable)
        val ownerName = findName(usable)
        val identifier = findIdentifier(usable, barcodeData)

        return ScannedPass(
            title = title,
            ownerName = ownerName,
            identifier = identifier,
            fields = extractFields(
                lines = usable,
                // Anything already shown as title, name or number would just be a repeat in
                // the field list.
                exclude = setOfNotNull(title, ownerName, identifier, barcodeData),
            ),
            textLines = usable.map { it.text },
        )
    }

    /**
     * Extracts label-value pairs from the recognized text.
     *
     * The two notations common on tickets are recognized: "Seat: 14A" on one line, and "Seat:"
     * with the value on the following line. Deliberately no guessing without a colon - treating
     * every line as a potential label produces more noise than benefit.
     */
    private fun extractFields(
        lines: List<ScannedLine>,
        exclude: Set<String>,
    ): List<ScannedField> {
        val result = mutableListOf<ScannedField>()
        val seenLabels = mutableSetOf<String>()

        lines.forEachIndexed { index, line ->
            if (result.size >= MAX_FIELDS) return@forEachIndexed

            val (label, value) = parseLabelValue(line.text)
                ?: parseLabelOnly(line.text, lines.getOrNull(index + 1)?.text)
                ?: return@forEachIndexed

            if (value in exclude) return@forEachIndexed
            if (!seenLabels.add(label.lowercase())) return@forEachIndexed

            result += ScannedField(label = label, value = value)
        }
        return result
    }

    /** "Seat: 14A" - label and value on the same line. */
    private fun parseLabelValue(text: String): Pair<String, String>? {
        val match = LABEL_VALUE_PATTERN.matchEntire(text) ?: return null
        val label = match.groupValues[1].trim()
        val value = match.groupValues[2].trim()

        // "https://..." would otherwise pass as label "https" with value "//...".
        if (value.startsWith("//")) return null
        if (!isPlausibleLabel(label) || value.isEmpty()) return null
        return label to value
    }

    /** "Seat:" with the value on the following line. */
    private fun parseLabelOnly(text: String, next: String?): Pair<String, String>? {
        val match = LABEL_ONLY_PATTERN.matchEntire(text) ?: return null
        val label = match.groupValues[1].trim()
        val value = next?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        // If the next label follows directly, the value doesn't belong to it.
        if (LABEL_ONLY_PATTERN.matches(value) || LABEL_VALUE_PATTERN.matches(value)) return null
        if (!isPlausibleLabel(label)) return null
        return label to value
    }

    /** A label contains letters and is not a pure number or time. */
    private fun isPlausibleLabel(label: String): Boolean =
        label.length in MIN_LABEL_LENGTH..MAX_LABEL_LENGTH && label.any(Char::isLetter)


    /**
     * Title = largest font in the upper part of the image. On tickets and cards, that's almost
     * always the issuer or the event.
     */
    private fun findTitle(lines: List<ScannedLine>): String? {
        if (lines.isEmpty()) return null
        val cutoff = lines.maxOf { it.top } * TITLE_SEARCH_FRACTION
        val candidates = lines.filter { it.top <= cutoff }.ifEmpty { lines }
        return candidates
            .filterNot { NUMBER_PATTERN.containsMatchIn(it.text) }
            .maxByOrNull { it.height }
            ?.text
    }

    private fun findName(lines: List<ScannedLine>): String? {
        // 1. Line directly after a label like "Name:".
        labelledValue(lines, NAME_LABELS)?.let { return it }

        // 2. Boarding pass notation.
        lines.firstOrNull { AIRLINE_NAME_PATTERN.matches(it.text) }?.let { return it.text }

        // 3. Classic "first name last name".
        return lines.firstOrNull { NAME_PATTERN.matches(it.text) }?.text
    }

    private fun findIdentifier(lines: List<ScannedLine>, barcodeData: String?): String? {
        labelledValue(lines, NUMBER_LABELS)
            ?.let { value -> NUMBER_PATTERN.find(value)?.value?.trim() ?: value }
            ?.let { return it }

        // Otherwise the longest digit sequence - card numbers are usually the longest number in
        // the image.
        return lines
            .asSequence()
            .mapNotNull { NUMBER_PATTERN.find(it.text)?.value?.trim() }
            .filterNot { it == barcodeData }
            .maxByOrNull { it.count(Char::isDigit) }
    }

    /**
     * Value for a label. Considers both common layouts: "Label: Value" on one line and "Label"
     * with the value on the following line.
     */
    private fun labelledValue(lines: List<ScannedLine>, labels: List<String>): String? {
        lines.forEachIndexed { index, line ->
            val lowercase = line.text.lowercase()
            if (labels.none { lowercase.contains(it) }) return@forEachIndexed

            line.text.substringAfter(':', missingDelimiterValue = "")
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { return it }

            lines.getOrNull(index + 1)
                ?.text
                ?.takeIf { next -> labels.none { next.lowercase().contains(it) } }
                ?.let { return it }
        }
        return null
    }

    private const val MIN_LINE_LENGTH = 2
    private const val MAX_LINE_LENGTH = 80

    /** Upper limit for automatically adopted fields - the rest would mostly be fine print. */
    private const val MAX_FIELDS = 10

    private const val MIN_LABEL_LENGTH = 2
    private const val MAX_LABEL_LENGTH = 30

    /** Only the upper third is searched for the title. */
    private const val TITLE_SEARCH_FRACTION = 0.35

    /** A coherent word - three letters are enough as proof of actual legibility. */
    private val WORD_PATTERN = Regex("""\p{L}{3,}""")

    /** Weight of a recognized word compared to a single character. */
    private const val WORD_BONUS = 5
}

