package org.shadowgrove.passporta.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Base64 URL decoding for JWT segments.
 *
 * Deliberately uses the Kotlin standard library instead of `android.util.Base64`:
 * - `java.util.Base64` is out (only available from API 26, minSdk is 24),
 * - `android.util.Base64` would not be runnable in plain JVM unit tests.
 *
 * JWTs are unpadded per RFC 7515; [Base64.PaddingOption.ABSENT_OPTIONAL] accepts both
 * notations.
 */
object Base64Url {

    @OptIn(ExperimentalEncodingApi::class)
    private val decoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /** Decodes [segment] to bytes or returns `null` if it is not valid Base64. */
    @OptIn(ExperimentalEncodingApi::class)
    fun decodeOrNull(segment: String?): ByteArray? {
        val value = segment?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { decoder.decode(value) }.getOrNull()
    }

    /** Decodes [segment] as UTF-8 text or returns `null`. */
    fun decodeToStringOrNull(segment: String?): String? =
        decodeOrNull(segment)?.toString(Charsets.UTF_8)
}
