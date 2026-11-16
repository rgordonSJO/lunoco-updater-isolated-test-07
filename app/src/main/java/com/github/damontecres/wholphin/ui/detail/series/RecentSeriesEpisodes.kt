package com.github.damontecres.wholphin.ui.detail.series

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.ItemRowFields
import com.github.damontecres.wholphin.ui.main.recentEpisodeIds
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.BlockingList
import com.github.damontecres.wholphin.util.GetEpisodesRequestHandler
import kotlinx.coroutines.CoroutineScope
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import java.util.UUID

data class RecentSeriesEpisodes(
    val season: BaseItem,
    val seasonIndex: Int,
    val episodes: List<BaseItem?>,
    val episodeIndex: Int,
    val episodeId: UUID,
)

/** Number is only a lookup hint; a missing ID must never focus a different numbered episode. */
internal suspend fun exactRecentItemIndex(
    items: List<BaseItem?>, id: UUID, numberHint: Int?,
): Int {
    suspend fun at(index: Int): BaseItem? =
        if (items is BlockingList<*>) (items as BlockingList<BaseItem?>).getBlocking(index) else items[index]
    val hint = numberHint?.minus(1)?.coerceAtLeast(0)
    if (hint != null && hint in items.indices && at(hint)?.id == id) return hint
    for (index in items.indices) if (index != hint && at(index)?.id == id) return index
    return -1
}

internal suspend fun loadRecentSeriesEpisodes(
    api: ApiClient,
    userId: UUID,
    seriesId: UUID,
    seasons: List<BaseItem?>,
    target: SeasonEpisodeIds,
    scope: CoroutineScope,
): RecentSeriesEpisodes? {
    val episodeId = target.episodeId ?: return null
    val seasonIndex = exactRecentItemIndex(seasons, target.seasonId, target.seasonNumber)
    if (seasonIndex < 0) return null
    val season = if (seasons is BlockingList<*>) {
        (seasons as BlockingList<BaseItem?>).getBlocking(seasonIndex)
    } else seasons[seasonIndex]
    season ?: return null
    val request = GetEpisodesRequest(
        seriesId = seriesId, userId = userId, seasonId = season.id,
        sortBy = ItemSortBy.INDEX_NUMBER, fields = ItemRowFields + listOf(
            org.jellyfin.sdk.model.api.ItemFields.PARENT_ID, org.jellyfin.sdk.model.api.ItemFields.PLAY_ACCESS,
        ),
    )
    val pager = ApiRequestPager(api, request, GetEpisodesRequestHandler, scope)
    pager.init((target.episodeNumber?.minus(1) ?: 0).coerceAtLeast(0))
    val episodeIndex = exactRecentItemIndex(pager, episodeId, target.episodeNumber)
    if (episodeIndex < 0) return null
    val episode = pager.getBlocking(episodeIndex) ?: return null
    if (episode.id != episodeId || recentEpisodeIds(episode.data, seriesId)?.seasonId != season.id) return null
    return RecentSeriesEpisodes(season, seasonIndex, pager, episodeIndex, episodeId)
}
