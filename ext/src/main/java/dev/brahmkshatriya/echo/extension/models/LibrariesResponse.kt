package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.Serializable


@Serializable
data class LibrariesResponse(
    val libraries: List<LibraryItem>
)

