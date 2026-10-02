package org.shadowgrove.passporta.data.backup

import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/** Builds safe, readable and bounded names for PKPASS entries in a backup ZIP. */
internal object BackupEntryName {

    /** Stay below common ZIP/filesystem component limits, including the `passes/` prefix. */
    const val MAX_ENTRY_NAME_BYTES = 240

    private const val PREFIX = "passes/"
    private const val SUFFIX = ".pkpass"
    private const val FALLBACK_TITLE = "pass"

    /**
     * Returns `passes/<normalized-title>-<uuid>.pkpass`.
     *
     * [usedNames] stores names case-insensitively because a backup may be unpacked on a
     * case-insensitive filesystem. Only the title is shortened; the UUID is always retained.
     */
    fun next(title: String, id: String, usedNames: MutableSet<String>): String {
        val safeTitle = normalizeTitle(title).ifBlank { FALLBACK_TITLE }
        val uuid = canonicalUuid(id)
        var collision = 1
        while (true) {
            val collisionSuffix = if (collision == 1) "" else "-$collision"
            val candidateTitle = truncateUtf8(
                safeTitle,
                titleBudget(uuid, collisionSuffix),
            ).ifBlank { FALLBACK_TITLE }
            val name = "$PREFIX$candidateTitle-$uuid$collisionSuffix$SUFFIX"
            val key = name.lowercase(Locale.ROOT)
            if (usedNames.add(key)) return name
            collision++
        }
    }

    /** Keeps letters/digits, turns whitespace into one dash, and drops all path punctuation. */
    internal fun normalizeTitle(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        val result = StringBuilder(normalized.length)
        var separatorPending = false
        for (character in normalized) {
            when {
                character.isLetterOrDigit() -> {
                    if (separatorPending && result.isNotEmpty() && result.last() != '-') {
                        result.append('-')
                    }
                    result.append(character)
                    separatorPending = false
                }
                character.isWhitespace() -> separatorPending = result.isNotEmpty()
                // Slash, backslash, dots, colons, control chars and all other punctuation vanish.
            }
        }
        return result.toString().trim('-')
    }

    /** Truncates by UTF-8 bytes without splitting a character. */
    internal fun truncateUtf8(value: String, maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
        val result = StringBuilder()
        for (character in value) {
            val candidate = result.toString() + character
            if (candidate.toByteArray(Charsets.UTF_8).size > maxBytes) break
            result.append(character)
        }
        return result.toString().trim('-')
    }

    private fun titleBudget(uuid: String, collisionSuffix: String): Int {
        val fixedBytes = "$PREFIX-$uuid$collisionSuffix$SUFFIX".toByteArray(Charsets.UTF_8).size
        return MAX_ENTRY_NAME_BYTES - fixedBytes
    }

    /** Production IDs are UUIDs; old/test IDs get a stable UUID without changing the stored ID. */
    private fun canonicalUuid(id: String): String = runCatching {
        UUID.fromString(id).toString().lowercase(Locale.ROOT)
    }.getOrElse {
        UUID.nameUUIDFromBytes(id.toByteArray(Charsets.UTF_8)).toString()
    }
}

