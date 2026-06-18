package com.example.banksmstracker.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents a merchant match rule for a category.
 *
 * Supports two matching modes:
 * - Exact (default): case-insensitive string equality against [Payment.merchant]
 * - Regex ([isRegex] = true): case-insensitive regex [containsMatchIn] against [Payment.merchant]
 *
 * JSON backward compatibility: a plain string `"FooStore"` is deserialized as
 * `Merchant(pattern = "FooStore")`. The new object format is also accepted.
 */
@Serializable(with = MerchantSerializer::class)
data class Merchant(val pattern: String, val displayName: String? = null, val isRegex: Boolean = false)

/**
 * Custom serializer that accepts both legacy plain-string format and new object format.
 *
 * Deserialize:
 *   - `"FooStore"` → `Merchant(pattern = "FooStore")`
 *   - `{"pattern":"FooStore","displayName":"Foo","isRegex":false}` → full object
 *
 * Serialize: always writes the full object format.
 */
object MerchantSerializer : KSerializer<Merchant> {

    private val nullableStringSerializer = String.serializer().nullable

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Merchant") {
        element<String>("pattern")
        element<String?>("displayName")
        element<Boolean>("isRegex")
    }

    override fun deserialize(decoder: Decoder): Merchant {
        // Use the JSON-specific path for backward compatibility with plain strings.
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonPrimitive && element.isString) {
                return Merchant(pattern = element.content)
            }
            val obj = element.jsonObject
            val pattern = obj["pattern"]?.jsonPrimitive?.content ?: ""
            val displayNameRaw = obj["displayName"]
            val displayName = if (displayNameRaw == null || displayNameRaw.toString() == "null") {
                null
            } else {
                displayNameRaw.jsonPrimitive.content
            }
            val isRegex = obj["isRegex"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            return Merchant(pattern = pattern, displayName = displayName, isRegex = isRegex)
        }
        // Non-JSON fallback — read as a plain string.
        return Merchant(pattern = decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Merchant) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.pattern)
            encodeNullableSerializableElement(descriptor, 1, nullableStringSerializer, value.displayName)
            encodeBooleanElement(descriptor, 2, value.isRegex)
        }
    }
}

@Serializable
data class Category(
    var id: Long? = null,
    var name: String,
    var merchants: MutableList<Merchant>,
    var enabled: Boolean = true
)
