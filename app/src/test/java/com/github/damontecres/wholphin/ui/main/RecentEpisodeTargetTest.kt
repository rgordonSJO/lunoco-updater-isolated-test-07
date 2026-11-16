package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.detail.series.exactRecentItemIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class RecentEpisodeTargetTest {
    private val series = UUID(1, 1)
    private val season = UUID(2, 3)
    private val recent = HomeRowConfig.RecentlyAdded(UUID(3, 1))
    private fun episode(n: Int, date: Int? = n) = BaseItemDto(
        id = UUID(4, n.toLong()), type = BaseItemKind.EPISODE, seriesId = series,
        seasonId = season, parentId = season, parentIndexNumber = 3, indexNumber = n,
        dateCreated = date?.let { LocalDateTime.of(2026, 1, it, 12, 0) },
    )
    private val seriesCard get() = BaseItem(BaseItemDto(id = series, type = BaseItemKind.SERIES))
    private suspend fun latest(candidates: List<BaseItemDto>) =
        resolveRecentEpisodeTarget(recent, seriesCard, series, { error("No episode ID on card") }, {
            assertEquals(series, it); candidates
        })

    @Test fun exactEpisodeWinsWithoutLatestQuery() = runTest {
        val exact = episode(7)
        val selected = resolveRecentEpisodeTarget(recent, BaseItem(exact), series,
            { assertEquals(exact.id, it); exact }, { error("Must retain exact ID") })
        assertEquals(exact.id, selected?.episodeId)
        assertEquals(season, selected?.seasonId)
        assertEquals(3, selected?.seasonNumber)
    }
    @Test fun newestAddedIsNotHighestEpisodeOrMostRecentlyAired() = runTest {
        val newestAirDate = episode(10, 1).copy(premiereDate = LocalDateTime.of(2026, 2, 10, 12, 0))
        val newlyAddedOldEpisode = episode(2, 8).copy(premiereDate = LocalDateTime.of(2020, 1, 1, 12, 0))
        assertEquals(episode(2).id, latest(listOf(newestAirDate, newlyAddedOldEpisode))?.episodeId)
    }
    @Test fun missingDateDoesNotInventLatest() = runTest {
        assertNull(latest(listOf(episode(7, null))))
    }
    @Test fun batchAdditionTieBreakIsStable() = runTest {
        val a = episode(5, 8); val b = episode(7, 8)
        assertEquals(latest(listOf(a, b)), latest(listOf(b, a)))
    }
    @Test fun specialsUseSeasonZeroAndExactId() = runTest {
        val ep = episode(7).copy(parentIndexNumber = 0)
        assertEquals(0, recentEpisodeIds(ep, series)?.seasonNumber)
    }
    @Test fun missingNumbersStillUseIds() = runTest {
        val ep = episode(7).copy(indexNumber = null, parentIndexNumber = null)
        assertEquals(ep.id, recentEpisodeIds(ep, series)?.episodeId)
    }
    @Test fun ineligibleAndForeignEpisodesAreRejected() {
        val ep = episode(7)
        listOf(ep.copy(seriesId = UUID(99, 1)), ep.copy(type = BaseItemKind.SEASON),
            ep.copy(isPlaceHolder = true), ep.copy(locationType = LocationType.VIRTUAL),
            ep.copy(playAccess = PlayAccess.NONE), ep.copy(mediaSourceCount = 0),
            ep.copy(id = UUID(0, 0)), ep.copy(seasonId = null, parentId = null),
        ).forEach { assertNull(recentEpisodeIds(it, series)) }
    }
    @Test fun unavailableExactEpisodeFallsBackWithoutChoosingAnother() = runTest {
        assertNull(resolveRecentEpisodeTarget(recent, BaseItem(episode(7)), series,
            { error("404") }, { error("No replacement episode allowed") }))
    }
    @Test fun continueWatchingNextUpAndOrdinaryEntriesNeverQuery() = runTest {
        for (row in listOf(null, HomeRowConfig.ContinueWatching(), HomeRowConfig.NextUp())) {
            assertNull(resolveRecentEpisodeTarget(row, seriesCard, series, { error("query") }, { error("query") }))
        }
    }
    @Test fun seasonCardCannotTargetAnotherSeason() = runTest {
        val card = BaseItem(BaseItemDto(id = season, type = BaseItemKind.SEASON, seriesId = series))
        val result = resolveRecentEpisodeTarget(recent, card, series, { error("query") }, {
            assertEquals(season, it)
            listOf(episode(7).copy(seasonId = UUID(2, 4)), episode(3))
        })
        assertEquals(episode(3).id, result?.episodeId)
    }
    @Test fun requestUsesAccountParentAndDateCreatedWithoutWatchFilter() {
        val user = UUID(8, 9)
        val request = recentEpisodeRequest(user, series)
        assertEquals(user, request.userId)
        assertEquals(series, request.parentId)
        assertEquals(listOf(BaseItemKind.EPISODE), request.includeItemTypes)
        assertEquals(listOf(ItemSortBy.DATE_CREATED), request.sortBy)
        assertEquals(listOf(SortOrder.DESCENDING), request.sortOrder)
        assertEquals(20, request.limit)
        assertNull(request.isPlayed)
    }
    @Test fun cancellationPropagates() = runTest {
        try {
            resolveRecentEpisodeTarget(recent, BaseItem(episode(7)), series,
                { throw CancellationException() }, { emptyList() })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
    @Test fun focusLookupUsesExactIdWithMissingReorderedNumbers() = runTest {
        val ep = episode(7)
        val items = listOf(BaseItem(episode(1)), BaseItem(ep), BaseItem(episode(8)))
        assertEquals(1, exactRecentItemIndex(items, ep.id, 7))
        assertEquals(-1, exactRecentItemIndex(items, UUID(9, 9), 7))
        assertEquals(-1, exactRecentItemIndex(emptyList(), ep.id, 7))
    }
}
