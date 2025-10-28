package dev.brahmkshatriya.echo.extension.network

import dev.brahmkshatriya.echo.extension.models.AlbumResponse
import dev.brahmkshatriya.echo.extension.models.ArtistResponse
import dev.brahmkshatriya.echo.extension.models.FavouriteResponse
import dev.brahmkshatriya.echo.extension.models.LoginRequest
import dev.brahmkshatriya.echo.extension.models.RegisterRequest
import dev.brahmkshatriya.echo.extension.models.SearchResponse
import dev.brahmkshatriya.echo.extension.models.Stream
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Response

class ApiService(client: OkHttpClient, json: Json) : BaseHttpClient(client, json) {

    val baseUrl: String = "https://dabmusic.xyz/api"

    suspend fun getAlbum(id: String): AlbumResponse {
        return get(
            url = "${baseUrl}/album",
            params = mapOf("albumId" to id)
        )
    }

    suspend fun getArtist(id: String): ArtistResponse {
        return get(
            url = "${baseUrl}/discography",
            params = mapOf("artistId" to id)
        )
    }

    suspend fun search(
        query: String,
        offset: Int = 0,
        type: String,
        session: String
    ): SearchResponse {
        return get(
            url = "${baseUrl}/search",
            params = mapOf(
                "q" to query,
                "offset" to offset.toString(),
                "type" to type
            ),
            headers = mapOf("Cookie" to session)
        )
    }

    suspend fun getStream(trackId: String): Stream {
        return get(
            url = "${baseUrl}/stream",
            params = mapOf("trackId" to trackId)
        )
    }

    suspend fun login(username: String, password: String): Response {
        return post<Response>(
            url = "${baseUrl}/auth/login",
            jsonBody = LoginRequest(username, password).toJsonString()
        )
    }

    suspend fun register(username: String, email: String, password: String, inviteCode: String): Response {
        return post<Response>(
            url = "${baseUrl}/auth/register",
            jsonBody = RegisterRequest(username, email, password, inviteCode).toJsonString()
        )
    }

    suspend fun getPlaylists(id: String? = null, session: String): Response {
        return get(
            url = "${baseUrl}/libraries/${id}",
            headers = mapOf("Cookie" to session)
        )
    }

    suspend fun getFavourites(session: String): FavouriteResponse {
        return get(
            url = "${baseUrl}/favorites",
            headers = mapOf("Cookie" to session)
        )
    }

    suspend fun addFavourite(json: String, session: String): Response {
        return post<Response>(
            url = "${baseUrl}/favorites",
            jsonBody = json,
            headers = mapOf("Cookie" to session)
        )
    }

    suspend fun removeFavourite(id: String, session: String): Response {
        return delete(
            url = "${baseUrl}/favorites",
            params = mapOf("trackId" to id),
            headers = mapOf("Cookie" to session)
        )
    }
}
