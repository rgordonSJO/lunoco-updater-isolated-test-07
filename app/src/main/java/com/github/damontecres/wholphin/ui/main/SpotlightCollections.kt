package com.github.damontecres.wholphin.ui.main

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jellyfin.sdk.model.api.*
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID

/**
 * The pinned SDK has no collection-membership field on a movie. Read actual accessible BoxSet
 * children, intersecting only the already eligible primary-library candidate IDs. Never infer
 * franchises from titles, studios or provider IDs. Bounds are deliberately independent of library size.
 */
internal suspend fun spotlightCollections(
    scope: SpotlightScope,
    candidateIds: Set<UUID>,
    fetch: suspend (GetItemsRequest) -> List<BaseItemDto>,
): Map<UUID, Set<UUID>> {
    if (candidateIds.size < 2) return emptyMap()
    suspend fun safeFetch(request: GetItemsRequest): List<BaseItemDto> =
        try { fetch(request) }
        catch (ex: CancellationException) { throw ex }
        catch (_: Exception) { Timber.w("Spotlight collection metadata unavailable"); emptyList() }
    val boxes = safeFetch(GetItemsRequest(
        userId = scope.userId, includeItemTypes = listOf(BaseItemKind.BOX_SET), recursive = true,
        sortBy = listOf(ItemSortBy.SORT_NAME), sortOrder = listOf(SortOrder.ASCENDING),
        limit = 64, enableImages = false, enableUserData = false, enableTotalRecordCount = false,
    )).filter { it.type == BaseItemKind.BOX_SET && it.id != UUID(0, 0) }.distinctBy { it.id }.take(64)
    val permits = Semaphore(2)
    val memberships = coroutineScope {
        boxes.map { box -> async {
            permits.withPermit {
                val children = safeFetch(GetItemsRequest(
                    userId = scope.userId, parentId = box.id, recursive = true,
                    includeItemTypes = listOf(BaseItemKind.MOVIE), limit = 100,
                    sortBy = listOf(ItemSortBy.SORT_NAME), sortOrder = listOf(SortOrder.ASCENDING),
                    enableImages = false, enableUserData = false, enableTotalRecordCount = false,
                ))
                children.filter { it.type == BaseItemKind.MOVIE && it.id in candidateIds }
                    .map { it.id to box.id }
            }
        } }.awaitAll().flatten()
    }
    Timber.i("Spotlight collections: boxes=%d matchedMovies=%d bounded=true",
        boxes.size, memberships.map { it.first }.distinct().size)
    return memberships.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
}
