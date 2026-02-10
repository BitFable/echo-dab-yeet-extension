package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.Serializable

@Serializable
data class FeaturedResponse(
    val albums: List<Album>? = null,
    val total: Int,
    val offset: Int? = null,
    val limit: Int,
    val hasMore: Boolean? = false
) {
    val pagination: Pagination
        get() = Pagination(
            offset = offset,
            limit = limit,
            total = total,
            hasMore = hasMore
        )
}