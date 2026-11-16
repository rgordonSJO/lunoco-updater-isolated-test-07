package com.github.damontecres.wholphin.ui.main

import org.jellyfin.sdk.model.api.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class SpotlightRotationTest {
    private val scope = SpotlightScope(UUID(1, 1), UUID(2, 2), UUID(3, 3))
    private val firstDay = LocalDate.of(2026, 11, 7).toEpochDay()
    private fun pool(n: Int) = (1..n).map { i ->
        SpotlightCandidate(scope, BaseItemDto(
            id = UUID(0, i.toLong()), name = "Movie %03d".format(i),
            sortName = "Movie %03d".format(i), type = BaseItemKind.MOVIE,
            playAccess = PlayAccess.FULL, mediaSourceCount = if (i % 4 == 0) null else 1,
            criticRating = if (i % 3 == 0) null else (80 + i % 20).toFloat(),
            communityRating = if (i % 5 == 0) null else 8f,
            backdropImageTags = if (i % 7 == 0) emptyList() else listOf("art"),
            runTimeTicks = 72_000_000_000L,
        ))
    }
    private fun week(n: Int): List<List<BaseItemDto>> = (0..6).map {
        selectSpotlightMovies(pool(n), scope, firstDay + it)
    }
    private fun audit(n: Int, lineups: List<List<BaseItemDto>>) {
        lineups.forEachIndexed { i, list ->
            println("ROTATION pool=$n date=" + LocalDate.ofEpochDay(firstDay + i) +
                " ids=" + list.joinToString(",") { it.id.leastSignificantBits.toString() })
        }
        println("ROTATION pool=$n unique=" + lineups.flatten().map { it.id }.toSet().size +
            " repeatedLeads=" + (7 - lineups.map { it.first().id }.toSet().size) +
            " overlaps=" + lineups.zipWithNext().joinToString(",") { (a,b) ->
                a.map { it.id }.toSet().intersect(b.map { it.id }.toSet()).size.toString()
            })
    }
    @Test fun sevenDatesHaveDistinctLeadsAndBroadQualityLineups() {
        val result = week(104)
        audit(104, result)
        assertEquals(7, result.map { it.first().id }.toSet().size)
        assertTrue(result.flatten().map { it.id }.toSet().size >= 30)
        result.forEach { day ->
            assertEquals(5, day.size)
            assertEquals(5, day.map { it.id }.toSet().size)
            assertTrue(day.all { spotlightEligible(SpotlightCandidate(scope, it), scope) })
            assertTrue(day.all { (spotlightRatings(it).critic ?: 0f) >= 70f ||
                (spotlightRatings(it).community ?: 0f) >= 7f })
        }
    }
    @Test fun aTwentyMoviePoolDoesNotRepeatItsLeadAfterFourDays() {
        val result = week(20)
        audit(20, result)
        assertEquals(7, result.map { it.first().id }.toSet().size)
        assertTrue(result.zipWithNext().all { (a,b) -> a.map { it.id }.toSet() != b.map { it.id }.toSet() })
    }
    @Test fun smallPoolsReuseOnlyWhenNecessaryAndFiveMoviesStillRotateLead() {
        for (n in listOf(1, 3, 5, 6, 7)) {
            val result = week(n)
            audit(n, result)
            assertEquals(minOf(n, 7), result.map { it.first().id }.toSet().size)
            assertTrue(result.all { it.size == minOf(n, 5) })
        }
    }
    @Test fun sameDayAndInputOrderAreStableAcrossAllSevenDates() {
        for (day in firstDay..firstDay + 6) {
            val candidates = pool(104)
            assertEquals(selectSpotlightMovies(candidates, scope, day),
                selectSpotlightMovies(candidates.reversed() + candidates, scope, day))
        }
    }
    @Test fun missingRatingFallbackRotatesRealPermittedMovies() {
        val candidates = pool(20).map { it.copy(movie = it.movie.copy(criticRating = null, communityRating = null)) }
        val leads = (0..6).map { selectSpotlightMovies(candidates, scope, firstDay + it).first().id }
        assertEquals(7, leads.toSet().size)
        assertTrue(selectSpotlightMovies(candidates, scope.copy(libraryId = UUID(9, 9)), firstDay).isEmpty())
    }
    @Test fun qualityShortlistRejectsWeakAndIneligibleAdditions() {
        val good = pool(30)
        val weak = pool(10).map { it.copy(movie = it.movie.copy(
            id = UUID(9, it.movie.id.leastSignificantBits), criticRating = 9f, communityRating = 2f)) }
        val blocked = pool(10).map { it.copy(movie = it.movie.copy(
            id = UUID(8, it.movie.id.leastSignificantBits), playAccess = PlayAccess.NONE)) }
        for (day in firstDay..firstDay + 6) {
            assertEquals(selectSpotlightMovies(good, scope, day), selectSpotlightMovies(good + weak + blocked, scope, day))
        }
    }
}
