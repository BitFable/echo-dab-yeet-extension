package dev.brahmkshatriya.echo.extension.models

import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.extension.utils.IntToString
import dev.brahmkshatriya.echo.extension.utils.LabelSerializer
import dev.brahmkshatriya.echo.extension.utils.parseDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import dev.brahmkshatriya.echo.common.models.Album as EchoAlbum

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Album(
    @Serializable(with = IntToString::class)
    val id: String,
    val title: String,
    val artist: String,
    @Serializable(with = IntToString::class)
    val artistId: String? = null,
    val releaseDate: String,
    val genre: String,
    val cover: String,
    val images: Images? = null,
    val trackCount: Int,
    val duration: Int? = null,
    val audioQuality: AudioQuality,
    @Serializable(with = LabelSerializer::class)
    val label: String? = null,
    val tracks: List<Track>? = null,
    @SerialName("parental_warning")
    val parentalWarning: Boolean = false
) {
    fun toAlbum(): EchoAlbum {
        return EchoAlbum(
            id = id,
            title = title,
            cover = images?.high?.toImageHolder() ?: cover.toImageHolder(),
            artists = listOf(Artist(id = artistId ?: artist, name = artist)),
            trackCount = trackCount.toLong(),
            duration = duration?.times(1000L),
            releaseDate = parseDate(releaseDate),
            label = label,
            isExplicit = parentalWarning,
            extras = mapOf("isLoaded" to "true")
        )
    }
}

@Serializable
data class AlbumResponse(
    val album: Album
)