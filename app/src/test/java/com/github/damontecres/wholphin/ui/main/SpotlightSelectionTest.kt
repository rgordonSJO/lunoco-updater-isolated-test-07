package com.github.damontecres.wholphin.ui.main

import org.jellyfin.sdk.model.api.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SpotlightSelectionTest {
    private val scope = SpotlightScope(UUID(1, 1), UUID(2, 2), UUID(3, 3))
    private fun movie(index: Int, critic: Float? = null, community: Float? = null) = BaseItemDto(
        id = UUID(0, index.toLong()), type = BaseItemKind.MOVIE, name = "Movie $index",
        playAccess = PlayAccess.FULL, mediaSourceCount = 1, backdropImageTags = listOf("backdrop"),
        criticRating = critic, communityRating = community,
    )
    private fun pick(items: List<BaseItemDto>, day: Long = 100) =
        selectSpotlightMovies(items.map { SpotlightCandidate(scope, it) }, scope, day)

    @Test fun queryBudgetIsBoundedAndScopedToPermittedMovies() {
        val requests = spotlightCandidateRequests(scope)
        assertEquals(3, requests.size)
        assertEquals(120, requests.sumOf { it.limit ?: 0 })
        requests.forEach {
            assertEquals(scope.userId, it.userId)
            assertEquals(scope.libraryId, it.parentId)
            assertEquals(listOf(BaseItemKind.MOVIE), it.includeItemTypes)
            assertEquals(true, it.recursive)
            assertEquals(false, it.isMissing)
            assertEquals(false, it.isPlaceHolder)
            assertEquals(false, it.enableTotalRecordCount)
            assertTrue(it.fields.orEmpty().contains(ItemFields.PLAY_ACCESS))
            assertTrue(it.fields.orEmpty().contains(ItemFields.MEDIA_SOURCE_COUNT))
        }
    }

    @Test fun excludesEveryNonMovieKind() {
        val nonMovies = listOf(BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.EPISODE,
            BaseItemKind.VIDEO, BaseItemKind.PROGRAM, BaseItemKind.BOX_SET, BaseItemKind.AUDIO)
        assertTrue(pick(nonMovies.mapIndexed { i, type -> movie(i + 1).copy(type = type) }).isEmpty())
    }

    @Test fun candidatesCannotCrossServerUserOrLibraryScope() {
        val foreign = listOf(scope.copy(serverId = UUID(9, 9)),
            scope.copy(userId = UUID(9, 9)), scope.copy(libraryId = UUID(9, 9)))
        assertTrue(selectSpotlightMovies(foreign.map { SpotlightCandidate(it, movie(1)) }, scope, 100).isEmpty())
    }

    @Test fun rejectsMissingVirtualUnavailableAndInvalidIdentity() {
        val good = movie(1)
        val bad = listOf(good.copy(isPlaceHolder = true), good.copy(locationType = LocationType.VIRTUAL),
            good.copy(playAccess = PlayAccess.NONE), good.copy(mediaSourceCount = 0),
            good.copy(mediaSourceCount = -1), good.copy(playAccess = null),
            good.copy(name = ""), good.copy(id = UUID(0, 0)), good.copy(extraType = ExtraType.TRAILER))
        assertTrue(pick(bad).isEmpty())
        assertTrue(spotlightEligible(SpotlightCandidate(scope, good), scope))
    }

    @Test fun validatesEachRatingOnItsOwnScale() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 101f))
            assertNull(spotlightRatings(movie(1, critic = value)).critic)
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 10.1f))
            assertNull(spotlightRatings(movie(1, community = value)).community)
        assertEquals(90f, spotlightRatings(movie(1, 90f, 9f)).critic)
        assertEquals(9f, spotlightRatings(movie(1, 90f, 9f)).community)
    }

    @Test fun aNinePointCriticScoreIsNotTreatedAsNinetyPercent() {
        val goodCommunity = (1..5).map { movie(it, community = 8f) }
        val weakCritics = (6..15).map { movie(it, critic = 9f) }
        assertEquals(goodCommunity.map { it.id }.toSet(), pick(goodCommunity + weakCritics).map { it.id }.toSet())
    }

    @Test fun coarseBandsDoNotPrioritizePerfectRawScoresWithinTheBand() {
        val sameBand = (1..20).map { movie(it, critic = if (it == 20) 100f else 90f) }
        val unchanged = sameBand.map { it.copy(criticRating = 95f) }
        assertEquals(pick(sameBand).map { it.id }, pick(unchanged).map { it.id })
    }

    @Test fun deterministicSelectionDoesNotDependOnResponseOrder() {
        val candidates = (1..30).map { movie(it, critic = 85f) }
        assertEquals(pick(candidates).map { it.id }, pick(candidates.reversed()).map { it.id })
        assertEquals(pick(candidates), pick(candidates))
    }

    @Test fun dailyRotationChangesTheShortlistWithoutRandomFocusReordering() {
        val candidates = (1..20).map { movie(it, critic = 90f) }
        assertNotEquals(pick(candidates, 100).map { it.id }.toSet(), pick(candidates, 101).map { it.id }.toSet())
        assertEquals(pick(candidates, 100), pick(candidates, 100))
    }

    @Test fun missingRatingsHaveARealMovieFallbackAndNeverFakeFiller() {
        assertTrue(pick(emptyList()).isEmpty())
        assertEquals(1, pick(listOf(movie(1))).size)
        assertEquals(3, pick((1..3).map(::movie)).size)
        assertEquals(5, pick((1..20).map(::movie)).size)
    }

    @Test fun deduplicatesAndCapsFive() {
        val candidates = (1..20).map { SpotlightCandidate(scope, movie(it, 80f)) }
        val result = selectSpotlightMovies(candidates + candidates, scope, 100, 99)
        assertEquals(5, result.size)
        assertEquals(5, result.map { it.id }.toSet().size)
        assertTrue(selectSpotlightMovies(candidates, scope, 100, 0).isEmpty())
    }

    @Test fun moviePlayResumeAndDetailsUseExistingDestinations() {
        val item = movie(1).copy(runTimeTicks = 60_000_000L,
            userData = UserItemDataDto(playbackPositionTicks = 20_000_000L, played = false,
                playCount = 0, isFavorite = false, key = "test", itemId = UUID(0, 1)))
        assertEquals(2000L, spotlightResumeMs(item))
        assertEquals(item.id, spotlightPlayback(item).itemId)
        assertEquals(2000L, spotlightPlayback(item).positionMs)
        assertEquals(BaseItemKind.MOVIE, spotlightDetails(item).type)
        assertEquals(item.id, spotlightDetails(item).itemId)
        assertEquals(0L, spotlightResumeMs(item.copy(userData = item.userData?.copy(played = true))))
        assertEquals(0L, spotlightResumeMs(item.copy(runTimeTicks = null)))
        assertEquals(0L, spotlightResumeMs(item.copy(userData = item.userData?.copy(playbackPositionTicks = -1))))
        assertEquals(0L, spotlightResumeMs(item.copy(userData = item.userData?.copy(playbackPositionTicks = 90_000_000L))))
    }

    @Test fun manualPagingWrapsAndDoesNotMutateCandidates() {
        val movies = (1..5).map(::movie)
        assertEquals(4, spotlightPageIndex(0, movies.size, -1))
        assertEquals(0, spotlightPageIndex(4, movies.size, 1))
        assertEquals(0, spotlightPageIndex(0, 0, 1))
        assertEquals((1..5).map { UUID(0, it.toLong()) }, movies.map { it.id })
    }
}
