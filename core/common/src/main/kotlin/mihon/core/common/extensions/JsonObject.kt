package mihon.core.common.extensions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** An empty [JsonObject] instance. */
val JsonObjectEmpty = JsonObject(emptyMap())

/** UTF-8 byte array representation of `{}`. */
val JsonObjectEmptyBytes = byteArrayOf(0x7B, 0x7D)

/** Convenient companion property for an empty [JsonObject]. */
val JsonObject.Companion.EMPTY: JsonObject
    inline get() = JsonObjectEmpty

/** Encodes this [JsonObject] into a UTF-8 [ByteArray]. */
fun JsonObject.toByteArray(): ByteArray = toString().encodeToByteArray()

/** Decodes a UTF-8 [ByteArray] into a [JsonObject]. */
fun ByteArray.toJsonObject(): JsonObject = Json.decodeFromString<JsonObject>(decodeToString())

