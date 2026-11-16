package com.github.damontecres.wholphin.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.util.GetGenresRequestHandler
import com.github.damontecres.wholphin.util.GetStudiosRequestHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.*
import org.jellyfin.sdk.model.api.request.*
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MovieExploreRepository @Inject constructor(private val server: ServerRepository) {
    private val mutex = Mutex()
    private data class Cached(val state: MovieExploreState, val at: Long)
    private val cache = LinkedHashMap<MovieExploreScope, Cached>()

    suspend fun load(scope: MovieExploreScope): MovieExploreState = withContext(WholphinDispatchers.IO) {
        mutex.withLock {
            requireScope(scope)
            cache.keys.removeAll { it.server != scope.server || it.user != scope.user }
            cache[scope]?.takeIf { System.nanoTime() - it.at < 600_000_000_000L }
                ?.let { return@withLock it.state }
            val permits = Semaphore(2)
            val folder = server.apiClient.userLibraryApi.getItem(scope.library, userId = scope.user).content
            require(folder.type == BaseItemKind.COLLECTION_FOLDER && folder.collectionType == CollectionType.MOVIES)
            val (studios, genres) = coroutineScope {
                val studios = async {
                    coroutineScope {
                        curatedStudios.flatMap { it.searches }.distinct().map { search -> async {
                            permits.withPermit {
                                val found = mutableListOf<BaseItemDto>()
                                try {
                                    // Search across the library before limiting; never filter the first global page.
                                    for (page in 0..1) {
                                        requireScope(scope)
                                        val batch = GetStudiosRequestHandler.execute(server.apiClient,
                                            curatedStudioRequest(scope, search, page)).content.items
                                        found += batch
                                        if (batch.size < 24) break
                                    }
                                } catch (ex: CancellationException) { throw ex }
                                catch (_: Exception) { Timber.w("Curated studio search unavailable") }
                                found
                            }
                        } }.awaitAll().flatten().distinctBy { it.id }
                    }
                }
                val genres = async {
                    requireScope(scope)
                    GetGenresRequestHandler.execute(server.apiClient, GetGenresRequest(
                        userId = scope.user, parentId = scope.library,
                        includeItemTypes = listOf(BaseItemKind.MOVIE), fields = listOf(ItemFields.ITEM_COUNTS),
                        sortBy = listOf(ItemSortBy.SORT_NAME), limit = 24,
                        enableImages = false, enableTotalRecordCount = false,
                    )).content.items
                }
                studios.await() to genres.await()
            }
            suspend fun facets(items: List<BaseItemDto>, kind: MovieFacetKind): List<MovieFacet> = coroutineScope {
                items.filter { validMovieFacet(it) && it.type ==
                    if (kind == MovieFacetKind.STUDIO) BaseItemKind.STUDIO else BaseItemKind.GENRE
                }.distinctBy { it.id }.sortedWith(
                    compareBy<BaseItemDto> { it.sortName ?: it.name.orEmpty() }.thenBy { it.id.toString() },
                ).map { item -> async {
                    val facet = MovieFacet(item.id, item.name!!, kind,
                        if (kind == MovieFacetKind.STUDIO) studioArtwork(item) else null)
                    // Known studio counts avoid an extra request. Unknown counts are never assumed nonempty.
                    if (kind == MovieFacetKind.STUDIO && (item.movieCount ?: 0) > 0) facet
                    else permits.withPermit {
                        requireScope(scope)
                        try {
                            val movies = server.apiClient.itemsApi.getItems(facetMovieRequest(scope, facet))
                                .content.items.filter { it.type == BaseItemKind.MOVIE }
                            val movie = movies.firstOrNull { genreArtwork(it) != null } ?: movies.firstOrNull()
                            movie?.let { facet.copy(image = if (kind == MovieFacetKind.GENRE) genreArtwork(it) else facet.image) }
                        } catch (ex: CancellationException) { throw ex }
                        catch (_: Exception) { Timber.w("Explore category metadata unavailable"); null }
                    }
                } }.awaitAll().filterNotNull()
            }
            // Prove each exact alias with an accessible movie, even when aggregate counts exist.
            val verified = coroutineScope {
                studios.filter { item -> curatedStudios.any { it.matches(item) } }.map { item -> async {
                    permits.withPermit {
                        requireScope(scope)
                        val facet = MovieFacet(item.id, item.name!!, MovieFacetKind.STUDIO)
                        try {
                            server.apiClient.itemsApi.getItems(facetMovieRequest(scope, facet)).content.items
                                .firstOrNull { it.type == BaseItemKind.MOVIE }?.let { item }
                        } catch (ex: CancellationException) { throw ex }
                        catch (_: Exception) { Timber.w("Curated studio scope verification unavailable"); null }
                    }
                } }.awaitAll().filterNotNull()
            }
            val result = MovieExploreState(scope = scope,
                studios = curatedStudioFacets(verified),
                genres = facets(genres, MovieFacetKind.GENRE))
            requireScope(scope)
            Timber.i("Explore coverage: studios=%d studioArtwork=%d genres=%d genreArtwork=%d",
                result.studios.size, result.studios.count { it.image != null },
                result.genres.size, result.genres.count { it.image != null })
            cache.remove(scope)
            cache[scope] = Cached(result, System.nanoTime())
            while (cache.size > 4) cache.remove(cache.keys.first())
            result
        }
    }

    private fun requireScope(scope: MovieExploreScope) {
        if (server.currentServer?.id != scope.server || server.currentUser?.id != scope.user)
            throw CancellationException("Explore account changed")
    }
}

@HiltViewModel
internal class MovieExploreViewModel @Inject constructor(
    private val server: ServerRepository,
    private val repository: MovieExploreRepository,
    private val navigation: NavigationManager,
) : ViewModel() {
    private val _state = MutableStateFlow(MovieExploreState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            server.current.collect { current ->
                val scope = _state.value.scope
                if (scope != null && (current?.server?.id != scope.server || current.user.id != scope.user)) {
                    job?.cancel()
                    _state.value = MovieExploreState()
                }
            }
        }
    }

    fun clearUnavailableLibrary() {
        job?.cancel()
        _state.value = MovieExploreState()
    }

    fun load(library: UUID) {
        val current = server.current.value ?: return
        val scope = MovieExploreScope(current.server.id, current.user.id, library)
        if (_state.value.scope == scope) return
        job?.cancel()
        _state.value = MovieExploreState(scope, loading = true)
        job = viewModelScope.launch {
            try { _state.value = repository.load(scope) }
            catch (ex: CancellationException) { throw ex }
            catch (_: Exception) { _state.value = MovieExploreState(scope, failed = true) }
        }
    }

    fun open(facet: MovieFacet) {
        val state = _state.value
        val scope = state.scope ?: return
        if (server.currentServer?.id == scope.server && server.currentUser?.id == scope.user &&
            (state.studios + state.genres).any { it.key == facet.key })
            navigation.navigateTo(facet.destination(scope))
    }
}
