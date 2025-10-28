package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.Serializable

@Serializable
data class LibraryResponse(
    val libraries: List<LibraryItem>
)

@Serializable
data class LibraryItem(
    val id: String,
    val name: String,
    val description: String?,
    val isPublic: Boolean,
    val createdAt: String,
    val trackCount: Int,
    val tracks: List<Track>
)