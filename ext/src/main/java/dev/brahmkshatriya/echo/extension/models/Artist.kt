package dev.brahmkshatriya.echo.extension.models

import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.extension.utils.IntToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dev.brahmkshatriya.echo.common.models.Artist as EchoArtist

@Serializable
data class ArtistResponse(
    val artist: Artist,
    val albums: List<Album>
) {
    fun toArtist(json: Json): EchoArtist {
        val albumList = albums.map { it.toAlbum() }
        return EchoArtist(
            id = artist.id,
            name = artist.name,
            cover = artist.image?.high?.toImageHolder(),
            bio = artist.biography?.content.orEmpty(),
            extras = mapOf(
                "similarArtistIds" to json.encodeToString(artist.similarArtistIds),
                "slug" to (artist.slug ?: ""),
                "albumList" to json.encodeToString(albumList),
                "isLoaded" to "true"
            )
        )
    }
}

@Serializable
data class Biography(
    val summary: String = "",
    val content: String = "",
    val source: String = "",
    val language: String = "en"
)

@Serializable
data class Artist(
    @Serializable(with = IntToString::class)
    val id: String,
    val name: String,
    val albumsCount: Int,
    val albumsAsPrimaryArtistCount: Int,
    val albumsAsPrimaryComposerCount: Int,
    val slug: String? = null,
    val image: Images? = null,
    val biography: Biography? = null,
    val similarArtistIds: List<String>,
    val information: String? = null
)