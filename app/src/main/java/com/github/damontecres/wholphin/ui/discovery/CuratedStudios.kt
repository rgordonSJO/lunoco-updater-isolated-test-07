package com.github.damontecres.wholphin.ui.discovery

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetStudiosRequest
import java.util.Locale

/** Explicit identity aliases, never fuzzy title matching or corporate-ownership inference. */
internal data class CuratedStudio(val key: String, val label: String, val searches: List<String>, val aliases: Set<String>)

internal val curatedStudios = listOf(
    CuratedStudio("disney", "Walt Disney Pictures", listOf("Disney"), setOf("Walt Disney Pictures", "Walt Disney Productions")),
    CuratedStudio("pixar", "Pixar", listOf("Pixar"), setOf("Pixar", "Pixar Animation Studios")),
    CuratedStudio("warner", "Warner Bros.", listOf("Warner"), setOf("Warner Bros.", "Warner Bros. Pictures")),
    CuratedStudio("universal", "Universal Pictures", listOf("Universal"), setOf("Universal Pictures", "Universal Studios")),
    CuratedStudio("sony", "Sony / Columbia", listOf("Sony", "Columbia"), setOf("Sony Pictures", "Sony Pictures Entertainment", "Sony Pictures Animation", "Columbia Pictures")),
    CuratedStudio("paramount", "Paramount Pictures", listOf("Paramount"), setOf("Paramount Pictures", "Paramount")),
    CuratedStudio("twentieth", "20th Century Studios", listOf("20th Century"), setOf("20th Century Studios", "20th Century Fox")),
    CuratedStudio("dreamworks", "DreamWorks", listOf("DreamWorks"), setOf("DreamWorks Pictures", "DreamWorks Animation", "DreamWorks", "DreamWorks SKG")),
    CuratedStudio("mgm", "MGM", listOf("MGM", "Metro"), setOf("MGM", "Metro-Goldwyn-Mayer", "Metro-Goldwyn-Mayer Pictures")),
    CuratedStudio("lionsgate", "Lionsgate", listOf("Lionsgate"), setOf("Lionsgate", "Lionsgate Films")),
    CuratedStudio("a24", "A24", listOf("A24"), setOf("A24")),
)

private fun studioName(name: String) = name.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

internal fun CuratedStudio.matches(item: BaseItemDto): Boolean =
    validMovieFacet(item) && item.type == BaseItemKind.STUDIO &&
        aliases.any { studioName(it) == studioName(item.name.orEmpty()) }

internal fun curatedStudioRequest(scope: MovieExploreScope, search: String, page: Int) = GetStudiosRequest(
    userId = scope.user, parentId = scope.library, includeItemTypes = listOf(BaseItemKind.MOVIE),
    fields = listOf(ItemFields.ITEM_COUNTS), searchTerm = search,
    startIndex = page * 24, limit = 24, enableImages = true, enableUserData = false,
    enableTotalRecordCount = false,
)

/** Input contains only exact aliases individually proven to have an accessible scoped movie. */
internal fun curatedStudioFacets(verified: List<BaseItemDto>): List<MovieFacet> = curatedStudios.mapNotNull { group ->
    val aliases = verified.filter(group::matches).distinctBy { it.id }.sortedBy { it.id.toString() }
    if (aliases.isEmpty()) null else {
        val art = aliases.mapNotNull(::studioArtwork).minByOrNull {
            when (it.type) {
                org.jellyfin.sdk.model.api.ImageType.LOGO -> 0
                org.jellyfin.sdk.model.api.ImageType.THUMB -> 1
                else -> 2
            }
        }
        MovieFacet(aliases.first().id, group.label, MovieFacetKind.STUDIO, art,
            studioIds = aliases.map { it.id }, curatedKey = group.key)
    }
}
