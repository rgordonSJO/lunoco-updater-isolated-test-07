package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RecentTvNavigationTest {
    private val seriesId = UUID.randomUUID()
    private val recent = HomeRowConfig.RecentlyAdded(UUID.randomUUID())
    private fun item(type: BaseItemKind, parent: UUID? = seriesId) = BaseItem(
        BaseItemDto(id = UUID.randomUUID(), type = type,
            seriesId = parent, seasonId = UUID.randomUUID(), parentIndexNumber = 2, indexNumber = 4),
    )

    @Test fun recentEpisodeOpensFullSeriesOverviewWithoutInitialEpisodeTarget() = runTest {
        val episode = item(BaseItemKind.EPISODE)
        val route = resolveHomeItemDestination(recent, episode) { true }
        val encoded = kotlinx.serialization.json.Json { classDiscriminator = "_destination" }.encodeToString(Destination.serializer(), route)
        assertFalse("Fresh series entry must not carry an episode-focus target", encoded.contains("recentEpisode"))
        assertNull((route as Destination.MediaItem).recentEpisode)
        assertTrue(encoded.contains(seriesId.toString()))
    }

    @Test fun recentEpisodeAndSeasonKeepCompleteSeriesLanding() {
        for (type in listOf(BaseItemKind.EPISODE, BaseItemKind.SEASON)) {
            val card = item(type)
            val route = homeItemDestination(recent, card) as Destination.MediaItem
            assertEquals(seriesId, route.itemId)
            assertEquals(BaseItemKind.SERIES, route.type)
            assertNull(route.recentEpisode)
        }
        val series = item(BaseItemKind.SERIES)
        assertEquals(Destination.MediaItem(series.id, BaseItemKind.SERIES),
            homeItemDestination(recent, series))
    }

    @Test fun resumeNextUpCustomRowsAndMoviesKeepTheirDestinations() {
        val episode = item(BaseItemKind.EPISODE)
        for (row in listOf(null, HomeRowConfig.ContinueWatching(), HomeRowConfig.NextUp())) {
            assertEquals(episode.destination(), homeItemDestination(row, episode))
        }
        val movie = item(BaseItemKind.MOVIE)
        assertEquals(movie.destination(), homeItemDestination(recent, movie))
    }

    @Test fun missingParentMetadataFallsBack() {
        val orphan = item(BaseItemKind.EPISODE, null)
        assertEquals(orphan.destination(), homeItemDestination(recent, orphan))
    }

    @Test fun unavailableParentFallsBackAndChecksOnlyActivatedRecentTv() = runTest {
        val episode = item(BaseItemKind.EPISODE)
        assertEquals(episode.destination(), resolveHomeItemDestination(recent, episode) { false })
        assertEquals(episode.destination(), resolveHomeItemDestination(recent, episode) { error("404") })
        assertEquals(homeItemDestination(recent, episode),
            resolveHomeItemDestination(recent, episode) { true })
        assertEquals(episode.destination(),
            resolveHomeItemDestination(HomeRowConfig.NextUp(), episode) { fail("Must not query"); false })
    }

    @Test fun oldLibraryRoutesRemainCompatibleAndRecentTargetRoundTrips() {
        val json = kotlinx.serialization.json.Json { classDiscriminator = "_destination" }
        val normal = Destination.MediaItem(seriesId, BaseItemKind.SERIES)
        val oldPayload = json.encodeToString(Destination.serializer(), normal)
        assertFalse(oldPayload.contains("recentEpisode"))
        assertEquals(normal, json.decodeFromString(Destination.serializer(), oldPayload))
        // Old serialized targeted routes still decode; new Home activations no longer create them.
        val targeted = normal.copy(recentEpisode = com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds(
            UUID(9, 1), 3, UUID(9, 7), 7))
        assertEquals(targeted, json.decodeFromString(Destination.serializer(), json.encodeToString(Destination.serializer(), targeted)))
    }

    @Test fun cancellationIsNotConvertedToNavigation() = runTest {
        try {
            resolveHomeItemDestination(recent, item(BaseItemKind.EPISODE)) { throw CancellationException() }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
