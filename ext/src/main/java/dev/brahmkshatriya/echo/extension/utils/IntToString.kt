package dev.brahmkshatriya.echo.extension.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

@OptIn(ExperimentalSerializationApi::class)
object IntToString : JsonTransformingSerializer<String>(String.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return when (element) {
            is JsonPrimitive -> JsonPrimitive(element.content)
            else -> JsonPrimitive("")
        }
    }
}