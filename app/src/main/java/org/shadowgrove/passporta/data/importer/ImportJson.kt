package org.shadowgrove.passporta.data.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Shared JSON configuration for all importers.
 *
 * Both `pass.json` and wallet JWTs contain many optional and vendor-specific fields. Unknown
 * keys must therefore never abort the import.
 */
internal val ImportJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** Nested object or `null`. */
internal fun JsonObject.child(key: String): JsonObject? = this[key] as? JsonObject

/** Nested array or `null`. */
internal fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

/** All objects of an array; empty list if the key is missing. */
internal fun JsonObject.objectArray(key: String): List<JsonObject> =
    array(key)?.filterIsInstance<JsonObject>().orEmpty()

/** Non-empty text of a primitive (numbers and booleans are included). */
internal fun JsonObject.text(key: String): String? = this[key].asText()

/** First non-empty text value from [keys]. */
internal fun JsonObject.firstText(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { text(it) }

/** Converts an arbitrary [JsonElement] into displayable text. */
internal fun JsonElement?.asText(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive.content == "null") return null
    return primitive.content.trim().takeIf { it.isNotEmpty() }
}
