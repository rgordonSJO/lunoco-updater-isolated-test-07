package com.github.damontecres.wholphin.ui.detail.series

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.successResponse
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.operations.TvShowsApi
import org.jellyfin.sdk.model.api.*
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RecentSeriesEpisodesTest {
    private val seriesId = UUID(1, 1)
    private val seasonId = UUID(2, 3)
    private val user = UUID(3, 1)
    private val seasons = List(4) { BaseItem(BaseItemDto(
        id = UUID(2, it.toLong()), type = BaseItemKind.SEASON, indexNumber = it,
    )) }
    private val api = mockk<ApiClient>()
    private val shows = mockk<TvShowsApi>()
    private fun episodes(count: Int) = List(count) { BaseItemDto(
        id = UUID(4, it.toLong() + 1), type = BaseItemKind.EPISODE, name = "Episode ${it + 1}",
        seriesId = seriesId, seasonId = seasonId, parentIndexNumber = 3, indexNumber = it + 1,
    ) }
    private fun pages(data: List<BaseItemDto>) {
        every { api.tvShowsApi } returns shows
        coEvery { shows.getEpisodes(any<GetEpisodesRequest>()) } answers {
            val r = firstArg<GetEpisodesRequest>()
            check(r.seriesId == seriesId && r.seasonId == seasonId && r.userId == user)
            successResponse(BaseItemDtoQueryResult(
                items = data.drop(r.startIndex ?: 0).take(r.limit ?: data.size), totalRecordCount = data.size, startIndex = r.startIndex ?: 0,
            ))
        }
    }
    @Test fun loadsCorrectSeasonBeforeExactEpisodeAndPagesPastOneHundred() = runTest {
        val data = episodes(130); pages(data)
        val result = loadRecentSeriesEpisodes(api, user, seriesId, seasons,
            SeasonEpisodeIds(seasonId, 3, data.last().id, 130), backgroundScope)
        assertEquals(3, result?.seasonIndex)
        assertEquals(129, result?.episodeIndex)
        assertEquals(data.last().id, result?.episodes?.get(129)?.id)
    }
    @Test fun waitsForPagedSeasonBeforeLoadingItsEpisodes() = runTest {
        val paged = mockk<com.github.damontecres.wholphin.util.ApiRequestPager<org.jellyfin.sdk.model.api.request.GetItemsRequest>>()
        every { paged.size } returns seasons.size
        coEvery { paged.getBlocking(any()) } answers { seasons[firstArg<Int>()] }
        val data = episodes(10); pages(data)
        val result = loadRecentSeriesEpisodes(api, user, seriesId, paged,
            SeasonEpisodeIds(seasonId, 3, data[6].id, 7), backgroundScope)
        assertEquals(3, result?.seasonIndex)
        assertEquals(6, result?.episodeIndex)
        coVerifyOrder {
            paged.getBlocking(3)
            shows.getEpisodes(any<GetEpisodesRequest>())
        }
    }

    @Test fun missingEpisodeDoesNotFocusAnotherWithSameNumber() = runTest {
        pages(episodes(10))
        assertNull(loadRecentSeriesEpisodes(api, user, seriesId, seasons,
            SeasonEpisodeIds(seasonId, 3, UUID(99, 99), 7), backgroundScope))
    }
    @Test fun missingSeasonMakesNoEpisodeRequest() = runTest {
        assertNull(loadRecentSeriesEpisodes(api, user, seriesId, emptyList(),
            SeasonEpisodeIds(seasonId, 3, UUID(4, 7), 7), backgroundScope))
    }
    @Test fun blockedEpisodeFallsBackToNormalSeries() = runTest {
        val data = episodes(7).map { it.copy(playAccess = PlayAccess.NONE) }; pages(data)
        assertNull(loadRecentSeriesEpisodes(api, user, seriesId, seasons,
            SeasonEpisodeIds(seasonId, 3, data.last().id, 7), backgroundScope))
    }
    @Test fun missingAndReorderedEpisodeNumbersStillUseExactIdentity() = runTest {
        val data = episodes(10).reversed().map { it.copy(indexNumber = null) }; pages(data)
        val result = loadRecentSeriesEpisodes(api, user, seriesId, seasons,
            SeasonEpisodeIds(seasonId, 3, UUID(4, 7), null), backgroundScope)
        assertEquals(3, result?.episodeIndex)
        assertEquals(UUID(4, 7), result?.episodeId)
    }
}
