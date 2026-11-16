package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.theme.colors.LunocoUiColors
import org.jellyfin.sdk.model.api.ImageType

internal fun spotlightPageIndex(index: Int, count: Int, delta: Int): Int =
    if (count <= 0) 0 else Math.floorMod(index + delta, count)

// The parent owns scroll geometry. Only entering the control group requests a reveal.
internal val LocalSpotlightReveal = staticCompositionLocalOf<() -> Unit> { {} }
internal val LocalHomeResetGeneration = staticCompositionLocalOf { 0L }

/** Only this lazy-item scope observes movie/pager state. Poster focus never changes artwork. */
@Composable
internal fun JellyLuSpotlight(
    viewModel: SpotlightViewModel,
    groupFocus: FocusRequester,
    rowFocus: FocusRequester?,
    onFocus: (Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loading && state.movies.isEmpty()) return
    val background = MaterialTheme.colorScheme.background
    val selectedId = rememberSaveable(state.scope?.toString(), state.selectionDay) { mutableStateOf<String?>(null) }
    val requestedIndex = state.movies.indexOfFirst { it.id.toString() == selectedId.value }.coerceAtLeast(0)
    val requestedMovie = state.movies.getOrNull(requestedIndex)
    val imageService = LocalImageUrlService.current
    val context = LocalContext.current
    val backdrop = remember(state.scope, imageService, requestedMovie?.id, requestedMovie?.backdropImageTags) {
        requestedMovie?.backdropImageTags?.firstOrNull()?.let { tag ->
            imageService.getItemImageUrl(requestedMovie.id, ImageType.BACKDROP,
                maxWidth = 1600, maxHeight = 900, tag = tag)
        }
    }
    val backdropRequest = remember(backdrop, context) {
        backdrop?.let { ImageRequest.Builder(context).data(it).size(1600, 900).scale(coil3.size.Scale.FIT).build() }
    }
    val frame by key(state.scope, state.selectionDay) {
        rememberSpotlightFrame(requestedMovie, backdropRequest)
    }
    val movie = frame.movie
    val index = state.movies.indexOfFirst { it.id == movie?.id }.coerceAtLeast(0)
    // Actions stay referentially stable while reading the currently committed presentation.
    // Do not capture a movie/index in remembered callbacks: daily snapshots can replace both.
    val currentMovie = rememberUpdatedState(movie)
    val currentMovies = rememberUpdatedState(state.movies)
    val automaticPage = remember(selectedId) { booleanArrayOf(false) }
    // If the next artwork is pending, actions belong to the still-visible movie. Cancel that
    // pending selection before leaving so Details/Back restores the movie the user actually saw.
    val onPlay = remember(viewModel, selectedId) { {
        currentMovie.value?.let { automaticPage[0] = false; selectedId.value = it.id.toString(); viewModel.play(it) }; Unit
    } }
    val onDetails = remember(viewModel, selectedId) { {
        currentMovie.value?.let { automaticPage[0] = false; selectedId.value = it.id.toString(); viewModel.details(it) }; Unit
    } }
    val onPage = remember(selectedId) {
        { delta: Int ->
            val movies = currentMovies.value
            val currentIndex = movies.indexOfFirst { it.id.toString() == selectedId.value }.coerceAtLeast(0)
            movies.getOrNull(spotlightPageIndex(currentIndex, movies.size, delta))?.let {
                selectedId.value = it.id.toString()
            }
            Unit
        }
    }
    val onPrevious = remember(onPage) { { automaticPage[0] = false; onPage(-1) } }
    val onNext = remember(onPage) { { automaticPage[0] = false; onPage(1) } }
    val onAutomaticNext = remember(onPage) { { automaticPage[0] = true; onPage(1) } }
    val controlsFocused = remember { mutableStateOf(false) }
    val onInteraction = remember(selectedId) { { focused: Boolean ->
        controlsFocused.value = focused
        // Do not let an unseen automatic image load finish changing the movie after the
        // user enters its controls. Manual paging within this group still works normally.
        if (focused && automaticPage[0]) {
            automaticPage[0] = false
            currentMovie.value?.let { selectedId.value = it.id.toString() }
        }
    } }
    SpotlightAutoRotation(state.movies.size, selectedId, controlsFocused, onAutomaticNext)
    val logo = remember(state.scope, imageService, movie?.id, movie?.imageTags) {
        movie?.imageTags?.get(ImageType.LOGO)?.let { tag ->
            imageService.getItemImageUrl(movie.id, ImageType.LOGO,
                maxWidth = 760, maxHeight = 120, tag = tag)
        }
    }
    val description = remember(movie?.overview) {
        HtmlCompat.fromHtml(movie?.overview.orEmpty().take(2000), HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString().replace(Regex("\\s+"), " ").trim()
    }
    val metadata = remember(movie?.id, movie?.genres, movie?.productionYear, movie?.officialRating) {
        (movie?.genres.orEmpty().take(2) + listOfNotNull(movie?.productionYear?.toString(), movie?.officialRating))
            .joinToString("  ·  ")
    }
    val pageDescription = stringResource(R.string.jellylu_spotlight_page,
        index + 1, state.movies.size, movie?.name.orEmpty())
    Box(Modifier.fillMaxWidth().height(230.dp).background(background)) {
        key(state.scope) {
            SpotlightArtwork(frame.painter, background, Modifier.fillMaxSize())
        }
        Box(Modifier.matchParentSize().drawWithCache {
            // Reuse the existing scrim draw to hide the fitted image's edge. Cache geometry
            // per painter/viewport; fade alpha never invalidates text, controls or Home rows.
            val start = maxOf(.38f, spotlightArtworkStartFraction(frame.painter, size) + .01f).coerceAtMost(.94f)
            val end = maxOf(.70f, start + .18f).coerceAtMost(.99f)
            val brush = Brush.horizontalGradient(
                0f to background, start to background,
                end to background.copy(alpha = .18f), 1f to Color.Transparent)
            onDrawBehind { drawRect(brush) }
        })
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to background.copy(alpha = .65f), .18f to Color.Transparent,
            .68f to Color.Transparent, 1f to background)))
        Text(stringResource(R.string.jellylu_spotlight), fontSize = 12.sp, lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 40.dp, top = 6.dp).semantics { heading() })
        if (movie == null) {
            Text(stringResource(R.string.jellylu_spotlight_loading), color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 40.dp, top = 80.dp))
        } else {
            SpotlightTitle(movie.name.orEmpty(), logo)
            Text(metadata, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 40.dp, top = 90.dp).width(380.dp))
            Text(description, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 40.dp, top = 112.dp).width(380.dp).height(36.dp))
            SpotlightControls(
                groupFocus = groupFocus, rowFocus = rowFocus, onFocus = onFocus,
                onInteraction = onInteraction,
                hasMultiple = state.movies.size > 1, resume = spotlightResumeMs(movie) > 0,
                pageDescription = pageDescription, onPlay = onPlay, onDetails = onDetails,
                onPrevious = onPrevious, onNext = onNext,
            )
            if (state.movies.size > 1) Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            ) {
                repeat(state.movies.size) { dot ->
                    Box(Modifier.size(if (dot == index) 5.dp else 4.dp)
                        .background(if (dot == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = .35f), CircleShape))
                }
            }
        }
    }
}

