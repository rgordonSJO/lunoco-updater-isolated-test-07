package com.github.damontecres.wholphin.ui.discovery

import org.jellyfin.sdk.model.api.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MovieExploreTest {
    private val scope = MovieExploreScope(UUID(1, 1), UUID(2, 2), UUID(3, 3))
    private fun facet(n: Int, kind: MovieFacetKind = MovieFacetKind.STUDIO) =
        MovieFacet(UUID(0, n.toLong()), "Studio $n", kind)

    @Test fun studioPrefersActualLogoThenThumbThenPrimaryAndNeverMovieArtwork() {
        val studio = BaseItemDto(type = BaseItemKind.STUDIO, id = UUID(0, 1), imageTags = mapOf(ImageType.PRIMARY to "p",
            ImageType.THUMB to "t", ImageType.LOGO to "l"), backdropImageTags = listOf("not-a-logo"))
        assertEquals(MovieExploreImage(studio.id, ImageType.LOGO, "l"), studioArtwork(studio))
        assertEquals(ImageType.THUMB, studioArtwork(studio.copy(imageTags = mapOf(ImageType.THUMB to "t")))!!.type)
        assertNull(studioArtwork(studio.copy(imageTags = emptyMap())))
        assertNull(studioArtwork(studio.copy(imageTags = mapOf(ImageType.LOGO to ""))))
    }

    @Test fun movieOrGenreLogoCannotMasqueradeAsStudioArtwork() {
        for (type in listOf(BaseItemKind.MOVIE, BaseItemKind.GENRE, BaseItemKind.SERIES)) {
            assertNull(studioArtwork(BaseItemDto(id = UUID(0, 99), type = type,
                imageTags = mapOf(ImageType.LOGO to "wrong-logo"))))
        }
    }

    @Test fun genreUsesOnlyRepresentativeMovieBackdropOrThumb() {
        val movie = BaseItemDto(type = BaseItemKind.MOVIE, id = UUID(0, 2), backdropImageTags = listOf(""),
            imageTags = mapOf(ImageType.PRIMARY to "poster", ImageType.LOGO to "movie-logo"))
        assertNull(genreArtwork(movie))
        assertEquals(movie.id, genreArtwork(movie.copy(backdropImageTags = listOf("backdrop")))!!.item)
        assertEquals(ImageType.THUMB, genreArtwork(movie.copy(imageTags = mapOf(ImageType.THUMB to "t")))!!.type)
    }

    @Test fun invalidOrKnownEmptyCategoriesAreExcluded() {
        assertFalse(validMovieFacet(BaseItemDto(type = BaseItemKind.STUDIO, id = UUID(0, 0), name = "Studio")))
        assertFalse(validMovieFacet(BaseItemDto(type = BaseItemKind.STUDIO, id = UUID(0, 1), name = " ")))
        assertFalse(validMovieFacet(BaseItemDto(type = BaseItemKind.STUDIO, id = UUID(0, 1), name = "Studio", movieCount = 0)))
        assertTrue(validMovieFacet(BaseItemDto(type = BaseItemKind.STUDIO, id = UUID(0, 1), name = "Studio")))
    }

    @Test fun studioFilterKeepsExactLibraryMovieTypeAndSeparateDisplayPreferences() {
        val studio = facet(1)
        val destination = studio.destination(scope)
        assertEquals(scope.library, destination.itemId)
        assertEquals(CollectionType.MOVIES, destination.collectionType)
        assertEquals(BaseItemKind.STUDIO, destination.parentType)
        assertEquals(listOf(studio.id), destination.filter.filter.studios)
        assertNull(destination.filter.filter.genres)
        assertEquals(listOf(BaseItemKind.MOVIE), destination.filter.filter.includeItemTypes)
        assertTrue(destination.recursive)
        assertNotEquals(scope.library.toString(), destination.filter.libraryDisplayInfoIdOverride)
    }

    @Test fun genreAndKidsLibraryNeverLeakIntoPrimaryMoviesScope() {
        val genre = facet(1, MovieFacetKind.GENRE)
        val kids = scope.copy(library = UUID(9, 9), user = UUID(8, 8))
        val request = facetMovieRequest(kids, genre)
        assertEquals(kids.user, request.userId)
        assertEquals(kids.library, request.parentId)
        assertEquals(listOf(genre.id), request.genreIds)
        assertNull(request.studioIds)
        assertEquals(3, request.limit)
        assertEquals(listOf(BaseItemKind.MOVIE), request.includeItemTypes)
        assertEquals(kids.library, genre.destination(kids).itemId)
    }

    @Test fun restoreByIdentityAfterReorderThenNearestSurvivorWhenRemoved() {
        val items = (1..10).map { facet(it) }
        assertEquals(8, restoredFacetIndex(items.reversed(), items[1].key, 1))
        assertEquals(8, restoredFacetIndex(items.dropLast(1), items.last().key, 9))
        assertEquals(0, restoredFacetIndex(emptyList(), "missing", 9))
    }

    @Test fun facetKeysAndSavedFiltersCannotCollideAcrossKindsOrLibraries() {
        val studio = facet(1)
        val genre = facet(1, MovieFacetKind.GENRE)
        assertNotEquals(studio.key, genre.key)
        assertNotEquals(studio.destination(scope).filter.libraryDisplayInfoIdOverride,
            genre.destination(scope).filter.libraryDisplayInfoIdOverride)
        assertNotEquals(studio.destination(scope).filter.libraryDisplayInfoIdOverride,
            studio.destination(scope.copy(library = UUID(9, 9))).filter.libraryDisplayInfoIdOverride)
    }
}
