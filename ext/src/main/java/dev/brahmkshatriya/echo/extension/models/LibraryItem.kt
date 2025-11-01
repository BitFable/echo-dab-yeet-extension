package dev.brahmkshatriya.echo.extension.models

import dev.brahmkshatriya.echo.common.models.Date
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class LibraryItem(
    val id: String,
    val name: String,
    val description: String?,
    val isPublic: Boolean,
    @SerialName("user_id")
    val userID: Long? = null,
    val createdAt: String,
    val trackCount: Long,
    val tracks: List<Track>? = emptyList(),
    val pagination: Pagination? = null
) {
    @OptIn(ExperimentalTime::class)
    fun toPlaylist(): Playlist {
        return Playlist(
            id = id,
            title = name,
            isEditable = true,
            isPrivate = !isPublic,
            isShareable = isPublic,
            trackCount = trackCount,
            cover = tracks?.firstOrNull()?.images?.high?.toImageHolder(),
            creationDate = Date(
                epochTimeMs = Instant.Companion.parse(createdAt).toEpochMilliseconds()
            ),
            description = description,
            extras = mapOf("userID" to userID.toString()),
            isRadioSupported = false,
            isFollowable = false,
        )
    }
}