/**
 * The page announcement may change, but unchanged buttons keep the same callbacks/modifiers.
 * Focus identity and saveable last-control state survive paging and Details/Back as before.
 */
@Composable
private fun SpotlightControls(
    groupFocus: FocusRequester,
    rowFocus: FocusRequester?,
    onFocus: (Int) -> Unit,
    onInteraction: (Boolean) -> Unit,
    hasMultiple: Boolean,
    resume: Boolean,
    pageDescription: String,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val navFocus = LocalJellyLuRailFocus.current
    val controls = remember { List(4) { FocusRequester() } }
    val resetGeneration = LocalHomeResetGeneration.current
    // Save the generation with the control: rememberSaveable inputs alone do not
    // invalidate a restored value when this lazy item was disposed offscreen.
    val lastControl = rememberSaveable { longArrayOf(resetGeneration, 0L) }
    if (lastControl[0] != resetGeneration) {
        lastControl[0] = resetGeneration
        lastControl[1] = 0L
    }
    val reveal = LocalSpotlightReveal.current
    val groupWasFocused = remember { booleanArrayOf(false) }
    DisposableEffect(onInteraction) {
        onDispose { onInteraction(false) }
    }
    val groupModifier = remember(groupFocus, hasMultiple, reveal, lastControl, onInteraction) {
        Modifier.padding(start = 40.dp, top = 158.dp)
            .onFocusChanged {
                if (it.hasFocus != groupWasFocused[0]) {
                    onInteraction(it.hasFocus)
                    if (it.hasFocus) reveal()
                }
                groupWasFocused[0] = it.hasFocus
            }
            .focusProperties {
                onEnter = {
                    controls[lastControl[1].toInt().coerceIn(0, if (hasMultiple) 3 else 1)].requestFocus()
                }
            }
            .focusRequester(groupFocus).focusGroup()
    }
    val controlModifiers = remember(onFocus, navFocus, rowFocus, lastControl) {
        List(4) { control ->
            Modifier.focusRequester(controls[control])
                .onFocusChanged {
                    if (it.isFocused) {
                        lastControl[1] = control.toLong()
                        onFocus(control)
                    }
                }
                .focusProperties {
                    navFocus?.let { up = it }
                    rowFocus?.let { down = it }
                }
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = groupModifier.semantics { stateDescription = pageDescription },
    ) {
        SpotlightButton(onPlay, controlModifiers[0].width(88.dp), primary = true) {
            Text(stringResource(if (resume) R.string.resume else R.string.play))
        }
        SpotlightButton(onDetails, controlModifiers[1].width(80.dp)) {
            Text(stringResource(R.string.jellylu_spotlight_details))
        }
        if (hasMultiple) {
            Spacer(Modifier.width(8.dp))
            SpotlightButton(onPrevious, controlModifiers[2].width(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    stringResource(R.string.jellylu_spotlight_previous), Modifier.size(20.dp))
            }
            SpotlightButton(onNext, controlModifiers[3].width(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    stringResource(R.string.jellylu_spotlight_next), Modifier.size(20.dp))
            }
        }
    }
}

/** Logo completion invalidates only this fixed title area, not the banner and controls. */
@Composable
private fun SpotlightTitle(title: String, logo: String?) {
    val readiness = remember { SpotlightLogoReadiness() }
    val imageLoader = SingletonImageLoader.get(LocalContext.current)
    var logoLoaded by remember(logo) { mutableStateOf(readiness.contains(logo, imageLoader.memoryCache)) }
    Box(Modifier.padding(start = 40.dp, top = 24.dp).width(380.dp).height(60.dp)) {
        if (!logoLoaded) Text(title, fontSize = 28.sp, lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.align(Alignment.BottomStart))
        if (logo != null) {
            AsyncImage(logo, if (logoLoaded) title else null,
                contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
                onSuccess = { readiness.success(logo, it.result.memoryCacheKey); logoLoaded = true },
                onError = { readiness.failure(logo); logoLoaded = false },
                modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun SpotlightButton(
    onClick: () -> Unit,
    modifier: Modifier,
    primary: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Button(onClick, modifier = modifier.height(32.dp),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (primary) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.background,
        ),
    ) {
        // TV Button's internal row can wrap at its 58dp minimum inside our wider surface.
        // Fill that row's available content area so the entire group is centered.
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Compact Home-only resume card: actions and remote play remain owned by the existing row. */
@Composable
internal fun CompactHomeResumeCard(
    item: BaseItem,
    cardHeight: androidx.compose.ui.unit.Dp,
    aspectRatio: Float,
    imageType: ImageType,
    contentScale: ContentScale,
    useSeries: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
) {
    val imageService = LocalImageUrlService.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val width = cardHeight * aspectRatio
    val imageUrl = remember(item, imageType, cardHeight, density, useSeries) {
        imageService.getItemImageUrl(item, imageType,
            fillHeight = with(density) { cardHeight.roundToPx() }, useSeriesForPrimary = useSeries)
    }
    val progress = remember(item.data.userData, item.data.runTimeTicks) { homeWatchProgress(item.data) }
    val time = when {
        progress.remainingTicks == null -> null
        progress.remainingTicks < 600_000_000L -> stringResource(R.string.jellylu_home_less_minute_left)
        else -> stringResource(R.string.jellylu_home_minutes_left, progress.remainingMinutes!!)
    }
    val detail = listOfNotNull(item.data.seasonEpisode.takeIf { item.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE }, time)
        .joinToString(" · ")
    val cardTitle = item.title ?: item.name.orEmpty()
    val accessible = listOfNotNull(cardTitle, detail.takeIf { it.isNotBlank() }).joinToString(", ")
    Card(onClick = onClick, onLongClick = onLongClick,
        scale = CardDefaults.scale(focusedScale = 1f),
        shape = CardDefaults.shape(RoundedCornerShape(6.dp)),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.border))),
        modifier = modifier.width(width).semantics { contentDescription = accessible },
    ) {
        Box(Modifier.fillMaxWidth().height(cardHeight)) {
            AsyncImage(imageUrl, null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
            progress.fraction?.takeIf { it > 0f && it < 1f }?.let { fraction ->
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                    .background(LunocoUiColors.ProgressTrack))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(fraction).height(3.dp)
                    .background(MaterialTheme.colorScheme.primary)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) })
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().background(LunocoUiColors.CardFooter)
                .heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(cardTitle, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            Text(detail, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth())
        }
    }
}
