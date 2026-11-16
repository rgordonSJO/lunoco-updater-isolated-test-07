package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.*
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

/** Shared by Home and its Recently Added View More grid; never invoked on focus. */
internal suspend fun resolveRecentSeriesDestination(
    api: org.jellyfin.sdk.api.client.ApiClient, userId: UUID?, row: HomeRowConfig?, item: BaseItem,
): com.github.damontecres.wholphin.ui.nav.Destination {
    // Resolve only the permitted series parent. Latest-episode targeting scrolls away from
    // the overview and is not part of a fresh landing-page entry. Existing saved/legacy
    // targeted routes remain compatible; ordinary episode browsing and Back are unchanged.
    return resolveHomeItemDestination(row, item) { id ->
        api.userLibraryApi.getItem(id, userId = userId).content.type == BaseItemKind.SERIES
    }
}

/** An activation-only, user-scoped lookup. DateCreated means library addition, not air date/Next Up. */
internal fun recentEpisodeRequest(userId: UUID, parentId: UUID) = GetItemsRequest(
    userId = userId, parentId = parentId, recursive = true,
    includeItemTypes = listOf(BaseItemKind.EPISODE),
    sortBy = listOf(ItemSortBy.DATE_CREATED), sortOrder = listOf(SortOrder.DESCENDING),
    limit = 20, enableTotalRecordCount = false, isMissing = false, isPlaceHolder = false,
    excludeLocationTypes = listOf(LocationType.VIRTUAL),
    fields = listOf(ItemFields.PARENT_ID, ItemFields.DATE_CREATED, ItemFields.PLAY_ACCESS, ItemFields.MEDIA_SOURCE_COUNT),
)

internal fun recentEpisodeIds(episode: BaseItemDto, seriesId: UUID): SeasonEpisodeIds? {
    val seasonId = (episode.seasonId ?: episode.parentId)?.takeUnless { it == UUID(0, 0) }
    if (episode.id == UUID(0, 0) || episode.type != BaseItemKind.EPISODE ||
        episode.seriesId != seriesId || seasonId == null || episode.extraType != null ||
        episode.isPlaceHolder == true || episode.locationType == LocationType.VIRTUAL ||
        episode.playAccess?.let { it != PlayAccess.FULL } == true ||
        episode.mediaSourceCount?.let { it <= 0 } == true
    ) return null
    return SeasonEpisodeIds(seasonId, episode.parentIndexNumber, episode.id, episode.indexNumber)
}

/** Exact IDs win. Ambiguous/missing dates never become an invented 'latest' episode. Specials are valid. */
internal suspend fun resolveRecentEpisodeTarget(
    row: HomeRowConfig?,
    item: BaseItem,
    seriesId: UUID,
    getEpisode: suspend (UUID) -> BaseItemDto,
    latestEpisodes: suspend (UUID) -> List<BaseItemDto>,
): SeasonEpisodeIds? {
    if (row !is HomeRowConfig.RecentlyAdded || item.data.extraType != null ||
        item.type !in listOf(BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.EPISODE)
    ) return null
    return try {
        if (item.type == BaseItemKind.EPISODE) {
            val episode = getEpisode(item.id)
            if (episode.id != item.id) null else recentEpisodeIds(episode, seriesId)
        } else {
            val parent = if (item.type == BaseItemKind.SEASON) item.id else seriesId
            latestEpisodes(parent)
                .filter { recentEpisodeIds(it, seriesId) != null && it.dateCreated != null }
                .filter { item.type != BaseItemKind.SEASON || recentEpisodeIds(it, seriesId)?.seasonId == item.id }
                .sortedWith(compareByDescending<BaseItemDto> { it.dateCreated }.thenBy { it.id.toString() })
                .firstOrNull()?.let { recentEpisodeIds(it, seriesId) }
        }
    } catch (ex: CancellationException) {
        throw ex
    } catch (_: Exception) {
        null // Full series page remains usable if optional targeting cannot be resolved.
    }
}
