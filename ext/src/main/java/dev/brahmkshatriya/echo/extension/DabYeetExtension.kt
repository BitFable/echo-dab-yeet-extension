package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.Locale.getDefault

@Suppress("unused")
class DabYeetExtension : ExtensionClient, SearchFeedClient, TrackClient, AlbumClient, ArtistClient,
    ShareClient, LoginClient.CustomInput, LibraryFeedClient, LikeClient, PlaylistClient, PlaylistEditClient, PlaylistEditPrivacyClient,
    HomeFeedClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(RateLimitInterceptor(5.0, 15, 30L))
            .build()
    }

    private val api by lazy { ApiService(client, json) }

    private var _session: String? = null


    // ===== Settings ===== //

    override suspend fun onExtensionSelected() {}

    override suspend fun onInitialize() {
        likedList.clear()
    }

    override suspend fun getSettingItems(): List<Setting> = emptyList()

    override fun setSettings(settings: Settings) {}


    //===== HomeFeedClient =====//

    private val featuredAlbumCategories = listOf(
        "new-releases",
        "most-streamed",
        "best-sellers",
        "press-awards",
        "editor-picks",
        "most-featured"
    )

    override suspend fun loadHomeFeed(): Feed<Shelf> {
        val session = _session
        val formattedShelf = mutableListOf<Shelf>()

        featuredAlbumCategories.forEach { type ->
            val shelf = buildPagedShelf(
                id = type,
                title = type.replace("-", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() },
                type = Shelf.Lists.Type.Linear,
                search = { offset -> api.getFeaturedAlbums(
                    type = type,
                    offset = offset,
                    session = session
                ) },
                extractItems = { it.albums?.map { a -> a.toAlbum() } ?: emptyList() },
                extractPagination = { it.pagination }
            )
            formattedShelf.add(shelf)
        }

        return formattedShelf.toFeed()
    }

    //==== SearchFeedClient ====//

    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        val session = _session
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
                val session = getSession(response)
                    ?: throw Exception("Failed to extract session from response")
                val register = api.getAuth(session)
                return listOf(
                    User(
                        id = register.user?.id?.toString() ?: data["email"]!!,
                        name = data["username"]!!,
                        extras = mapOf(
                            "session" to session,
                            "email" to data["email"]!!,
                            "password" to data["password"]!!
                        )
                    )
                )
            }
            "login" -> {
                val response = api.login(
                    username = data["email"]!!,
                    password = data["password"]!!
                )
                val session = getSession(response)
                    ?: throw Exception("Failed to extract session from response")
                val login = json.decodeFromString<LoginResponse>(response.body.string())
                return listOf(
                    User(
                        id = login.user.id.toString(),
                        name = login.user.username,
                        extras = mapOf(
                            "session" to session,
                            "email" to data["email"]!!,
                            "password" to data["password"]!!
                        )
                    )
                )
            }
            else -> {
                throw IllegalArgumentException("Invalid login form key: $key")
            }
        }
    }

    private fun getSession(response: Response): String? {
        val cookies = Cookie.parseAll(
            response.request.url,
            response.headers
        )
        val session = cookies.find { it.name == "session" }?.value

        return if (session != null) {
            "session=$session"
        } else {
            null
        }
    }


    override fun setLoginUser(user: User?) = runBlocking {
        if (_session == null && user == null) return@runBlocking

        if (_session != null && isSessionValid(_session!!)) return@runBlocking

        val userSession = user?.extras?.get("session")
        if (userSession != null && isSessionValid(userSession)) {
            _session = userSession
            return@runBlocking
        }

        _session = if (user != null) {
            runCatching {
                val response = api.login(
                    username = user.extras["email"]!!,
                    password = user.extras["password"]!!
                )
                getSession(response)
            }.getOrNull()
        } else {
            null
        }
    }

    private suspend fun isSessionValid(session: String): Boolean {
        return runCatching {
            api.getAuth(session).user?.id != null
        }.getOrDefault(false)
    }

    override suspend fun getCurrentUser(): User? = null

    // ======= LibraryFeedClient ===== //

    override suspend fun loadLibraryFeed(): Feed<Shelf> {
        val session = _session ?: throw ClientException.LoginRequired()

        likedList.run {
            clear()
            val favorites = api.getFavourites(session)
            println(favorites)
            addAll(favorites.track.map { it.toTrack(json) })
        }
        shouldFetchLikes = false

        val favShelf = Shelf.Lists.Items(
            id = "fav",
            title = "Favourites",
            list = likedList.toList(),
            type = Shelf.Lists.Type.Linear,
            more = if (likedList.isNotEmpty()) {
                likedList.toList().map { it.toShelf() }.toFeed(
                    Feed.Buttons(showPlayAndShuffle=true)
                )
            }
            else null
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