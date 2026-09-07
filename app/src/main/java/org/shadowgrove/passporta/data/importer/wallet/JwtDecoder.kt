package org.shadowgrove.passporta.data.importer.wallet

import kotlinx.serialization.json.JsonObject
import org.shadowgrove.passporta.data.importer.ImportJson
import org.shadowgrove.passporta.util.Base64Url

/**
 * Reads the payload of a JSON Web Token - exclusively locally.
 *
 * The signature is deliberately **not** verified: that would require the issuer's public key,
 * which can only be obtained online. This is uncritical for PassPorta because the token only
 * serves as a data container and authorizes nothing. Accordingly, its content is treated like
 * arbitrary user input and never executed or passed on.
 */
object JwtDecoder {

    /** Recognizes a JWT (header.payload[.signature]) anywhere in a text. */
    private val TOKEN_PATTERN = Regex("""[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{20,}(?:\.[A-Za-z0-9_-]*)?""")

    /**
     * Searches for the first segment in [text] that can be decoded as a JWT with a JSON
     * payload.
     *
     * This equally covers path (`/gp/v/save/<jwt>`), query (`?jwt=...`), fragment and shared
     * text blobs.
     */
    fun findToken(text: String?): String? {
        val source = text?.takeIf { it.isNotBlank() } ?: return null
        return TOKEN_PATTERN.findAll(source)
            .map { it.value }
            .firstOrNull { decodePayload(it) != null }
    }

    /**
     * Decodes the middle segment of [token] into a [JsonObject].
     *
     * @return the payload or `null` if [token] is not a JWT with a JSON object as payload.
     */
    fun decodePayload(token: String?): JsonObject? {
        val segments = token?.split('.') ?: return null
        if (segments.size < 2) return null
        val json = Base64Url.decodeToStringOrNull(segments[1]) ?: return null
        return runCatching { ImportJson.parseToJsonElement(json) }.getOrNull() as? JsonObject
    }
}
