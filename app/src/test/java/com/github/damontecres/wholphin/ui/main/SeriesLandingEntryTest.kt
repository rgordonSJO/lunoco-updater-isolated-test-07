package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.nav.Destination
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Recently Added Shows and Kids Shows share the same user-permitted row contract. */
class SeriesLandingEntryTest {
    private val series = UUID(20, 1)
    private fun card(kind: BaseItemKind) = BaseItem(BaseItemDto(
        id = if (kind == BaseItemKind.SERIES) series else UUID(20, 9),
        type = kind, seriesId = series, seasonId = UUID(20, 3),
        parentIndexNumber = 3, indexNumber = 18,
    ))

    @Test fun showsEpisodeCardOpensOverviewWithoutEpisodeTarget() {
        val route = homeItemDestination(HomeRowConfig.RecentlyAdded(UUID(1, 1)), card(BaseItemKind.EPISODE))
        assertEquals(Destination.MediaItem(series, BaseItemKind.SERIES), route)
    }

    @Test fun kidsShowsEpisodeCardOpensOverviewWithoutEpisodeTarget() {
        val route = homeItemDestination(HomeRowConfig.RecentlyAdded(UUID(1, 2)), card(BaseItemKind.EPISODE))
        assertEquals(Destination.MediaItem(series, BaseItemKind.SERIES), route)
    }

    @Test fun seriesCardsDoNotFetchLatestEpisodeForEitherLibrary() = runTest {
        for (library in listOf(UUID(1, 1), UUID(1, 2))) {
            val api = mockk<ApiClient>()
            assertEquals(Destination.MediaItem(series, BaseItemKind.SERIES),
                resolveRecentSeriesDestination(api, UUID(3, 1), HomeRowConfig.RecentlyAdded(library), card(BaseItemKind.SERIES)))
            verify { api wasNot Called }
        }
    }

    @Test fun movieAndContinueWatchingRoutesRemainUnchanged() {
        val episode = card(BaseItemKind.EPISODE)
        assertEquals(episode.destination(), homeItemDestination(HomeRowConfig.ContinueWatching(), episode))
        val movie = card(BaseItemKind.MOVIE)
        assertEquals(movie.destination(), homeItemDestination(HomeRowConfig.RecentlyAdded(UUID(1, 1)), movie))
    }
}
