package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.cards.ItemRowTitle
import com.github.damontecres.wholphin.ui.formatSeasonNumber
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.tryRequestFocus

/** Uses existing episode artwork/progress and click behavior; focus waits for a placed lazy child. */
@Composable
internal fun RecentSeriesEpisodeRow(
    selection: RecentSeriesEpisodes,
    requestInitialFocus: Boolean,
    onInitialFocus: () -> Unit,
    onClick: (BaseItem) -> Unit,
    onLongClick: (Int, BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = selection.episodeIndex)
    var returnIndex by rememberInt(selection.episodeIndex)
    val targetFocus = remember { FocusRequester() }
    var placed by remember(returnIndex) { mutableStateOf(false) }
    val currentOnInitialFocus by rememberUpdatedState(onInitialFocus)
    LaunchedEffect(requestInitialFocus, placed) {
        if (requestInitialFocus && placed) {
            withFrameNanos { }
            if (targetFocus.tryRequestFocus()) currentOnInitialFocus()
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ItemRowTitle(selection.season.name ?: selection.season.indexNumber?.let { formatSeasonNumber(it) }.orEmpty())
        LazyRow(
            state = rowState, contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().focusRestorer(targetFocus).focusGroup(),
        ) {
            itemsIndexed(selection.episodes, key = { index, item -> item?.id?.toString() ?: "recent-episode-$index" }) { index, episode ->
                BannerCard(
                    item = episode, name = episode?.name, cornerText = episode?.ui?.episodeCornerText,
                    played = episode?.played ?: false, playPercent = episode?.data?.userData?.playedPercentage ?: 0.0,
                    aspectRatio = episode?.aspectRatio?.coerceAtLeast(AspectRatios.FOUR_THREE) ?: AspectRatios.WIDE,
                    cardHeight = 120.dp, useSeriesForPrimary = false,
                    onClick = { if (episode != null) { returnIndex = index; onClick(episode) } },
                    onLongClick = { if (episode != null) { returnIndex = index; onLongClick(index, episode) } },
                    modifier = Modifier
                        .then(if (index == returnIndex) Modifier.focusRequester(targetFocus).onGloballyPositioned { placed = true } else Modifier)
                        .testTag("recent-series-episode-${episode?.id}")
                        .semantics {
                            contentDescription = listOfNotNull(episode?.ui?.episodeCornerText, episode?.name).joinToString(" ")
                        },
                )
            }
        }
    }
}
