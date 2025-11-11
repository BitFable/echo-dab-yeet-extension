package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.LibraryFeedClient
import dev.brahmkshatriya.echo.common.clients.LikeClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.PlaylistEditClient
import dev.brahmkshatriya.echo.common.clients.PlaylistEditPrivacyClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.ShareClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toServerMedia
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.models.Library
import dev.brahmkshatriya.echo.extension.models.LoginResponse
import dev.brahmkshatriya.echo.extension.models.Pagination
import dev.brahmkshatriya.echo.extension.network.ApiService
import dev.brahmkshatriya.echo.extension.network.RateLimitInterceptor
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Suppress("unused")
class DabYeetExtension : ExtensionClient, SearchFeedClient, TrackClient, AlbumClient, ArtistClient,
    ShareClient, LoginClient.CustomInput, LibraryFeedClient, LikeClient, PlaylistClient, PlaylistEditClient, PlaylistEditPrivacyClient{

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(RateLimitInterceptor(permits = 3, period = 1, unit = TimeUnit.SECONDS))
            .build()
    }

    private val api by lazy { ApiService(client, json) }

    private var _session: String? = null


    // ===== Settings ===== //

    override suspend fun onExtensionSelected() {}

    override suspend fun onInitialize() {
        _session = null
        likedList.clear()
    }

    override suspend fun getSettingItems(): List<Setting> = listOf()

    override fun setSettings(settings: Settings) {}

    //==== SearchFeedClient ====//

    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        val session = _session ?: throw ClientException.LoginRequired()
        if (query.isBlank()) return emptyList<Shelf>().toFeed()

        val albumShelf = buildPagedShelf(
            id = "0",
            title = "Albums",
            type = Shelf.Lists.Type.Linear,
            search = { offset -> api.search(query, offset, MediaType.Album.type, session) },
            extractItems = { it.albums?.map { a -> a.toAlbum() } ?: emptyList() },
            extractPagination = { it.pagination }
        )

        val trackShelf = buildPagedShelf(
            id = "1",
            title = "Tracks",
            type = Shelf.Lists.Type.Grid,
            search = { offset -> api.search(query, offset, MediaType.Track.type, session) },
            extractItems = { it.tracks?.map { t -> t.toTrack(json) } ?: emptyList() },
            extractPagination = { it.pagination }
        )

        return listOf<Shelf>(albumShelf, trackShelf).toFeed()
    }

    // ====== TrackClient ======= //

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track = track

    override suspend fun loadStreamableMedia(
        streamable: Streamable,
        isDownload: Boolean
    ): Streamable.Media {
        val stream = api.getStream(streamable.id)
        return stream.url.toServerMedia()
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null

    // ====== AlbumClient ====== //

    override suspend fun loadAlbum(album: Album): Album {
        return if (album.isLoaded()) {
            album
        } else {
            api.getAlbum(album.id).album.toAlbum()
        }
    }

    override suspend fun loadTracks(album: Album): Feed<Track>? {
        val albumList = api.getAlbum(album.id).album.tracks?.map { it.toTrack(json) }.orEmpty()
        if (albumList.isNotEmpty()) {
            return albumList.toFeed()
        }
        return null
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? = null

    // ====== ArtistClient ===== //

    override suspend fun loadArtist(artist: Artist): Artist {
        return if (artist.isLoaded()) {
            artist
        } else {
            api.getArtist(artist.id).toArtist(json)
        }
    }

    override suspend fun loadFeed(artist: Artist): Feed<Shelf> {
        val data = if (artist.isLoaded()) {
            artist
        } else {
            api.getArtist(artist.id).toArtist(json)
        }
        val albumList = json.decodeFromString<List<Album>>(data.extras["albumList"]!!)
        json.decodeFromString<List<String>>(data.extras["similarArtistIds"]!!)

        val albums = Shelf.Lists.Items(
            id = "0",
            title = "More from ${artist.name}",
            list = albumList,
            type = Shelf.Lists.Type.Linear
        )
        return listOf<Shelf>(albums).toFeed()
    }

    // ====== LoginClient ===== //


    override val forms: List<LoginClient.Form>
        get() = listOf(
            LoginClient.Form(
                key = "register",
                "Register",
                LoginClient.InputField.Type.Misc,
                inputFields = listOf(
                    LoginClient.InputField(
                        type = LoginClient.InputField.Type.Username,
                        key = "username",
                        label = "Username",
                        isRequired = true
                    ),
                    LoginClient.InputField(
                        type = LoginClient.InputField.Type.Email,
                        key = "email",
                        label = "Email",
                        isRequired = true
                    ),
                    LoginClient.InputField(
                        type = LoginClient.InputField.Type.Password,
                        key = "password",
                        label = "Password",
                        isRequired = true
                    ),
                    LoginClient.InputField(
                        type = LoginClient.InputField.Type.Misc,
                        key = "inviteCode",
                        label = "Invite Code (optional)",
                        isRequired = false
                    )
                )
            ),
            LoginClient.Form(
                key = "login",
                "Login",
                LoginClient.InputField.Type.Misc,
                inputFields = listOf(
                    LoginClient.InputField(
                        type = LoginClient.InputField.Type.Email,
                        key = "email",
                        label = "Email",
                        isRequired = true
                    ),
                    LoginClient.InputField(
                        type = LoginClient.InputField.Type.Password,
                        key = "password",
                        label = "Password",
                        isRequired = true
                    )
                )
            )
        )


    override suspend fun onLogin(
        key: String,
        data: Map<String, String?>
    ): List<User> {
        when (key) {
            "register" -> {
                val response = api.register(
                    username = data["username"]!!,
                    email = data["email"]!!,
                    password = data["password"]!!,
                    inviteCode = data["inviteCode"]
                )
                val session = extractSession(response.headers["set-cookie"])
                    ?: throw Exception("Failed to extract session from response")
                return listOf(
                    User(
                        id = data["email"]!!,
                        name = data["username"]!!,
                        extras = mapOf("session" to session)
                    )
                )
            }
            "login" -> {
                val response = api.login(
                    username = data["email"]!!,
                    password = data["password"]!!
                )
                val parsedResponse = json.decodeFromString<LoginResponse>(response.body.string())
                val session = extractSession(response.headers["set-cookie"])
                    ?: throw Exception("Failed to extract session from response")
                return listOf(
                    User(
                        id = parsedResponse.user.id.toString(),
                        name = parsedResponse.user.username,
                        extras = mapOf("session" to session)
                    )
                )
            }
            else -> {
                throw IllegalArgumentException("Invalid login form key: $key")
            }
        }
    }


    private fun extractSession(cookieHeader: String?): String? {
        if (cookieHeader == null) {
            return null
        }

        val cookies = cookieHeader.split(';')

        for (cookie in cookies) {
            val trimmedCookie = cookie.trim()
            if (trimmedCookie.startsWith("session=")) {
                return trimmedCookie
            }
        }
        return null
    }


    override fun setLoginUser(user: User?) {
        _session = user?.extras?.get("session")
    }

    override suspend fun getCurrentUser(): User? = null

    // ======= LibraryFeedClient ===== //

    override suspend fun loadLibraryFeed(): Feed<Shelf> {
        val session = _session ?: throw ClientException.LoginRequired()

        likedList.run {
            clear()
            addAll(api.getFavourites(session).track.map { it.toTrack(json) })
        }
        shouldFetchLikes = false

        val favShelf = Shelf.Lists.Items(
            id = "fav",
            title = "Favourites",
            list = likedList.toList(),
            type = Shelf.Lists.Type.Linear,
            more = if (likedList.isNotEmpty()) { likedList.toList().map { it.toShelf() }.toFeed(Feed.Buttons(showPlayAndShuffle=true)) } else null
        )

        val playlists = api.getPlaylists(session).libraries.map { it.toPlaylist() }

        val playlistShelf = Shelf.Lists.Items(
            id = "playlist",
            title = "Playlists",
            list = playlists,
            type = Shelf.Lists.Type.Linear,
        )

        return listOf(favShelf, playlistShelf).toFeed()
    }

    // ===== LikeClient ===== //

    private var shouldFetchLikes = true
    private var likedList: MutableList<EchoMediaItem> = mutableListOf()

    override suspend fun likeItem(
        item: EchoMediaItem,
        shouldLike: Boolean
    ) {
        val session = _session ?: throw ClientException.LoginRequired()
        if (shouldLike) {
            api.addFavourite(item.extras["rawJson"]!!, session)
            likedList.add(item)
        } else {
            api.removeFavourite(item.id, session)
            likedList.removeIf { it.id == item.id }
        }
    }

    override suspend fun isItemLiked(item: EchoMediaItem): Boolean {
        val session = _session ?: return false
        if (shouldFetchLikes) {
            likedList.run {
               clear()
                addAll(api.getFavourites(session).track.map { it.toTrack(json) })
            }
            shouldFetchLikes = false
        }
        return likedList.any { it.id == item.id }
    }


    // ====== PlaylistClient ======= //

    override suspend fun loadPlaylist(playlist: Playlist): Playlist {
        val session = _session ?: throw ClientException.LoginRequired()

        return api.getPlaylist(playlist.id, session).library.toPlaylist()
    }

    override suspend fun loadTracks(playlist: Playlist): Feed<Track> {
        val session = _session ?: throw ClientException.LoginRequired()

        return PagedData.Continuous { continuation ->
            val currentPage = continuation?.toIntOrNull() ?: 1
            api.getPlaylist(playlist.id, session, currentPage).let { response ->
                val tracks = response.library.tracks?.map { it.toTrack(json) }.orEmpty()
                val nextContinuation =
                    if (response.library.pagination?.hasMore == true) (currentPage + 1).toString() else null
                Page(tracks, nextContinuation)
            }
        }.toFeed()
    }

    override suspend fun loadFeed(playlist: Playlist): Feed<Shelf>? = null

    // ====== PlaylistEditClient ===== //

    override suspend fun createPlaylist(
        title: String,
        description: String?
    ): Playlist {
        val session = _session ?: throw ClientException.LoginRequired()

        val playlistJson = Library(
            name = title,
            description = description
        )

        api.createLibrary(
            json = json.encodeToString(playlistJson),
            session = session
        ).let { response ->
            return response.library.toPlaylist()
        }
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        val session = _session ?: throw ClientException.LoginRequired()
        api.deleteLibrary(playlist.id, session)
    }

    override suspend fun editPlaylistMetadata(
        playlist: Playlist,
        title: String,
        description: String?
    ) {
        val session = _session ?: throw ClientException.LoginRequired()

        val playlistJson = Library(
            name = title,
            description = description,
            isPublic = !playlist.isPrivate
        )

        api.editLibraryMetadata(
            id = playlist.id,
            json = json.encodeToString(playlistJson),
            session = session
        )
    }

    override suspend fun addTracksToPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        index: Int,
        new: List<Track>
    ) {
        val session = _session ?: throw ClientException.LoginRequired()
        new.forEach {
            api.addToLibrary(playlist.id, it.extras["rawJson"]!!, session)
        }
    }

    override suspend fun removeTracksFromPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        indexes: List<Int>
    ) {
        val session = _session ?: throw ClientException.LoginRequired()
        indexes.forEach { index ->
            api.removeFromLibrary(playlist.id, tracks[index].id, session)
        }
    }

    override suspend fun moveTrackInPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        fromIndex: Int,
        toIndex: Int
    ) {
        throw ClientException.NotSupported("Will not be implemented")
    }

    override suspend fun listEditablePlaylists(track: Track?): List<Pair<Playlist, Boolean>> {
        val session = _session ?: throw ClientException.LoginRequired()

        val playlists: MutableList<Pair<Playlist, Boolean>> = mutableListOf()
        api.getPlaylists(session).libraries.forEach { playlist ->
            playlists.add(playlist.toPlaylist() to false)
        }
        return playlists
    }

    // ====== PlaylistEditPrivacyClient ===== //

    override suspend fun setPrivacy(
        playlist: Playlist,
        isPrivate: Boolean
    ) {
        val session = _session ?: throw ClientException.LoginRequired()
        val playlistJson = Library(
            name = playlist.title,
            description = playlist.description,
            isPublic = !isPrivate
        )

        api.editLibraryMetadata(
            id = playlist.id,
            json = json.encodeToString(playlistJson),
            session = session
        )
    }

    // ====== ShareClient ===== //

    override suspend fun onShare(item: EchoMediaItem): String {
        return when (item) {
            is Track -> "https://www.qobuz.com/us-en/album/${item.extras["albumId"]}"
            is Album -> "https://www.qobuz.com/us-en/album/${item.id}"
            is Artist -> {
                val id = item.id
                val slug = item.extras["slug"]
                "https://www.qobuz.com/us-en/interpreter/$slug/$id"
            }

            is Playlist -> "https://dab.yeet.su/shared/library/${item.id}"
            is Radio -> throw ClientException.NotSupported("Will not be implemented")
        }
    }

    // ===== Utils ===== //

    private fun Any.isLoaded(): Boolean {
        return when (this) {
            is Track -> extras["isLoaded"] == "true"
            is Album -> extras["isLoaded"] == "true"
            is Artist -> extras["isLoaded"] == "true"
            is Playlist -> extras["isLoaded"] == "true"
            else -> throw TypeCastException("Type mismatch: expected Echo Model but found ${this::class.simpleName}")
        }
    }

    private suspend fun <R> buildPagedShelf(
        id: String,
        title: String,
        type: Shelf.Lists.Type,
        search: suspend (offset: Int) -> R,
        extractItems: (R) -> List<EchoMediaItem>,
        extractPagination: (R) -> Pagination
    ): Shelf.Lists.Items {

        val firstResponse = search(0)
        val firstItems = extractItems(firstResponse)

        val paged = PagedData.Continuous<Shelf> { paginationString ->
            val offset = if (paginationString == null) {
                0
            } else {
                val current = json.decodeFromString<Pagination>(paginationString)
                current.offset!! + current.limit
            }

            val response = if (offset == 0) firstResponse else search(offset)
            val items = extractItems(response).map { it.toShelf() }
            val pagination = extractPagination(response)
            val next = if (pagination.hasMore == true) json.encodeToString(pagination) else null

            Page(items, next)
        }

        return Shelf.Lists.Items(
            id = id,
            title = title,
            list = firstItems,
            type = type,
            more = paged.toFeed()
        )
    }

    enum class MediaType(val type: String) {
        Track("track"),
        Album("album"),
        Artist("artist"),
    }
}