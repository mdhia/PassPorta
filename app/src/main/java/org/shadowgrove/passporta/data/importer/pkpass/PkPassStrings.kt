package org.shadowgrove.passporta.data.importer.pkpass

/**
 * Minimal reader for Apple's `pass.strings` files.
 *
 * Format (UTF-8, optionally UTF-16 with BOM): lines of the form `"KEY" = "Value";`, with
 * C-style comments allowed in between.
 *
 * In localized passes, `pass.json` only contains keys; the displayable texts live in
 * `<language>.lproj/pass.strings`.
 */
internal object PkPassStrings {

    private val ENTRY_PATTERN = Regex(
        """"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** Empty dictionary for non-localized passes. */
    val EMPTY: Map<String, String> = emptyMap()

    /**
     * Picks the matching `pass.strings` from the archive and parses it.
     *
     * Order: exact language ([preferredLanguage]), then `en`, then the first match.
     */
    fun load(archive: PkPassArchive, preferredLanguage: String?): Map<String, String> {
        val candidates = archive.endingWith(".lproj/pass.strings")
        if (candidates.isEmpty()) return EMPTY

        val language = preferredLanguage?.lowercase()?.substringBefore('-')
        val bytes = candidates.entries
            .sortedBy { (name, _) ->
                val entryLanguage = name.substringBeforeLast(".lproj").substringAfterLast('/')
                when {
                    language != null && entryLanguage.substringBefore('-') == language -> 0
                    entryLanguage.startsWith("en") -> 1
                    else -> 2
                }
            }
            .first()
            .value

        return parse(decode(bytes))
    }

    /** Parses the content of a `.strings` file. */
    fun parse(content: String): Map<String, String> =
        ENTRY_PATTERN.findAll(content).associate { match ->
            match.groupValues[1].unescape() to match.groupValues[2].unescape()
        }

    /** Detects UTF-16 BOMs; UTF-8 is assumed without a BOM. */
    private fun decode(bytes: ByteArray): String = when {
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            String(bytes, Charsets.UTF_16BE)

        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            String(bytes, Charsets.UTF_16LE)

        else -> bytes.toString(Charsets.UTF_8)
    }

    private fun String.unescape(): String {
        if (!contains('\\')) return this
        val builder = StringBuilder(length)
        var index = 0
        while (index < length) {
            val current = this[index]
            if (current != '\\' || index == lastIndex) {
                builder.append(current)
                index++
                continue
            }
            when (val escaped = this[index + 1]) {
                'n' -> builder.append('\n')
                't' -> builder.append('\t')
                'r' -> builder.append('\r')
                'u' -> {
                    val hex = substring(index + 2, minOf(index + 6, length))
                    val code = hex.toIntOrNull(16)
                    if (code != null && hex.length == 4) {
                        builder.append(code.toChar())
                        index += 6
                        continue
                    }
                    builder.append(escaped)
                }

                else -> builder.append(escaped)
            }
            index += 2
        }
        return builder.toString()
    }
}
