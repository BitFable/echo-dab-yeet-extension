package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.Serializable

@Serializable
data class Library(
    val description: String? = "",
    val isPublic: Boolean = false,
    val name: String
)