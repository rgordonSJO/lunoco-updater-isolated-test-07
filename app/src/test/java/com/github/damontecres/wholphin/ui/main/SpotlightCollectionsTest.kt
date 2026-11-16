package com.github.damontecres.wholphin.ui.main

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.*
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SpotlightCollectionsTest {
    private val scope = SpotlightScope(UUID(1, 1), UUID(2, 2), UUID(3, 3))
    private fun movie(n: Int) = BaseItemDto(id = UUID(0, n.toLong()), type = BaseItemKind.MOVIE,
        name = "Movie $n", criticRating = 90f, playAccess = PlayAccess.FULL,
        backdropImageTags = listOf("image"))
    private fun select(movies: List<BaseItemDto>, day: Long, membership: Map<UUID, Set<UUID>>) =
        selectSpotlightMovies(movies.map { SpotlightCandidate(scope, it) }, scope, day, collections = membership)

    @Test fun onePerVerifiedFranchiseAcrossSevenDaysWithStableDailyLeadTraversal() {
        val movies = (1..40).map(::movie)
        val membership = movies.associate { it.id to setOf(UUID(9, (it.id.leastSignificantBits / 4))) }
        val days = (100L..106L).map { select(movies, it, membership) }
        assertEquals(7, days.map { it.first().id }.distinct().size)
        assertTrue("Collection diversity must also vary movies across the week", days.flatten().map { it.id }.distinct().size >= 25)
        days.forEachIndexed { index, lineup ->
            println("SPOTLIGHT10A_DAY=${100 + index};before=" +
                select(movies, 100L + index, emptyMap()).joinToString { it.id.leastSignificantBits.toString() } +
                ";after=" + lineup.joinToString { it.id.leastSignificantBits.toString() })
            assertEquals(5, lineup.size)
            assertEquals(5, lineup.flatMap { membership.getValue(it.id) }.distinct().size)
            assertEquals(lineup, select(movies.reversed(), 100L + index, membership))
            assertEquals(select(movies, 100L + index, emptyMap()).first(), lineup.first())
        }
    }

    @Test fun overlappingCollectionsCannotLeakASiblingIntoLineup() {
        val movies = (1..20).map(::movie)
        val membership = movies.take(3).associate { it.id to setOf(UUID(9, 1), UUID(9, 2)) }
        for (day in 1L..30L) assertTrue(select(movies, day, membership).count { it.id in membership } <= 1)
    }

    @Test fun smallCollectionOnlyPoolStillFillsWithoutDuplicatesOrInventedMovies() {
        val movies = (1..3).map(::movie)
        val membership = movies.associate { it.id to setOf(UUID(9, 1)) }
        assertEquals(movies.map { it.id }.toSet(), select(movies, 5, membership).map { it.id }.toSet())
    }

    @Test fun qualityFallbackUsedForDiversityOnlyWhenQualityPoolCannotSupplyDistinctCollections() {
        val rated = (1..8).map(::movie)
        val fallback = (9..15).map { movie(it).copy(criticRating = null) }
        val membership = rated.associate { it.id to setOf(UUID(9, 1)) }
        val result = select(rated + fallback, 5, membership)
        assertEquals(1, result.count { it.id in membership })
        assertEquals(5, result.size)
        assertNotNull(result.first().criticRating)
    }

    @Test fun noBrittleTitleGuessingAndNoSixtyMovieExclusion() {
        val movies = (1..100).map { movie(it).copy(name = "Toy Story $it") }
        assertEquals(5, select(movies, 5, emptyMap()).size)
        val leads = (0L..99L).map { select(movies, it, emptyMap()).first().id }.toSet()
        assertEquals(100, leads.size)
    }

    @Test fun collectionQueriesAreBoundedUserScopedAndIntersectPrimaryCandidateIds() = runTest {
        val boxes = (1..100).map { BaseItemDto(id = UUID(8, it.toLong()), type = BaseItemKind.BOX_SET) }
        val requests = mutableListOf<GetItemsRequest>()
        val accepted = movie(1)
        val result = spotlightCollections(scope, setOf(accepted.id, movie(2).id)) { request ->
            requests += request
            if (request.parentId == null) boxes
            else listOf(accepted, movie(999), accepted.copy(id = movie(2).id, type = BaseItemKind.EPISODE))
        }
        assertEquals(setOf(accepted.id), result.keys)
        assertEquals(64, result.getValue(accepted.id).size)
        assertEquals(65, requests.size)
        requests.forEach {
            assertEquals(scope.userId, it.userId)
            assertEquals(false, it.enableImages)
            assertEquals(false, it.enableUserData)
            assertTrue(it.limit!! <= 100)
        }
    }

    @Test fun unavailableCollectionsDegradeWithoutHidingEligibleMovies() = runTest {
        assertTrue(spotlightCollections(scope, setOf(movie(1).id, movie(2).id)) { error("offline") }.isEmpty())
        assertEquals(2, select((1..2).map(::movie), 5, emptyMap()).size)
    }

    @Test(expected = CancellationException::class)
    fun accountCancellationIsNeverSwallowed() = runTest {
        spotlightCollections(scope, setOf(movie(1).id, movie(2).id)) { throw CancellationException() }
    }

    @Test fun singletonPoolDoesNotRequestCollections() = runTest {
        assertTrue(spotlightCollections(scope, setOf(movie(1).id)) { error("must not query") }.isEmpty())
    }
}
