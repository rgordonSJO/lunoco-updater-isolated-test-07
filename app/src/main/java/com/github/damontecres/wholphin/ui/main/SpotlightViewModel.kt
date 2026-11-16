package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.nav.JellyLuBrowseTab
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.ui.nav.jellyLuBrowseLinks
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.PlayAccess
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

internal data class SpotlightState(
    val scope: SpotlightScope? = null,
    val loading: Boolean = false,
    val movies: List<BaseItemDto> = emptyList(),
    val resolved: Boolean = scope != null,
    val selectionDay: Long? = null,
)

internal fun spotlightCandidateRequests(scope: SpotlightScope): List<GetItemsRequest> =
    listOf(ItemSortBy.CRITIC_RATING to 50, ItemSortBy.COMMUNITY_RATING to 50, ItemSortBy.DATE_CREATED to 20)
        .map { (sort, limit) ->
            GetItemsRequest(
                userId = scope.userId, parentId = scope.libraryId,
                includeItemTypes = listOf(BaseItemKind.MOVIE), recursive = true,
                isMissing = false, isPlaceHolder = false,
                excludeLocationTypes = listOf(LocationType.VIRTUAL),
                sortBy = listOf(sort, ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
                limit = limit, enableTotalRecordCount = false,
                enableUserData = true, enableImages = true, imageTypeLimit = 1,
                fields = listOf(ItemFields.PLAY_ACCESS, ItemFields.OVERVIEW, ItemFields.MEDIA_SOURCE_COUNT,
                    ItemFields.SORT_NAME, ItemFields.DATE_CREATED, ItemFields.GENRES),
            )
        }

/** One bounded cached pool per account/library/day. Never called from focus or pager callbacks. */
@Singleton
internal class SpotlightRepository @Inject constructor(private val server: ServerRepository) {
    private val mutex = Mutex()
    private var cacheKey: Pair<SpotlightScope, Long>? = null
    private var cached = emptyList<BaseItemDto>()

    suspend fun load(scope: SpotlightScope, day: Long): List<BaseItemDto> =
        withContext(WholphinDispatchers.IO) {
            mutex.withLock {
                requireScope(scope)
                if (cacheKey == (scope to day)) return@withLock cached
                val semaphore = Semaphore(2)
                val plans = spotlightCandidateRequests(scope)
                val successes = java.util.concurrent.atomic.AtomicInteger()
                val batches = coroutineScope {
                    plans.map { request -> async {
                        semaphore.withPermit {
                            requireScope(scope)
                            try {
                                server.apiClient.itemsApi.getItems(request).content.items.also { successes.incrementAndGet() }
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (_: Exception) {
                                // No exception URLs or credentials in logs; other bounded streams may work.
                                Timber.w("Spotlight candidate stream unavailable")
                                emptyList()
                            }
                        }
                    } }.awaitAll()
                }
                requireScope(scope)
                val candidates = batches.flatten().distinctBy { it.id }
                // Sanitized, read-only metadata coverage. Generic fields have no provider/count attribution.
                val rawCritics = candidates.mapNotNull { it.criticRating }
                val rawCommunity = candidates.mapNotNull { it.communityRating }
                val critics = candidates.mapNotNull { spotlightRatings(it).critic }
                val community = candidates.mapNotNull { spotlightRatings(it).community }
                Timber.i("Spotlight audit: movies=%d critic=%d range=%s community=%d range=%s counts=unavailable provenance=unknown",
                    candidates.size, critics.size,
                    if (critics.isEmpty()) "none" else "${critics.min()}..${critics.max()}",
                    community.size, if (community.isEmpty()) "none" else "${community.min()}..${community.max()}")
                Timber.i("Spotlight rejected ratings: critic=%d community=%d",
                    rawCritics.size - critics.size, rawCommunity.size - community.size)
                val scoped = candidates.map { SpotlightCandidate(scope, it) }
                val audit = spotlightPoolAudit(scoped, scope)
                val collections = spotlightCollections(scope,
                    scoped.filter { spotlightEligible(it, scope) }.map { it.movie.id }.toSet()) { request ->
                    requireScope(scope)
                    server.apiClient.itemsApi.getItems(request).content.items
                }
                requireScope(scope)
                cached = selectSpotlightMovies(scoped, scope, day, collections = collections)
                Timber.i("Spotlight pool: returned=%d unique=%d accepted=%d selected=%d rejected=%s missingBackdrop=%d omittedSourceCount=%d streams=%d/%d",
                    batches.sumOf { it.size }, audit.fetched, audit.accepted, cached.size,
                    audit.rejections.toSortedMap(compareBy { it.ordinal }).toString(),
                    audit.missingBackdrop, audit.omittedSourceCount, successes.get(), plans.size)
                cacheKey = if (successes.get() > 0) scope to day else null
                cached
            }
        }

    private fun requireScope(scope: SpotlightScope) {
        if (server.currentServer?.id != scope.serverId || server.currentUser?.id != scope.userId)
            throw CancellationException("Spotlight account changed")
    }
}

@HiltViewModel
internal class SpotlightViewModel @Inject constructor(
    private val server: ServerRepository,
    private val repository: SpotlightRepository,
    private val navigation: NavigationManager,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(SpotlightState())
    val state = _state.asStateFlow()
    val available = state.map { it.movies.isNotEmpty() }.distinctUntilChanged()
    val entryPending = state.map { it.loading || !it.resolved }.distinctUntilChanged()
    private var requestedScope: SpotlightScope? = null
    private var requestedDay: Long? = null
    private var requestedSession: Long? = null
    private var loadingJob: Job? = null
    private var playJob: Job? = null

    init {
        viewModelScope.launch {
            server.current.collect { current ->
                val scope = requestedScope
                if (scope != null && (current?.server?.id != scope.serverId || current.user.id != scope.userId)) {
                    loadingJob?.cancel()
                    playJob?.cancel()
                    requestedScope = null
                    requestedDay = null
                    requestedSession = null
                    _state.value = SpotlightState()
                }
            }
        }
    }

    fun load(libraries: List<ServerNavDrawerItem>, homeSession: Long? = null,
        epochDay: Long = LocalDate.now().toEpochDay()) {
        val current = server.current.value ?: return
        val library = jellyLuBrowseLinks(libraries).firstOrNull { it.tab == JellyLuBrowseTab.MOVIES }?.library
        if (library == null) {
            Timber.i("Spotlight scope: libraries=%d primaryMovies=false", libraries.size)
            loadingJob?.cancel()
            requestedScope = null
            requestedDay = null
            requestedSession = null
            _state.value = SpotlightState(resolved = libraries.isNotEmpty())
            return
        }
        val scope = SpotlightScope(current.server.id, current.user.id, library.itemId)
        val freshSession = homeSession != null && homeSession != requestedSession
        val day = if (requestedScope != scope || freshSession) epochDay else requestedDay ?: epochDay
        if (homeSession != null) requestedSession = homeSession
        if (requestedScope == scope && requestedDay == day) return
        Timber.i("Spotlight scope: libraries=%d primaryMovies=true", libraries.size)
        requestedScope = scope
        requestedDay = day
        loadingJob?.cancel()
        _state.value = SpotlightState(scope = scope, loading = true, selectionDay = day)
        loadingJob = viewModelScope.launch {
            try {
                val movies = repository.load(scope, day)
                if (requestedScope == scope && requestedDay == day)
                    _state.value = SpotlightState(scope, movies = movies, selectionDay = day)
            } catch (ex: CancellationException) {
                throw ex
            } catch (_: Exception) {
                if (requestedScope == scope && requestedDay == day)
                    _state.value = SpotlightState(scope, selectionDay = day)
            }
        }
    }

    fun details(movie: BaseItemDto) {
        if (_state.value.movies.any { it.id == movie.id } && accountMatches())
            navigation.run {
                preserveSpotlightHomeReturn()
                navigateTo(spotlightDetails(movie))
            }
    }

    fun play(movie: BaseItemDto) {
        if (playJob?.isActive == true || !accountMatches() || _state.value.movies.none { it.id == movie.id }) return
        val scope = _state.value.scope ?: return
        playJob = viewModelScope.launch {
            try {
                // Refresh resume/permission only on confirmed Play, never on focus or manual paging.
                val fresh = withContext(WholphinDispatchers.IO) {
                    server.apiClient.userLibraryApi.getItem(movie.id).content
                }
                if (!accountMatches() || _state.value.scope != scope) return@launch
                if (fresh.type != BaseItemKind.MOVIE || fresh.isPlaceHolder == true ||
                    fresh.playAccess == PlayAccess.NONE || fresh.mediaSourceCount == 0) {
                    showToast(context, context.getString(R.string.jellylu_spotlight_unavailable))
                    return@launch
                }
                _state.value = _state.value.copy(movies = _state.value.movies.map {
                    if (it.id == fresh.id) fresh else it
                })
                navigation.navigateTo(spotlightPlayback(fresh))
            } catch (ex: CancellationException) {
                throw ex
            } catch (_: Exception) {
                showToast(context, context.getString(R.string.jellylu_spotlight_unavailable))
            }
        }
    }

    private fun accountMatches(): Boolean = _state.value.scope?.let {
        server.currentServer?.id == it.serverId && server.currentUser?.id == it.userId
    } == true
}
