package com.github.damontecres.wholphin.ui.main

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Real API omission semantics: one source is omitted, PlayAccess must be requested. */
class SpotlightSparseMetadataTest {
    private val scope = SpotlightScope(UUID(1, 1), UUID(2, 2), UUID(3, 3))
    private fun wireMovie() = Json.decodeFromString<BaseItemDto>(
        """{"Id":"00000000-0000-0000-0000-000000000001","Name":"Available movie","Type":"Movie","PlayAccess":"Full"}""",
    )
    private fun candidate(movie: BaseItemDto) = SpotlightCandidate(scope, movie)

    @Test fun omittedSingleSourceCountDoesNotMeanZeroPlayableSources() {
        val movie = wireMovie()
        assertNull(movie.mediaSourceCount)
        assertEquals(PlayAccess.FULL, movie.playAccess)
        assertTrue(spotlightEligible(candidate(movie), scope))
    }

    @Test fun missingRatingsAndBackdropStillProduceRealMovieFallback() {
        val movies = (1..10).map { wireMovie().copy(id = UUID(0, it.toLong())) }
        val selected = selectSpotlightMovies(movies.map(::candidate), scope, 100)
        assertEquals(5, selected.size)
        assertEquals(5, selected.map { it.id }.toSet().size)
        assertTrue(selected.all { it.type == BaseItemKind.MOVIE })
        assertTrue(selected.all { it.criticRating == null && it.communityRating == null })
        assertTrue(selected.all { !spotlightHasBackdrop(it) })
        assertEquals(selected, selectSpotlightMovies(movies.reversed().map(::candidate), scope, 100))
    }

    @Test fun explicitZeroNegativeVirtualAndDeniedRemainRejected() {
        val movie = wireMovie()
        val invalid = listOf(movie.copy(mediaSourceCount = 0), movie.copy(mediaSourceCount = -1),
            movie.copy(playAccess = PlayAccess.NONE), movie.copy(locationType = LocationType.VIRTUAL))
        invalid.forEach { assertFalse(spotlightEligible(candidate(it), scope)) }
    }

    @Test fun permissionsAreRequestedNotInventedFromAnOmittedField() {
        spotlightCandidateRequests(scope).forEach { request ->
            assertTrue(request.fields.orEmpty().contains(ItemFields.PLAY_ACCESS))
        }
        assertEquals(SpotlightRejection.PLAY_ACCESS_MISSING,
            spotlightRejection(candidate(wireMovie().copy(playAccess = null)), scope))
    }

    @Test fun backdropIsPreferredWithinABandWithoutBeingMandatory() {
        val fallback = wireMovie().copy(criticRating = 85f)
        val artwork = fallback.copy(id = UUID(0, 2), backdropImageTags = listOf("tag"))
        assertEquals(artwork.id, selectSpotlightMovies(listOf(candidate(fallback), candidate(artwork)),
            scope, epochDay = 0, limit = 1).single().id)
        assertEquals(fallback.id, selectSpotlightMovies(listOf(candidate(fallback)),
            scope, epochDay = 0).single().id)
    }

    @Test fun auditSeparatesRejectedMoviesFromMissingDecorativeMetadata() {
        val sparse = wireMovie()
        val full = sparse.copy(id = UUID(0, 2), mediaSourceCount = 1, backdropImageTags = listOf("tag"))
        val audit = spotlightPoolAudit(listOf(sparse, full,
            full.copy(id = UUID(0, 3), playAccess = PlayAccess.NONE),
            full.copy(id = UUID(0, 4), playAccess = null),
            full.copy(id = UUID(0, 5), type = BaseItemKind.EPISODE)).map(::candidate), scope)
        assertEquals(5, audit.fetched)
        assertEquals(2, audit.accepted)
        assertEquals(3, audit.rejections.values.sum())
        assertEquals(1, audit.missingBackdrop)
        assertEquals(1, audit.omittedSourceCount)
        assertEquals(1, audit.rejections[SpotlightRejection.PLAY_ACCESS_MISSING])
        assertEquals(1, audit.rejections[SpotlightRejection.PLAY_BLOCKED])
        assertEquals(1, audit.rejections[SpotlightRejection.NON_MOVIE])
    }
}
