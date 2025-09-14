package dev.brahmkshatriya.echo.extension.models

import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.extension.utils.parseDate
import kotlinx.serialization.Serializable

@Serializable
data class LibraryResponse(
    val libraries: List<LibraryItem>
)

@Serializable
data class LibraryItem(
    val id: String,
    val name: String,
    val description: String,
    val isPublic: Boolean,
    val createdAt: String,
    val trackCount: Int,
    val tracks: List<Track>
) {
    fun toPlaylist(): Playlist {
        return Playlist(
            id = id,
            title = name,
            description = description,
            isEditable = true,
            isPrivate = !isPublic,
            creationDate = parseDate(createdAt),
            trackCount = trackCount.toLong(),
            isRadioSupported = false,
            isShareable = isPublic,
            extras = mapOf("isLoaded" to "true")
        )
    }
}

