package com.github.damontecres.wholphin.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.data.model.QuickDetailsData
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.cards.BannerCardWithTitle
import com.github.damontecres.wholphin.ui.cards.GenreCard
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.StudioCard
import com.github.damontecres.wholphin.ui.cards.ViewMoreCard
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.EpisodeName
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.FocusableItemRow
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.components.ViewOptionImageType
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.discovery.MovieExploreViewModel
import com.github.damontecres.wholphin.ui.discovery.MovieFacetCard
import com.github.damontecres.wholphin.ui.discovery.homeDiscoveryRows
import com.github.damontecres.wholphin.ui.nav.JellyLuBrowseTab
import com.github.damontecres.wholphin.ui.nav.jellyLuBrowseLinks
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.playback.scale
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LifecycleStartEffect(Unit) {
        viewModel.init(refresh = viewModel.navigationManager.consumeHomeRefresh())
        onStopOrDispose { }
    }
    val state by viewModel.state.collectAsState()
    val loading = state.loadingState
    val refreshing = state.refreshState
    val homeRows = state.homeRows
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val spotlightViewModel = hiltViewModel<SpotlightViewModel>()
    val spotlightAvailable by spotlightViewModel.available.collectAsStateWithLifecycle(false)
    val spotlightPending by spotlightViewModel.entryPending.collectAsStateWithLifecycle(true)
    val homeResetGeneration by viewModel.navigationManager.homeResetGeneration.collectAsStateWithLifecycle()
    val homeSessionGeneration by viewModel.navigationManager.homeSessionGeneration.collectAsStateWithLifecycle()
    LaunchedEffect(libraryItems, homeSessionGeneration) {
        spotlightViewModel.load(libraryItems, homeSession = homeSessionGeneration)
    }

    val discoveryViewModel = hiltViewModel<MovieExploreViewModel>()
    val discoveryState by discoveryViewModel.state.collectAsStateWithLifecycle()
    val moviesLibrary = remember(libraryItems) {
        jellyLuBrowseLinks(libraryItems).firstOrNull { it.tab == JellyLuBrowseTab.MOVIES }?.library?.itemId
    }
    val homeReady = loading == LoadingState.Success
    val updater = com.github.damontecres.wholphin.ui.setup.LocalLunocoUpdater.current
    DisposableEffect(updater, homeReady) {
        updater?.homeReady = homeReady
        onDispose { updater?.homeReady = false }
    }
    LaunchedEffect(moviesLibrary, homeReady) {
        val library = moviesLibrary
        if (library == null) discoveryViewModel.clearUnavailableLibrary()
        else if (homeReady) {
            // Start metadata as soon as Home is usable, without competing with its initial
            // foreground rows. The scoped repository cache and ViewModel guard deduplicate
            // re-entry; neither viewport changes nor card focus can restart this effect.
            discoveryViewModel.load(library)
        }
    }

    when (val state = loading) {
        is LoadingState.Error -> {
            ErrorMessage(state, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
            var showPlaylistDialog by remember { mutableStateOf<UUID?>(null) }
            var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }

            val playlistState by playlistViewModel.playlistState.collectAsState()
            var position by rememberPosition()

            val onFocusPosition = remember { { it: RowColumn -> position = it } }
            val currentHomeRows by rememberUpdatedState(homeRows)
            val currentAppPreferences by rememberUpdatedState(preferences.appPreferences)
            val currentHomePrefs by rememberUpdatedState(preferences.appPreferences.homePagePreferences)
            val onClickItem =
                remember {
                    { clickedPosition: RowColumn, item: BaseItem ->
                        position = clickedPosition
                        if (currentHomePrefs.clickToPlay &&
                            currentHomeRows.getOrNull(clickedPosition.row)?.isContinueWatchingNextUp == true
                        ) {
                            viewModel.navigationManager.navigateTo(Destination.Playback(item))
                        } else {
                            viewModel.openHomeItem(
                                (currentHomeRows.getOrNull(clickedPosition.row) as? HomeRowLoadingState.Success)?.rowType,
                                item,
                            )
                        }
                    }
                }
            val onLongClickItem =
                remember {
                    { clickedPosition: RowColumn, item: BaseItem ->
                        position = clickedPosition
                        val row =
                            (currentHomeRows.getOrNull(clickedPosition.row) as? HomeRowLoadingState.Success)
                        val canRemoveContinueWatching =
                            row?.rowType is HomeRowConfig.ContinueWatching || row?.rowType is HomeRowConfig.ContinueWatchingCombined
                        val canRemoveNextUp =
                            row?.rowType is HomeRowConfig.NextUp || row?.rowType is HomeRowConfig.ContinueWatchingCombined
                        showContextMenu =
                            ContextMenu.ForBaseItem(
                                fromLongClick = true,
                                item = item,
                                chosenStreams = null,
                                showGoTo = true,
                                showStreamChoices = false,
                                canDelete =
                                    viewModel.canDelete(
                                        item,
                                        currentAppPreferences,
                                    ),
                                canRemoveContinueWatching = canRemoveContinueWatching,
                                canRemoveNextUp = canRemoveNextUp,
                                actions =
                                    ContextMenuActions(
                                        navigateTo = viewModel.navigationManager::navigateTo,
                                        onClickWatch = viewModel::setWatched,
                                        onClickFavorite = viewModel::setFavorite,
                                        onClickAddPlaylist = { itemId ->
                                            playlistViewModel.loadPlaylists()
                                            showPlaylistDialog = itemId
                                        },
                                        onSendMediaInfo = viewModel.serverReportService::sendMediaReportFor,
                                        onDeleteItem = {
                                            viewModel.deleteItem(position, it)
                                        },
                                        onChooseVersion = { _, _ ->
                                            // Not supported on this page
                                        },
                                        onChooseTracks = {
                                            // Not supported on this page
                                        },
                                        onShowOverview = {
                                            overviewDialog = ItemDetailsDialogInfo(it)
                                        },
                                        onClearChosenStreams = {},
                                        onClickRemoveFromNextUp = viewModel::removeFromNextUp,
                                    ),
                            )
                    }
                }
            val onClickPlay =
                remember {
                    { _: RowColumn, item: BaseItem ->
                        viewModel.navigationManager.navigateTo(Destination.Playback(item))
                    }
                }
            val onClickViewMore =
                remember {
                    { _: RowColumn, row: HomeRowLoadingState.Success ->
                        viewModel.navigationManager.navigateTo(
                            Destination.MoreHomeRow(row.title, row.rowType!!, row.items.size),
                        )
                    }
                }

            // The TV trace showed repeated row composition/measure during focus scrolling.
            // Keep a bounded two viewports behind for row reversals and prepare one ahead.
            // This retains nearby rows, not the entire feed; cards and semantics stay intact.
            val homeCacheWindow = remember {
                LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 2f)
            }
            val homeListState = rememberLazyListState(cacheWindow = homeCacheWindow)
            val presentedRows = remember(homeRows, moviesLibrary, discoveryState) {
                homeRows + if (moviesLibrary != null) homeDiscoveryRows(
                    if (discoveryState.scope?.library == moviesLibrary) discoveryState
                    else com.github.damontecres.wholphin.ui.discovery.MovieExploreState()
                ) else emptyList()
            }
            val discoveryFacets = remember(discoveryState) {
                (discoveryState.studios + discoveryState.genres).associateBy { it.id }
            }
            HomePageContent(
                homeRows = presentedRows,
                discoveryLoading = moviesLibrary != null &&
                    (discoveryState.scope?.library != moviesLibrary || discoveryState.loading),
                discoveryCard = { item, cardModifier, onClick ->
                    discoveryFacets[item.id]?.let { MovieFacetCard(it, onClick, cardModifier) }
                },
                resetGeneration = homeResetGeneration,
                listState = homeListState,
                position = RowColumn(-1, -1),
                getPosition = { position },
                onFocusPosition = onFocusPosition,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                onClickPlay = onClickPlay,
                loadingState = refreshing,
                showClock = preferences.appPreferences.interfacePreferences.showClock,
                onUpdateBackdrop = {}, // Home never submits focus-driven full-screen artwork.
                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                showViewMore = true,
                onClickViewMore = onClickViewMore,
                spotlightAvailable = spotlightAvailable,
                spotlightPending = spotlightPending,
                spotlightContent = { focus, rowFocus ->
                    JellyLuSpotlight(spotlightViewModel, focus, rowFocus) { control ->
                        onFocusPosition(RowColumn(-1, control))
                    }
                },
                classicHome = true,
                jellyLuHome = true,
                headerComposable = {},
                modifier = modifier,
            )
            overviewDialog?.let { info ->
                ItemDetailsDialog(
                    info = info,
                    showFilePath = false,
                    onDismissRequest = { overviewDialog = null },
                )
            }
            showContextMenu?.let { contextMenu ->
                ContextMenuDialog(
                    onDismissRequest = { showContextMenu = null },
                    getMediaSource = null,
                    contextMenu = contextMenu,
                    preferredSubtitleLanguage = null,
                )
            }
            showPlaylistDialog?.let { itemId ->
                PlaylistDialog(
                    title = stringResource(R.string.add_to_playlist),
                    state = playlistState,
                    onDismissRequest = { showPlaylistDialog = null },
                    onClick = {
                        playlistViewModel.addToPlaylist(it.id, itemId)
                        showPlaylistDialog = null
                    },
                    createEnabled = true,
                    onCreatePlaylist = {
                        playlistViewModel.createPlaylistAndAddItem(it, itemId)
                        showPlaylistDialog = null
                    },
                    onSearch = playlistViewModel::loadPlaylists,
                    elevation = 3.dp,
                )
            }
        }
    }
}

