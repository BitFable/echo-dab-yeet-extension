package dev.brahmkshatriya.echo.extension.models

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.extension.utils.IntToString
import dev.brahmkshatriya.echo.extension.utils.parseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dev.brahmkshatriya.echo.common.models.Track as EchoTrack

@Serializable
data class Track(
    @Serializable(with = IntToString::class)
    val id: String,
    val title: String,
    val artist: String,
    @Serializable(with = IntToString::class)
    val artistId: String,
    val albumTitle: String,
    val albumCover: String,
    @Serializable(with = IntToString::class)
    val albumId: String,
    val releaseDate: String,
    val genre: String,
    val duration: Int,
    val audioQuality: AudioQuality,
    val version: String? = null,
    val label: String? = null,
    @SerialName("parental_warning")
    val parentalWarning: Boolean = false,
    val isrc: String? = null,
    val images: Images? = null
) {
    fun toTrack(json: Json): EchoTrack {
        val trackJson = json.encodeToString(this)

        val rawJson =  """{"track": $trackJson}"""

        return EchoTrack(
            id = id,
            title = title,
            artists = listOf(Artist(id = artistId, name = artist)),
            album = Album(id = albumId, title = albumTitle),
            cover = images?.high?.toImageHolder() ?: albumCover.toImageHolder(),
            duration = duration.times(1000L),
            releaseDate = parseDate(releaseDate),
            isExplicit = parentalWarning,
            isrc = isrc,
            genres = genre.split(" ").filter { it.isNotBlank() },
            extras = mapOf(
                "albumId" to albumId,
                "rawJson" to rawJson
            ),
            streamables = listOf(
                Streamable.server(
                    id = id,
                    quality = 0,
                    title = if (audioQuality.isHiRes) "Lossless" else "Lossy",
                )
            ),
        )
    }
}
