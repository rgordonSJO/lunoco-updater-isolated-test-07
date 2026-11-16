package com.github.damontecres.wholphin.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.tryRequestFocus
import java.util.UUID
import kotlinx.coroutines.flow.first

/** The existing library grid remains the initial view. No discovery work until explicit Explore. */
@Composable
internal fun LunocoMoviesWorkspace(
    library: UUID, modifier: Modifier = Modifier,
    allMovies: @Composable (Modifier, (Int, Int) -> Unit) -> Unit,
) {
    val allMoviesFocus = remember(library) { FocusRequester() }
    var explore by rememberSaveable(library) { mutableStateOf(false) }
    var gridAtTop by rememberSaveable(library) { mutableStateOf(true) }
    val onGridPosition = remember(library) { { columns: Int, position: Int -> gridAtTop = position < columns } }
    Column(modifier) {
        // Follow the existing collapsing grid header. Retaining another fixed header while deep
        // browsing changes the lazy grid's available focus-search viewport.
        if (explore || gridAtTop) Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MovieModeButton(stringResource(R.string.lunoco_all_movies), !explore, "movies-all", Modifier.focusRequester(allMoviesFocus)) { explore = false }
            MovieModeButton(stringResource(R.string.lunoco_explore), explore, "movies-explore") { explore = true }
        }
        if (!explore) allMovies(Modifier.weight(1f).fillMaxWidth(), onGridPosition)
        else MovieExploreRoute(library, Modifier.weight(1f).fillMaxWidth(), allMoviesFocus)
    }
}

@Composable
private fun MovieModeButton(label: String, active: Boolean, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick, modifier = modifier.height(34.dp).testTag(tag).semantics { selected = active },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(containerColor = if (active) Color(0xFF142132) else Color.Transparent,
            contentColor = if (active) Color(0xFF70B8FF) else Color(0xFFEAF0F6))) {
        Text(label, fontSize = 15.sp)
    }
}

@Composable
private fun MovieExploreRoute(library: UUID, modifier: Modifier, fallbackFocus: FocusRequester, viewModel: MovieExploreViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(library) { viewModel.load(library) }
    MovieExploreContent(state, viewModel::open, modifier, fallbackFocus)
}

/** Saveable list states and stable facet identities survive filtered-grid and Details navigation. */
@Composable
internal fun MovieExploreContent(state: MovieExploreState, onOpen: (MovieFacet) -> Unit, modifier: Modifier = Modifier, fallbackFocus: FocusRequester? = null) {
    key(state.scope) { MovieExploreScopedContent(state, onOpen, modifier, fallbackFocus) }
}

