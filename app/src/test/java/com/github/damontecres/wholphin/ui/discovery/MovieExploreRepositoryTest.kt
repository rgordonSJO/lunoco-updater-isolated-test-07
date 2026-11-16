package com.github.damontecres.wholphin.ui.discovery

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.successQueryResult
import com.github.damontecres.wholphin.ui.successResponse
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.*
import org.jellyfin.sdk.api.operations.*
import org.jellyfin.sdk.model.api.*
import org.jellyfin.sdk.model.api.request.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MovieExploreRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val server = mockk<ServerRepository>(relaxed = true)
    private val api = mockk<ApiClient>()
    private val items = mockk<ItemsApi>()
    private val studios = mockk<StudiosApi>()
    private val genres = mockk<GenresApi>()
    private val library = mockk<UserLibraryApi>()
    private val scope = MovieExploreScope(UUID(1, 1), UUID(2, 2), UUID(3, 3))
    private val studio = BaseItemDto(id = UUID(4, 1), name = "A24", type = BaseItemKind.STUDIO,
        movieCount = 1, imageTags = mapOf(ImageType.LOGO to "logo"))
    private val genre = BaseItemDto(id = UUID(5, 1), name = "Drama", type = BaseItemKind.GENRE)
    private val movie = BaseItemDto(id = UUID(6, 1), name = "Movie", type = BaseItemKind.MOVIE,
        backdropImageTags = listOf("backdrop"))

    @Before fun setup() {
        WholphinDispatchers.configure(dispatcher)
        every { server.apiClient } returns api
        every { server.currentServer?.id } returns scope.server
        every { server.currentUser?.id } returns scope.user
        every { api.itemsApi } returns items
        every { api.studiosApi } returns studios
        every { api.genresApi } returns genres
        every { api.userLibraryApi } returns library
        coEvery { library.getItem(scope.library, userId = scope.user) } returns successResponse(
            BaseItemDto(id = scope.library, type = BaseItemKind.COLLECTION_FOLDER, collectionType = CollectionType.MOVIES))
        coEvery { studios.getStudios(any<GetStudiosRequest>()) } returns successQueryResult(listOf(studio))
        coEvery { genres.getGenres(any<GetGenresRequest>()) } returns successQueryResult(listOf(genre))
        coEvery { items.getItems(any<GetItemsRequest>()) } returns successQueryResult(listOf(movie))
    }
    @After fun teardown() { WholphinDispatchers.reset() }

    @Test fun accountLibraryScopedRequestsAndCachedReentryNeedNoExtraQueries() = runTest(dispatcher) {
        val repository = MovieExploreRepository(server)
        val result = repository.load(scope)
        assertEquals(result, repository.load(scope))
        assertEquals(ImageType.LOGO, result.studios.single().image!!.type)
        assertEquals(movie.id, result.genres.single().image!!.item)
        coVerify(exactly = 13) { studios.getStudios(match<GetStudiosRequest> {
            it.userId == scope.user && it.parentId == scope.library &&
                it.includeItemTypes == listOf(BaseItemKind.MOVIE) && it.limit == 24 && !it.searchTerm.isNullOrBlank()
        }) }
        coVerify(exactly = 1) { genres.getGenres(match<GetGenresRequest> {
            it.userId == scope.user && it.parentId == scope.library &&
                it.includeItemTypes == listOf(BaseItemKind.MOVIE) && it.limit == 24
        }) }
        coVerify(exactly = 2) { items.getItems(any<GetItemsRequest>()) }
    }

    @Test fun unknownCountsMustProveNonemptyAndMissingImagesKeepNameFallback() = runTest(dispatcher) {
        coEvery { studios.getStudios(any<GetStudiosRequest>()) } returns successQueryResult(
            listOf(studio.copy(movieCount = null, imageTags = null), studio.copy(id = UUID(4, 2), movieCount = 0)))
        coEvery { items.getItems(any<GetItemsRequest>()) } returns successQueryResult(listOf(movie.copy(backdropImageTags = null)))
        val result = MovieExploreRepository(server).load(scope)
        assertEquals(1, result.studios.size)
        assertNull(result.studios.single().image)
        assertNull(result.genres.single().image)
    }

    @Test fun emptyAndFailedRepresentativeRequestsCannotCreateEmptyGenresOrInventArt() = runTest(dispatcher) {
        coEvery { items.getItems(any<GetItemsRequest>()) } returns successQueryResult()
        val result = MovieExploreRepository(server).load(scope)
        assertTrue(result.genres.isEmpty())
        assertTrue(result.studios.isEmpty()) // Aggregate counts never override scoped movie proof.
    }

    @Test fun targetedSearchFindsMajorStudioAfterMoreThanFortyEightUnrelatedGlobalNames() = runTest(dispatcher) {
        val disney = studio.copy(id = UUID(30,1), name = "Walt Disney Pictures")
        coEvery { studios.getStudios(any<GetStudiosRequest>()) } answers {
            val request = firstArg<GetStudiosRequest>()
            successQueryResult(when {
                request.searchTerm != "Disney" -> emptyList()
                request.startIndex == 0 -> List(24) { studio.copy(id = UUID(31,it.toLong()), name = "Disney Television $it") }
                else -> listOf(disney)
            })
        }
        val result = MovieExploreRepository(server).load(scope)
        assertEquals(listOf("Walt Disney Pictures"), result.studios.map { it.name })
        assertEquals(listOf(disney.id), result.studios.single().studioIds)
        coVerify(exactly = 1) { studios.getStudios(match<GetStudiosRequest> { it.searchTerm == "Disney" && it.startIndex == 24 }) }
        coVerify(exactly = 0) { studios.getStudios(match<GetStudiosRequest> { it.searchTerm.isNullOrBlank() }) }
    }

    @Test fun aggregateCountCannotExposeStudioWithoutAnAccessibleMovie() = runTest(dispatcher) {
        coEvery { items.getItems(match<GetItemsRequest> { it.studioIds != null }) } returns successQueryResult()
        val result = MovieExploreRepository(server).load(scope)
        assertTrue(result.studios.isEmpty())
        assertEquals(1, result.genres.size)
    }

    @Test fun primaryAndKidsCachesAreIsolatedAndReturningToPrimaryReusesMetadata() = runTest(dispatcher) {
        val kids = scope.copy(library = UUID(3,4))
        coEvery { library.getItem(kids.library,userId = kids.user) } returns successResponse(
            BaseItemDto(id = kids.library, type = BaseItemKind.COLLECTION_FOLDER, collectionType = CollectionType.MOVIES))
        val repository = MovieExploreRepository(server)
        val primary = repository.load(scope)
        val children = repository.load(kids)
        assertEquals(scope, primary.scope)
        assertEquals(kids, children.scope)
        assertEquals(primary, repository.load(scope))
        coVerify(exactly = 2) { genres.getGenres(any<GetGenresRequest>()) }
        coVerify(exactly = 26) { studios.getStudios(any<GetStudiosRequest>()) }
    }

    @Test fun coldMetadataLatencyAndWarmReentryAreMeasuredWithoutChangingQueryPolicy() = runTest(dispatcher) {
        coEvery { library.getItem(scope.library, userId = scope.user) } coAnswers {
            kotlinx.coroutines.delay(250)
            successResponse(BaseItemDto(id = scope.library, type = BaseItemKind.COLLECTION_FOLDER,
                collectionType = CollectionType.MOVIES))
        }
        coEvery { studios.getStudios(any<GetStudiosRequest>()) } coAnswers {
            kotlinx.coroutines.delay(250); successQueryResult(listOf(studio))
        }
        coEvery { genres.getGenres(any<GetGenresRequest>()) } coAnswers {
            kotlinx.coroutines.delay(250); successQueryResult(listOf(genre))
        }
        coEvery { items.getItems(any<GetItemsRequest>()) } coAnswers {
            kotlinx.coroutines.delay(250); successQueryResult(listOf(movie))
        }
        val repository = MovieExploreRepository(server)
        val start = testScheduler.currentTime
        val cold = repository.load(scope)
        val coldMs = testScheduler.currentTime - start
        val warmStart = testScheduler.currentTime
        assertSame(cold, repository.load(scope))
        val warmMs = testScheduler.currentTime - warmStart
        assertEquals(2500L, coldMs)
        assertEquals(0L, warmMs)
        coVerify(exactly = 1) { library.getItem(scope.library, userId = scope.user) }
        coVerify(exactly = 13) { studios.getStudios(any<GetStudiosRequest>()) }
        coVerify(exactly = 1) { genres.getGenres(any<GetGenresRequest>()) }
        coVerify(exactly = 2) { items.getItems(any<GetItemsRequest>()) }
        println("HOME_DISCOVERY_SIMULATED_COLD_MS=$coldMs;WARM_MS=$warmMs;METADATA_REQUESTS=17;ARTWORK_DOWNLOADS=0")
    }

    @Test fun simultaneousHomeAndExploreLoadsShareOneCandidateRequestSet() = runTest(dispatcher) {
        coEvery { library.getItem(scope.library, userId = scope.user) } coAnswers {
            kotlinx.coroutines.delay(250)
            successResponse(BaseItemDto(id = scope.library, type = BaseItemKind.COLLECTION_FOLDER,
                collectionType = CollectionType.MOVIES))
        }
        val repository = MovieExploreRepository(server)
        val home = async { repository.load(scope) }
        val explore = async { repository.load(scope) }
        advanceUntilIdle()
        assertSame(home.await(), explore.await())
        coVerify(exactly = 1) { library.getItem(scope.library, userId = scope.user) }
        coVerify(exactly = 13) { studios.getStudios(any<GetStudiosRequest>()) }
        coVerify(exactly = 1) { genres.getGenres(any<GetGenresRequest>()) }
        coVerify(exactly = 2) { items.getItems(any<GetItemsRequest>()) }
    }

    @Test(expected = CancellationException::class)
    fun foreignUserCannotReadCachedFacets() = runTest(dispatcher) {
        val repository = MovieExploreRepository(server)
        repository.load(scope)
        every { server.currentUser?.id } returns UUID(99, 99)
        repository.load(scope)
    }

    @Test(expected = IllegalArgumentException::class)
    fun deniedOrNonMoviesLibraryNeverQueriesDiscovery() = runTest(dispatcher) {
        coEvery { library.getItem(scope.library, userId = scope.user) } returns successResponse(
            BaseItemDto(id = scope.library, type = BaseItemKind.COLLECTION_FOLDER, collectionType = CollectionType.TVSHOWS))
        MovieExploreRepository(server).load(scope)
    }
}
