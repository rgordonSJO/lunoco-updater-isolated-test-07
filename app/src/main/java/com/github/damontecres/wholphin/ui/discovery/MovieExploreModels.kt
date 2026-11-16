package com.github.damontecres.wholphin.ui.discovery

import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.ui.nav.Destination
import org.jellyfin.sdk.model.api.*
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

internal data class MovieExploreScope(val server: UUID, val user: UUID, val library: UUID)
internal enum class MovieFacetKind { STUDIO, GENRE }
internal data class MovieExploreImage(val item: UUID, val type: ImageType, val tag: String)
internal data class MovieFacet(
    val id: UUID, val name: String, val kind: MovieFacetKind,
    val image: MovieExploreImage? = null,
    val studioIds: List<UUID> = listOf(id),
    val curatedKey: String? = null,
) {
    val key: String get() = "${kind.name}:${curatedKey ?: id}"
    fun destination(scope: MovieExploreScope) = Destination.FilteredCollection(
        itemId = scope.library,
        parentType = if (kind == MovieFacetKind.STUDIO) BaseItemKind.STUDIO else BaseItemKind.GENRE,
        filter = CollectionFolderFilter(
            nameOverride = name,
            filter = GetItemsFilter(
                includeItemTypes = listOf(BaseItemKind.MOVIE),
                studios = studioIds.takeIf { kind == MovieFacetKind.STUDIO },
                genres = listOf(id).takeIf { kind == MovieFacetKind.GENRE },
            ),
            libraryDisplayInfoIdOverride = "${scope.library}_explore_$key",
        ),
        recursive = true, collectionType = CollectionType.MOVIES,
    )
}

internal fun studioArtwork(item: BaseItemDto): MovieExploreImage? =
    if (item.type != BaseItemKind.STUDIO) null else listOf(ImageType.LOGO, ImageType.THUMB, ImageType.PRIMARY).firstNotNullOfOrNull { type ->
        item.imageTags?.get(type)?.takeIf(String::isNotBlank)?.let { MovieExploreImage(item.id, type, it) }
    }

internal fun genreArtwork(movie: BaseItemDto): MovieExploreImage? =
    movie.backdropImageTags.orEmpty().firstOrNull(String::isNotBlank)?.let {
        MovieExploreImage(movie.id, ImageType.BACKDROP, it)
    } ?: movie.imageTags?.get(ImageType.THUMB)?.takeIf(String::isNotBlank)?.let {
        MovieExploreImage(movie.id, ImageType.THUMB, it)
    }

internal fun validMovieFacet(item: BaseItemDto) =
    item.id != UUID(0, 0) && !item.name.isNullOrBlank() && item.movieCount != 0

/** A single scoped movie proves the category is not empty; never query on focus. */
internal fun facetMovieRequest(scope: MovieExploreScope, facet: MovieFacet) = GetItemsRequest(
    userId = scope.user, parentId = scope.library, recursive = true,
    includeItemTypes = listOf(BaseItemKind.MOVIE),
    studioIds = facet.studioIds.takeIf { facet.kind == MovieFacetKind.STUDIO },
    genreIds = listOf(facet.id).takeIf { facet.kind == MovieFacetKind.GENRE },
    sortBy = listOf(ItemSortBy.DATE_CREATED, ItemSortBy.SORT_NAME),
    sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
    isMissing = false, isPlaceHolder = false,
    excludeLocationTypes = listOf(LocationType.VIRTUAL),
    limit = if (facet.kind == MovieFacetKind.GENRE) 3 else 1,
    enableImages = facet.kind == MovieFacetKind.GENRE,
    enableUserData = false, enableTotalRecordCount = false, imageTypeLimit = 1,
)

internal data class MovieExploreState(
    val scope: MovieExploreScope? = null,
    val loading: Boolean = false,
    val studios: List<MovieFacet> = emptyList(),
    val genres: List<MovieFacet> = emptyList(),
    val failed: Boolean = false,
)

/** Prefer an identity after reorder; otherwise the nearest surviving card. */
internal fun restoredFacetIndex(items: List<MovieFacet>, key: String?, previousIndex: Int): Int =
    items.indexOfFirst { it.key == key }.takeIf { it >= 0 }
        ?: previousIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
