package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.Serializable

@Serializable
data class LibraryResponse(
    val library: LibraryItem
)