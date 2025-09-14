package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String? = null,
    val message: String? = null
)