@Composable
private fun MovieExploreScopedContent(state: MovieExploreState, onOpen: (MovieFacet) -> Unit, modifier: Modifier, fallbackFocus: FocusRequester?) {
    val column = rememberLazyListState()
    val studioRow = rememberLazyListState()
    val genreRow = rememberLazyListState()
    var returnKey by rememberSaveable(state.scope?.toString()) { mutableStateOf<String?>(null) }
    var returnIndex by rememberSaveable(state.scope?.toString()) { mutableIntStateOf(0) }
    // A trip through the top bar to Settings does not activate a facet. Preserve the
    // last actual content focus independently of the filtered-grid activation anchor.
    var focusedKey by rememberSaveable(state.scope?.toString()) { mutableStateOf<String?>(null) }
    var focusedIndex by rememberSaveable(state.scope?.toString()) { mutableIntStateOf(0) }
    val requester = remember { FocusRequester() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sections = listOf(
        Triple(R.string.lunoco_browse_studio, state.studios, studioRow),
        Triple(R.string.lunoco_browse_genre, state.genres, genreRow),
    ).filter { it.second.isNotEmpty() }
    val currentSections by rememberUpdatedState(sections)
    var entryRestorationComplete by remember { mutableStateOf(false) }
    // Restore only on destination resume (or completion of its initial loading), never
    // on ordinary focus movement. Lifecycle cancellation prevents an outgoing screen
    // from winning a focus race with Settings. No metadata query is performed here.
    LaunchedEffect(state.scope, state.loading, lifecycle) {
        if (state.loading) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (entryRestorationComplete) return@repeatOnLifecycle
            val key = returnKey ?: focusedKey
            if (key == null) {
                entryRestorationComplete = true
                return@repeatOnLifecycle
            }
            val available = currentSections
            val section = available.indexOfFirst { it.second.firstOrNull()?.kind?.name == key.substringBefore(':') }
                .takeIf { it >= 0 } ?: available.indices.firstOrNull()
            if (section == null) {
                returnKey = null
                focusedKey = null
                withFrameNanos { }
                fallbackFocus?.tryRequestFocus()
                entryRestorationComplete = true
                return@repeatOnLifecycle
            }
            val (_, facets, row) = available[section]
            val index = restoredFacetIndex(facets, key, if (returnKey != null) returnIndex else focusedIndex)
            // Attach the requester to the resolved identity before restoring a lazy target.
            returnKey = facets[index].key
            returnIndex = index
            if (column.layoutInfo.visibleItemsInfo.none { it.index == section }) column.scrollToItem(section)
            if (row.layoutInfo.visibleItemsInfo.none { it.index == index }) row.scrollToItem(index)
            snapshotFlow { row.layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it }
            withFrameNanos { }
            requester.requestFocus()
            returnKey = null
            entryRestorationComplete = true
        }
    }
    when {
        state.loading -> Text(stringResource(R.string.lunoco_explore_loading), Modifier.padding(16.dp), color = Color(0xFFEAF0F6))
        sections.isEmpty() -> Text(stringResource(if (state.failed) R.string.lunoco_explore_unavailable else R.string.lunoco_explore_empty),
            Modifier.padding(16.dp), color = Color(0xFFEAF0F6))
        else -> LazyColumn(modifier.focusGroup().testTag("movie-explore"), state = column,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(sections, key = { _, section -> section.first }) { _, section ->
                val (title, facets, row) = section
                Column {
                    Text(stringResource(title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEAF0F6), modifier = Modifier.padding(bottom = 10.dp))
                    LazyRow(state = row, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(4.dp),
                        modifier = Modifier.testTag(if (title == R.string.lunoco_browse_studio) "explore-studios" else "explore-genres")) {
                        itemsIndexed(facets, key = { _, facet -> facet.key }) { index, facet ->
                            val restoring = returnKey?.substringBefore(':') == facet.kind.name &&
                                restoredFacetIndex(facets, returnKey, returnIndex) == index
                            MovieFacetCard(facet, {
                                returnIndex = index
                                returnKey = facet.key
                                onOpen(facet)
                            }, Modifier
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        focusedKey = facet.key
                                        focusedIndex = index
                                    }
                                }
                                .then(if (restoring) Modifier.focusRequester(requester) else Modifier))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MovieFacetCard(facet: MovieFacet, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val studio = facet.kind == MovieFacetKind.STUDIO
    val width = if (studio) 224.dp else 196.dp
    val height = if (studio) 120.dp else 110.dp
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val service = LocalImageUrlService.current
    val context = LocalContext.current
    val image = facet.image
    val url = remember(image, service) {
        image?.let { service.getItemImageUrl(it.item, it.type, maxWidth = 448, maxHeight = 240, tag = it.tag) }
    }
    var imageFailed by remember(url) { mutableStateOf(false) }
    val request = remember(url, context) { url?.let { ImageRequest.Builder(context).data(it).size(448, 240).build() } }
    Surface(onClick = onClick, modifier = modifier.size(width, height)
        .testTag("explore-${facet.key}").onFocusChanged { focused = it.isFocused }
        .border(if (focused) 2.dp else 0.dp, if (focused) Color(0xFF85C3FF) else Color.Transparent, shape),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF142132),
            focusedContainerColor = Color(0xFF142132), contentColor = Color(0xFFEAF0F6),
            focusedContentColor = Color(0xFFEAF0F6))) {
        Box(Modifier.fillMaxSize().clip(shape)) {
            if (studio) {
                if (request != null && !imageFailed) {
                    // Separate measured slots: a wrapping label can never draw over the logo.
                    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(request, contentDescription = null, contentScale = ContentScale.Fit,
                            onError = { imageFailed = true },
                            modifier = Modifier.weight(1f).fillMaxWidth().testTag("studio-logo"))
                        Spacer(Modifier.height(6.dp))
                        Text(facet.name, color = Color(0xFFEAF0F6), fontSize = 16.sp,
                            lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().testTag("studio-label"))
                    }
                } else {
                    Text(facet.name, color = Color(0xFFEAF0F6), fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(14.dp).testTag("studio-label"))
                }
            } else {
                if (request != null && !imageFailed) AsyncImage(request, contentDescription = null,
                    contentScale = ContentScale.Crop, onError = { imageFailed = true }, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xE6101826)))))
                Text(facet.name, color = Color(0xFFEAF0F6), fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}