val HomeRowLoadingState?.isContinueWatchingNextUp: Boolean
    get() =
        (this as? HomeRowLoadingState.Success).let { row ->
            row?.rowType is HomeRowConfig.ContinueWatching ||
                row?.rowType is HomeRowConfig.NextUp ||
                row?.rowType is HomeRowConfig.ContinueWatchingCombined
        }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePageContent(
    homeRows: List<HomeRowLoadingState>,
    position: RowColumn,
    onFocusPosition: (RowColumn) -> Unit,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onClickPlay: (RowColumn, BaseItem) -> Unit,
    showClock: Boolean,
    onUpdateBackdrop: (BaseItem) -> Unit,
    showLogo: Boolean,
    showViewMore: Boolean,
    modifier: Modifier = Modifier,
    loadingState: LoadingState? = null,
    listState: LazyListState = rememberLazyListState(),
    takeFocus: Boolean = true,
    showEmptyRows: Boolean = false,
    headerComposable: @Composable (focusedItem: BaseItem?) -> Unit = { focusedItem ->
        HomePageHeader(
            item = focusedItem,
            showLogo = showLogo,
            modifier = HeaderUtils.modifier,
        )
    },
    onClickViewMore: (RowColumn, HomeRowLoadingState.Success) -> Unit = { _, _ -> },
    classicHome: Boolean = false,
    jellyLuHome: Boolean = false,
    classicLibraries: List<ServerNavDrawerItem> = emptyList(),
    onClickClassicLibrary: (ServerNavDrawerItem) -> Unit = {},
    // Defer rapidly changing focus state reads out of the list composition.
    getPosition: () -> RowColumn = { position },
    spotlightAvailable: Boolean = false,
    spotlightPending: Boolean = false,
    spotlightContent: (@Composable (FocusRequester, FocusRequester?) -> Unit)? = null,
    resetGeneration: Long = 0L,
    discoveryCard: (@Composable (BaseItem, Modifier, () -> Unit) -> Unit)? = null,
    discoveryLoading: Boolean = false,
) {
    val rowKeys = remember(homeRows, jellyLuHome) { homeRowKeys(homeRows, jellyLuHome) }
    val rowFocusRequesters = remember(rowKeys) { List(homeRows.size) { FocusRequester() } }
    val lastRowKey = rememberSaveable { arrayOf<String?>(null) }
    var firstFocused by remember { mutableStateOf(false) }
    val railFocus = LocalJellyLuRailFocus.current
    val spotlightFocus = remember { FocusRequester() }
    val spotlightVisible = remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            layout.visibleItemsInfo.any { it.key == "jellylu_spotlight" &&
                it.offset + it.size > layout.viewportStartOffset && it.offset < layout.viewportEndOffset }
        }
    }
    var spotlightPlaced by remember { mutableStateOf(false) }
    val placedRows = remember(rowKeys) { mutableStateMapOf<Int, Boolean>() }
    val firstMediaRow = remember(homeRows) {
        homeRows.indexOfFirst { it is HomeRowLoadingState.Success && it.items.isNotEmpty() }
    }

    var appliedReset by rememberSaveable { mutableStateOf(0L) }
    var resetFocusPending by remember { mutableStateOf(false) }
    val currentPosition by rememberUpdatedState(getPosition)
    val currentOnFocusPosition by rememberUpdatedState<(RowColumn) -> Unit>({
        // Navigation transitions can transiently focus an outgoing/restored row.
        // An explicit reset still owns its initial focus until the target is attached.
        if (appliedReset == resetGeneration && !resetFocusPending) {
            firstFocused = true
            lastRowKey[0] = rowKeys.getOrNull(it.row)
            onFocusPosition(it)
        }
    })
    val scope = rememberCoroutineScope()
    val spotlightUpJob = remember { arrayOf<Job?>(null) }
    val homeLifecycle = LocalLifecycleOwner.current.lifecycle
    val homeLifecycleState by homeLifecycle.currentStateFlow.collectAsState()
    val navigationHasFocus = LocalJellyLuNavigationHasFocus.current
    val currentSpotlightAvailable by rememberUpdatedState(spotlightAvailable)
    var backToTopPending by remember { mutableStateOf(false) }
    val consumeScrolledHomeBack by remember(listState, homeLifecycle, jellyLuHome) {
        derivedStateOf {
            jellyLuHome && homeLifecycleState == Lifecycle.State.RESUMED &&
                (backToTopPending || listState.isScrollInProgress ||
                    listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0)
        }
    }
    // A single Back on scrolled Home reveals Spotlight and focuses Home. The entry must
    // be resumed so this cannot consume the Back that dismisses a Details destination.
    // At the top the shell's content-to-navigation and normal exit behavior stay intact.
    BackHandler(enabled = consumeScrolledHomeBack) {
        if (!backToTopPending) {
            backToTopPending = true
            spotlightUpJob[0]?.cancel()
            railFocus?.tryRequestFocus()
            scope.launch {
                try {
                    listState.scrollToItem(0)
                    // DOWN from the still-focused Home icon must enter the top content,
                    // not restore a now-offscreen row. Each row retains its own card anchor.
                    currentOnFocusPosition(RowColumn(-1, 0))
                    withFrameNanos { }
                } finally {
                    backToTopPending = false
                }
            }
        }
    }
    val returnUpToSpotlight = remember(listState, spotlightFocus, homeLifecycle, navigationHasFocus) {
        {
            if (spotlightUpJob[0]?.isActive != true) {
                val sourceRow = currentPosition().row
                spotlightUpJob[0] = scope.launch {
                    // Finish the focus transaction before scrolling/recomposing a lazy target.
                    withFrameNanos { }
                    if (currentPosition().row != sourceRow || navigationHasFocus()) return@launch
                    val entryState = homeLifecycle.currentStateFlow.first {
                        it == Lifecycle.State.RESUMED || !it.isAtLeast(Lifecycle.State.STARTED)
                    }
                    if (entryState != Lifecycle.State.RESUMED || navigationHasFocus()) return@launch
                    listState.scrollToItem(0)
                    snapshotFlow { spotlightPlaced || !currentSpotlightAvailable }.first { it }
                    withFrameNanos { }
                    // A different row/reset may have acquired focus during placement.
                    if (currentPosition().row == sourceRow && !resetFocusPending && !navigationHasFocus() &&
                        homeLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        if (currentSpotlightAvailable && spotlightPlaced) {
                            spotlightFocus.requestFocus()
                        } else {
                            railFocus?.requestFocus()
                        }
                    }
                }
            }
            Unit
        }
    }
    val revealSpotlight = remember(listState) {
        {
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                scope.launch { listState.scrollToItem(0) }
            }
            Unit
        }
    }
    LaunchedEffect(resetGeneration) {
        if (jellyLuHome && appliedReset != resetGeneration) {
            spotlightUpJob[0]?.cancel()
            resetFocusPending = true
            firstFocused = false
            lastRowKey[0] = null
            onFocusPosition(RowColumn(-1, 0))
            listState.scrollToItem(0)
            withFrameNanos { }
            appliedReset = resetGeneration
        }
    }
    var entryWaitComplete by remember { mutableStateOf(false) }
    LaunchedEffect(takeFocus, jellyLuHome) {
        // Bound the fresh-entry wait, including missing libraries and failed requests.
        if (jellyLuHome) delay(1500)
        entryWaitComplete = true
    }
    val currentOnClickPlay by rememberUpdatedState(onClickPlay)

    if (takeFocus) {
        LaunchedEffect(homeRows, spotlightAvailable, spotlightPlaced, spotlightPending, entryWaitComplete, appliedReset, resetFocusPending) {
            if (jellyLuHome && appliedReset != resetGeneration) return@LaunchedEffect
            if (jellyLuHome && !firstFocused && !spotlightAvailable &&
                (!spotlightPending || entryWaitComplete) && homeRestoreRow(homeRows, rowKeys, null, 0) == null) {
                firstFocused = railFocus?.tryRequestFocus() == true
                return@LaunchedEffect
            }
            if (!firstFocused && (homeRows.isNotEmpty() || (jellyLuHome && spotlightAvailable))) {
                val position = if (resetFocusPending) RowColumn(-1, 0) else currentPosition()
                if (jellyLuHome && position.row < 0 &&
                    spotlightPending && !spotlightAvailable && !entryWaitComplete) return@LaunchedEffect
                if (jellyLuHome && homeEntryTarget(position.row, spotlightAvailable) == HomeEntryTarget.SPOTLIGHT) {
                    // Lazy children are attached during layout, after the parent's first effect.
                    if (!spotlightPlaced) return@LaunchedEffect
                    withFrameNanos { }
                    firstFocused = spotlightFocus.tryRequestFocus()
                } else if (position.row >= 0) {
                    val index = if (jellyLuHome) homeRestoreRow(homeRows, rowKeys, lastRowKey[0], position.row)
                        else if (rowFocusRequesters.isNotEmpty()) position.row.coerceIn(0, rowFocusRequesters.lastIndex) else null
                    if (index != null) {
                        if (jellyLuHome) {
                            if (listState.layoutInfo.visibleItemsInfo.none { it.key == rowKeys[index] }) {
                                listState.scrollToItem(index + if (spotlightContent != null) 1 else 0)
                            }
                            snapshotFlow { placedRows[index] == true }.first { it }
                            withFrameNanos { }
                            if (firstFocused) return@LaunchedEffect
                        }
                        firstFocused = rowFocusRequesters[index].tryRequestFocus()
                    } else if (jellyLuHome) {
                        firstFocused = if (spotlightAvailable && spotlightPlaced) spotlightFocus.tryRequestFocus()
                            else railFocus?.tryRequestFocus() == true
                    }
                } else {
                    // Waiting for the first home row to load, then focus on it
                    homeRows
                        .indexOfFirstOrNull { it is HomeRowLoadingState.Success && it.items.isNotEmpty() }
                        ?.let {
                            if (jellyLuHome) {
                                if (listState.layoutInfo.visibleItemsInfo.none { item -> item.key == rowKeys[it] }) {
                                    listState.scrollToItem(it + if (spotlightContent != null) 1 else 0)
                                }
                                snapshotFlow { placedRows[it] == true }.first { it }
                                withFrameNanos { }
                                if (firstFocused) return@LaunchedEffect
                            }
                            firstFocused = rowFocusRequesters[it].tryRequestFocus()
                            delay(50)
                            // The classic library strip is a separate LazyColumn item.
                            if (!jellyLuHome) {
                                listState.scrollToItem(it + if (classicHome && classicLibraries.isNotEmpty()) 1 else 0)
                            }
                        }
                }
            }
            if (firstFocused) resetFocusPending = false
        }
    }
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .focusProperties {
                        onEnter = {
                            val target = if (resetFocusPending) RowColumn(-1, 0) else currentPosition()
                            if (jellyLuHome && homeEntryTarget(target.row, spotlightAvailable) == HomeEntryTarget.SPOTLIGHT) {
                                if (spotlightPlaced) spotlightFocus.tryRequestFocus()
                            } else {
                                val row = if (jellyLuHome) {
                                    homeRestoreRow(homeRows, rowKeys, lastRowKey[0], target.row)
                                } else target.row
                                if (row != null && (!jellyLuHome || placedRows[row] == true)) {
                                    rowFocusRequesters.getOrNull(row)?.tryRequestFocus()
                                }
                            }
                        }
                    }.fillMaxSize(),
        ) {
            if (!jellyLuHome) {
                FocusedHomeHeader(
                    homeRows = homeRows,
                    getPosition = currentPosition,
                    onUpdateBackdrop = onUpdateBackdrop,
                    content = headerComposable,
                )
            }

            val density = LocalDensity.current
            val spaceAbovePx =
                remember(density, jellyLuHome) {
                    with(density) {
                        (if (jellyLuHome) 32.dp else 50.dp).toPx()
                    }
                }
            val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
            CompositionLocalProvider(
                LocalBringIntoViewSpec provides
                    if (jellyLuHome) remember(density) {
                        JellyLuHomeBringIntoViewSpec(spaceAbovePx)
                    } else ScrollToTopBringIntoViewSpec(spaceAbovePx),
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(if (jellyLuHome) 10.dp else 0.dp),
                    contentPadding =
                        PaddingValues(
                            bottom = Cards.height2x3,
                        ),
                    modifier =
                        if (jellyLuHome) Modifier else Modifier.focusRestorer(),
                ) {
                    if (jellyLuHome && spotlightContent != null) {
                        item(key = "jellylu_spotlight", contentType = "spotlight") {
                            androidx.compose.runtime.DisposableEffect(Unit) {
                                onDispose { spotlightPlaced = false }
                            }
                            Box(Modifier.onPlaced { spotlightPlaced = true }) {
                                CompositionLocalProvider(LocalSpotlightReveal provides revealSpotlight,
                                    LocalHomeResetGeneration provides resetGeneration,
                                    LocalSpotlightVisibility provides spotlightVisible) {
                                    spotlightContent(spotlightFocus, rowFocusRequesters.getOrNull(firstMediaRow))
                                }
                            }
                        }
                    }
                    if (classicHome && !jellyLuHome) {
                        homeLibraryRow(classicLibraries, onClickClassicLibrary)
                    }
                    itemsIndexed(
                        homeRows,
                        // Keep row slots stable when the separate library strip arrives or disappears.
                        key = { index, _ -> rowKeys[index] },
                        contentType = { _, row ->
                            when (row) {
                                is HomeRowLoadingState.Success -> "media_row"
                                is HomeRowLoadingState.Error -> "error_row"
                                else -> "loading_row"
                            }
                        },
                    ) { rowIndex, row ->
                        androidx.compose.runtime.DisposableEffect(rowKeys[rowIndex]) {
                            onDispose { placedRows.remove(rowIndex) }
                        }
                        val rowModifier =
                            (if (jellyLuHome) Modifier else Modifier.animateItem(placementSpec = null))
                                .padding(bottom = if (jellyLuHome) 0.dp else 8.dp)
                                .onPlaced {
                                    if ((!jellyLuHome || row !is HomeRowLoadingState.Success) && placedRows[rowIndex] != true) {
                                        placedRows[rowIndex] = true
                                    }
                                }
                        CompositionLocalProvider(
                            LocalBringIntoViewSpec provides defaultBringIntoViewSpec,
                        ) {
                            when (val r = row) {
                                is HomeRowLoadingState.Loading,
                                is HomeRowLoadingState.Pending,
                                -> {
                                    FocusableItemRow(
                                        title = r.title.getString(),
                                        subtitle = stringResource(R.string.loading),
                                        modifier = rowModifier,
                                    )
                                }

                                is HomeRowLoadingState.Error -> {
                                    FocusableItemRow(
                                        title = r.title.getString(),
                                        subtitle = r.localizedMessage,
                                        isError = true,
                                        modifier = rowModifier,
                                    )
                                }

                                is HomeRowLoadingState.Success -> {
                                    // Reserve only the two existing discovery footprints while metadata
                                    // is pending: real headings, no fake cards, artwork requests or focus
                                    // targets. The same height applies after arrival, avoiding a row jump.
                                    val discoveryRowHeight = if (discoveryCard != null) when (r.presentationKey) {
                                        "lunoco_discovery:studios" -> 128.dp // 120dp card + 4dp above/below
                                        "lunoco_discovery:genres" -> 118.dp // 110dp card + 4dp above/below
                                        else -> 0.dp
                                    } else 0.dp
                                    if (row.items.isNotEmpty() || (discoveryLoading && discoveryRowHeight > 0.dp)) {
                                        val viewOptions =
                                            remember(jellyLuHome, classicHome, row.rowType, row.viewOptions) {
                                                if (jellyLuHome) {
                                                    compactHomeOptions(row.rowType, row.viewOptions)
                                                } else if (classicHome && row.isContinueWatchingNextUp) {
                                                    row.viewOptions.copy(
                                                        heightDp = 124,
                                                        aspectRatio = AspectRatio.WIDE,
                                                        episodeAspectRatio = AspectRatio.WIDE,
                                                        imageType = ViewOptionImageType.THUMB,
                                                        episodeImageType = ViewOptionImageType.THUMB,
                                                        showTitles = true,
                                                    )
                                                } else {
                                                    row.viewOptions
                                                }
                                            }
                                        val itemKeys = remember(row.items) { homeItemKeys(row.items) }
                                        ItemRow(
                                            title = row.title.getString(),
                                            resetGeneration = if (jellyLuHome) resetGeneration else null,
                                            onFocusReady = if (jellyLuHome) {
                                                { placedRows[rowIndex] = true }
                                            } else null,
                                            items = row.items,
                                            itemKey = { index, _ -> itemKeys[index] },
                                            itemContentType = { _, item ->
                                                when (item?.type) {
                                                    BaseItemKind.GENRE -> "genre"
                                                    BaseItemKind.STUDIO -> "studio"
                                                    else -> "media"
                                                }
                                            },
                                            onClickItem =
                                                remember(rowIndex, onClickItem) {
                                                    { index, item ->
                                                        onClickItem.invoke(
                                                            RowColumn(
                                                                rowIndex,
                                                                index,
                                                            ),
                                                            item,
                                                        )
                                                    }
                                                },
                                            onLongClickItem =
                                                remember(rowIndex, onLongClickItem) {
                                                    { index, item ->
                                                        onLongClickItem.invoke(
                                                            RowColumn(rowIndex, index),
                                                            item,
                                                        )
                                                    }
                                                },
                                            modifier =
                                                rowModifier
                                                    .then(if (discoveryRowHeight > 0.dp) Modifier.testTag(r.presentationKey.orEmpty()) else Modifier)
                                                    .fillMaxWidth()
                                                    .focusProperties {
                                                        // Only the boundary above the first media row:
                                                        // never direct UP at an uncomposed Spotlight.
                                                        if (jellyLuHome && rowIndex == firstMediaRow && spotlightAvailable) {
                                                            onExit = {
                                                                if (requestedFocusDirection == FocusDirection.Up) {
                                                                    cancelFocusChange()
                                                                    returnUpToSpotlight()
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .focusGroup()
                                                    .focusRequester(rowFocusRequesters[rowIndex]),
                                            horizontalPadding = viewOptions.spacing.dp,
                                            minimumRowHeight = discoveryRowHeight,
                                            cardContent = { index, item, cardModifier, onClick, onLongClick ->
                                                val onFocus =
                                                    remember(rowIndex, index) {
                                                        { isFocused: Boolean ->
                                                            if (isFocused) {
                                                                currentOnFocusPosition(
                                                                    RowColumn(
                                                                        rowIndex,
                                                                        index,
                                                                    ),
                                                                )
                                                            }
                                                        }
                                                    }
                                                val onKey =
                                                    remember(item) {
                                                        { event: KeyEvent ->
                                                            if (isPlayKeyUp(event) && item?.type?.playable == true) {
                                                                Timber.v("Clicked play on ${item.id}")
                                                                currentOnClickPlay(
                                                                    currentPosition(),
                                                                    item,
                                                                )
                                                                true
                                                            } else {
                                                                false
                                                            }
                                                        }
                                                    }
                                                if (row.presentationKey?.startsWith("lunoco_discovery:") == true &&
                                                    item != null && discoveryCard != null) {
                                                    discoveryCard(item, cardModifier.onFocusChanged { onFocus(it.isFocused) }, onClick)
                                                } else HomePageCardContent(
                                                    index = index,
                                                    item = item,
                                                    onClick = onClick,
                                                    onLongClick = onLongClick,
                                                    viewOptions = viewOptions,
                                                    compactResume = jellyLuHome && homeResumeRow(row.rowType),
                                                    modifier =
                                                        cardModifier
                                                            .onFocusChanged { onFocus(it.isFocused) }
                                                            .focusProperties {
                                                                if (jellyLuHome && rowIndex == firstMediaRow && !spotlightAvailable) {
                                                                    railFocus?.let { up = it }
                                                                }
                                                            }
                                                            .onKeyEvent { onKey(it) },
                                                )
                                            },
                                            showViewMore = showViewMore && row.showViewMore,
                                            viewMoreCardContent = { mod ->
                                                HomePageViewMoreCard(
                                                    isEpisode = row.items.last()?.type == BaseItemKind.EPISODE,
                                                    onClick = {
                                                        onClickViewMore.invoke(
                                                            RowColumn(
                                                                rowIndex,
                                                                r.items.size,
                                                            ),
                                                            r,
                                                        )
                                                    },
                                                    onLongClick = {},
                                                    viewOptions = viewOptions,
                                                    modifier =
                                                        mod.onFocusChanged {
                                                            if (it.isFocused) {
                                                                currentOnFocusPosition.invoke(
                                                                    RowColumn(
                                                                        rowIndex,
                                                                        r.items.size,
                                                                    ),
                                                                )
                                                            }
                                                        },
                                                )
                                            },
                                        )
                                    } else if (showEmptyRows) {
                                        FocusableItemRow(
                                            title = r.title.getString(),
                                            subtitle = stringResource(R.string.no_results),
                                            modifier = rowModifier,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // JellyLu formerly appended a SECOND automatic library strip here.
                    // Configured homeRows above remain untouched; direct/overflow libraries stay available.
                }
            }
        }
        when (loadingState) {
            LoadingState.Pending,
            LoadingState.Loading,
            -> {
                Box(
                    modifier =
                        Modifier
                            .padding(if (showClock) 40.dp else 20.dp)
                            .size(40.dp)
                            .align(Alignment.TopEnd),
                ) {
                    CircularProgress(Modifier.fillMaxSize())
                }
            }

            else -> {}
        }
    }
}

// Focus updates invalidate this small header scope, not the LazyColumn and its rows.
@Composable
private fun FocusedHomeHeader(
    homeRows: List<HomeRowLoadingState>,
    getPosition: () -> RowColumn,
    onUpdateBackdrop: (BaseItem) -> Unit,
    content: @Composable (BaseItem?) -> Unit,
) {
    val position = getPosition()
    val focusedItem =
        (homeRows.getOrNull(position.row) as? HomeRowLoadingState.Success)
            ?.items?.getOrNull(position.column)
    LaunchedEffect(onUpdateBackdrop, focusedItem) {
        focusedItem?.let(onUpdateBackdrop)
    }
    content(focusedItem)
}

private fun LazyListScope.homeLibraryRow(
    classicLibraries: List<ServerNavDrawerItem>,
    onClickClassicLibrary: (ServerNavDrawerItem) -> Unit,
) {
    if (classicLibraries.isNotEmpty()) {
        item(key = "classic_my_media", contentType = "library_row") {
            ItemRow<ServerNavDrawerItem>(
                title = stringResource(R.string.jellylu_my_libraries),
                items = classicLibraries,
                itemKey = { index, library -> library?.itemId?.toString() ?: "library_placeholder_$index" },
                itemContentType = { _, _ -> "library" },
                onClickItem = { _, library -> onClickClassicLibrary(library) },
                onLongClickItem = { _, library -> onClickClassicLibrary(library) },
                cardContent = { index, library, cardModifier, onClick, onLongClick ->
                    library?.let {
                        ClassicLibraryCard(
                            library = it,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = cardModifier,
                        )
                    }
                },
                horizontalPadding = 16.dp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }
    }

}

internal fun homeRowKeys(rows: List<HomeRowLoadingState>, enabled: Boolean): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return rows.mapIndexed { index, row ->
        if (enabled) (row as? HomeRowLoadingState.Success)?.presentationKey?.let { return@mapIndexed it }
        val config = (row as? HomeRowLoadingState.Success)?.rowType
        val identity = if (enabled && config != null) {
            config.updateViewOptions(HomeRowViewOptions()).toString()
        } else return@mapIndexed "home_row_$index"
        val count = occurrences.getOrDefault(identity, 0)
        occurrences[identity] = count + 1
        "home_config:$identity:$count"
    }
}

internal fun homeRestoreRow(rows: List<HomeRowLoadingState>, keys: List<String>, key: String?, index: Int): Int? {
    val candidates = rows.indices.filter {
        val row = rows[it]
        row is HomeRowLoadingState.Success && row.items.any { item -> item != null }
    }
    return key?.let { keys.indexOf(it).takeIf { found -> found in candidates } }
        ?: candidates.minByOrNull { kotlin.math.abs(it.toLong() - index.toLong()) }
}

// Occurrence suffixes also support custom rows containing repeated IDs; null slots stay unique.
internal fun homeItemKeys(items: List<BaseItem?>): List<String> {
    val occurrences = mutableMapOf<UUID, Int>()
    return items.mapIndexed { index, item ->
        if (item == null) {
            "placeholder:$index"
        } else {
            val occurrence = occurrences.getOrDefault(item.id, 0)
            occurrences[item.id] = occurrence + 1
            "item:${item.id}:$occurrence"
        }
    }
}

@Composable
fun HomePageHeader(
    item: BaseItem?,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    val isEpisode = item?.type == BaseItemKind.EPISODE
    val title =
        remember(item?.id) {
            when (item?.type) {
                BaseItemKind.EPISODE,
                BaseItemKind.SEASON,
                -> item.data.seriesName

                else -> item?.title
            }
        }
    val subtitle =
        remember(item?.id) {
            when (item?.type) {
                BaseItemKind.SEASON,
                BaseItemKind.EPISODE,
                -> item.name

                else -> null
            }
        }
    HomePageHeader(
        title = title,
        subtitle = subtitle,
        overview = item?.data?.overview,
        overviewTwoLines = isEpisode,
        quickDetails = item?.ui?.quickDetails,
        timeRemaining = item?.timeRemainingOrRuntime,
        endsAt = item?.data?.endDate,
        showLogo = showLogo,
        logoImageUrl = rememberLogoUrl(item),
        modifier = modifier,
    )
}

@Composable
fun HomePageHeader(
    title: String?,
    subtitle: String?,
    overview: String?,
    overviewTwoLines: Boolean,
    quickDetails: QuickDetailsData?,
    timeRemaining: Duration?,
    endsAt: DateTime?,
    showLogo: Boolean,
    logoImageUrl: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        TitleOrLogo(
            title = title,
            logoImageUrl = logoImageUrl,
            showLogo = showLogo,
            modifier = Modifier.fillMaxWidth(.75f),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth(.6f),
        ) {
            if (subtitle != null) {
                EpisodeName(subtitle)
            }
            QuickDetails(quickDetails, timeRemaining, endsAt = endsAt)
            val overviewModifier =
                Modifier
                    .padding(0.dp)
                    .height(48.dp + if (!overviewTwoLines) 12.dp else 0.dp)
                    .width(400.dp)
            if (overview.isNotNullOrBlank()) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (overviewTwoLines) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = overviewModifier,
                )
            } else {
                Spacer(overviewModifier)
            }
        }
    }
}

@Composable
fun HomePageCardContent(
    index: Int,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    viewOptions: HomeRowViewOptions,
    modifier: Modifier,
    compactResume: Boolean = false,
) {
    when (item?.type) {
        BaseItemKind.GENRE -> {
            GenreCard(
                genreId = item.id,
                name = item.name,
                imageUrl = item.imageUrlOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier.height(viewOptions.heightDp.dp),
            )
        }

        BaseItemKind.STUDIO -> {
            StudioCard(
                studioId = item.id,
                name = item.name,
                imageUrl = item.imageUrlOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier.height(viewOptions.heightDp.dp),
            )
        }

        else -> {
            val imageType =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeImageType.imageType
                    } else {
                        viewOptions.imageType.imageType
                    }
                }
            val ratio =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeAspectRatio.ratio
                    } else {
                        viewOptions.aspectRatio.ratio
                    }
                }
            val scale =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeContentScale.scale
                    } else {
                        viewOptions.contentScale.scale
                    }
                }
            if (compactResume && viewOptions.showTitles && item != null) {
                CompactHomeResumeCard(
                    item = item,
                    cardHeight = viewOptions.heightDp.dp,
                    aspectRatio = ratio,
                    imageType = imageType,
                    contentScale = scale,
                    useSeries = viewOptions.useSeries,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier = modifier,
                )
            } else if (viewOptions.showTitles) {
                BannerCardWithTitle(
                    title = item?.title,
                    subtitle = item?.subtitle,
                    item = item,
                    aspectRatio = ratio,
                    imageType = imageType,
                    imageContentScale = scale,
                    cornerText = item?.ui?.episodeUnplayedCornerText,
                    played = item?.data?.userData?.played ?: false,
                    favorite = item?.favorite ?: false,
                    playPercent =
                        item?.data?.userData?.playedPercentage
                            ?: 0.0,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier = modifier,
                    cardHeight = viewOptions.heightDp.dp,
                    useSeriesForPrimary = viewOptions.useSeries,
                )
            } else {
                BannerCard(
                    name = item?.data?.seriesName ?: item?.name,
                    item = item,
                    aspectRatio = ratio,
                    imageType = imageType,
                    imageContentScale = scale,
                    cornerText = item?.ui?.episodeUnplayedCornerText,
                    played = item?.data?.userData?.played ?: false,
                    favorite = item?.favorite ?: false,
                    playPercent =
                        item?.data?.userData?.playedPercentage
                            ?: 0.0,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier = modifier,
                    interactionSource = null,
                    cardHeight = viewOptions.heightDp.dp,
                    useSeriesForPrimary = viewOptions.useSeries,
                )
            }
        }
    }
}

@Composable
fun HomePageViewMoreCard(
    isEpisode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    viewOptions: HomeRowViewOptions,
    modifier: Modifier,
) {
    val aspectRatio =
        remember(isEpisode, viewOptions) {
            if (isEpisode) {
                viewOptions.episodeAspectRatio
            } else {
                viewOptions.aspectRatio
            }
        }
    ViewMoreCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        aspectRatio = aspectRatio,
        size = DpSize(height = viewOptions.heightDp.dp, width = Dp.Unspecified),
        showTitle = viewOptions.showTitles,
    )
}
