package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.Serializable

@Serializable
data class Images(
    val thumbnail: String? = null,
    val small: String? = null,
    val large: String? = null,
    val extralarge: String? = null,
    val mega: String? = null,
    val back: String? = null,
) {
    val high: String?
        get() = mega ?: extralarge ?: large ?: small ?: thumbnail

    val low: String?
        get() = thumbnail ?: small
}