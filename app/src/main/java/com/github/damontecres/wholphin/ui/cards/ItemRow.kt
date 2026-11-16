package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.damontecres.wholphin.ui.main.LocalJellyLuHomeCards
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun <T> ItemRow(
    title: String,
    items: List<T?>,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    cardContent: @Composable (
        index: Int,
        item: T?,
        modifier: Modifier,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    showViewMore: Boolean = false,
    viewMoreCardContent: @Composable (Modifier) -> Unit = {},
    itemKey: ((Int, T?) -> Any)? = null,
    itemContentType: (Int, T?) -> Any? = { _, _ -> null },
    resetGeneration: Long? = null,
    onFocusReady: (() -> Unit)? = null,
    initialPosition: Int? = null,
    minimumRowHeight: Dp = 0.dp,
) {
    val calmHome = LocalJellyLuHomeCards.current
    val edgePadding = if (calmHome) 40.dp else horizontalPadding
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialPosition ?: 0)
    val firstFocus = remember { FocusRequester() }
    val focusRequester = remember { FocusRequester() }
    var position by rememberInt(initialPosition ?: 0)
    if (initialPosition != null) {
        var initialApplied by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(initialPosition) {
            if (!initialApplied && items.isNotEmpty()) {
                position = initialPosition.coerceIn(items.indices)
                state.scrollToItem(position)
                initialApplied = true
            }
        }
    }
    val homeRestoration = calmHome && resetGeneration != null
    val anchor = rememberSaveable(saver = HomeRowFocusAnchor.Saver) { HomeRowFocusAnchor() }
    val keys = remember(items, itemKey, showViewMore, homeRestoration) {
        if (homeRestoration) items.mapIndexed { index, item ->
            if (item == null) null else itemKey?.invoke(index, item)?.toString() ?: "index:$index"
        } + if (showViewMore) listOf("item_row_view_more") else emptyList()
        else emptyList()
    }
    var appliedReset by rememberSaveable { mutableStateOf(0L) }
    // Observe reset completion here, but keep ordinary focus-anchor writes unobserved.
    // Otherwise a scroll reset can leave the requester attached to the old card.
    val target = if (homeRestoration) {
        if (appliedReset != resetGeneration) 0 else anchor.resolve(keys)
    } else position
    var enterAtStart by rememberSaveable { mutableStateOf(false) }
    val currentOnFocusReady by rememberUpdatedState(onFocusReady)
    LaunchedEffect(resetGeneration, keys) {
        if (resetGeneration != null && appliedReset != resetGeneration) {
            position = 0
            anchor.record(0, keys.firstOrNull())
            state.scrollToItem(0)
            enterAtStart = true
            appliedReset = resetGeneration
        }
        if (homeRestoration && keys.isNotEmpty()) {
            snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > 0 }
            val restored = anchor.resolve(keys)
            if (state.layoutInfo.visibleItemsInfo.none { it.index == restored }) state.scrollToItem(restored)
            withFrameNanos { }
            currentOnFocusReady?.invoke()
        }
    }

    val currentOnClickItem by rememberUpdatedState(onClickItem)
    val currentOnLongClickItem by rememberUpdatedState(onLongClickItem)

    Column(
        verticalArrangement = Arrangement.spacedBy(if (calmHome) 4.dp else 8.dp),
        modifier =
            modifier.focusProperties {
                onEnter = {
                    if (enterAtStart) {
                        if (firstFocus.tryRequestFocus()) enterAtStart = false
                    } else focusRequester.tryRequestFocus()
                }
            },
    ) {
        ItemRowTitle(
            title,
            modifier = if (LocalJellyLuHomeCards.current) {
                Modifier.padding(start = (edgePadding - 8.dp).coerceAtLeast(0.dp))
            } else Modifier,
        )

        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(horizontalPadding),
            contentPadding = PaddingValues(horizontal = edgePadding, vertical = if (calmHome) 4.dp else 8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (minimumRowHeight > 0.dp) Modifier.heightIn(min = minimumRowHeight) else Modifier)
                    .focusGroup()
                    // Discard the restorer's cached child only for an explicit reset.
                    // Otherwise it can override the freshly reset first-card requester.
                    .then(if (homeRestoration && enterAtStart) Modifier else Modifier.focusRestorer(firstFocus))
                    .focusRequester(focusRequester),
        ) {
            itemsIndexed(items, key = itemKey, contentType = itemContentType) { index, item ->
                val cardModifier =
                    remember(index, target, keys.getOrNull(index), homeRestoration) {
                        val targetModifier = if (index == target) Modifier.focusRequester(firstFocus) else Modifier
                        if (homeRestoration) targetModifier.onFocusChanged {
                            if (it.isFocused) anchor.record(index, keys.getOrNull(index))
                        } else targetModifier
                    }

                val onClick =
                    remember(index, item) {
                        {
                            position = index
                            if (homeRestoration) anchor.record(index, keys.getOrNull(index))
                            if (item != null) currentOnClickItem(index, item)
                        }
                    }

                val onLongClick =
                    remember(index, item) {
                        {
                            position = index
                            if (homeRestoration) anchor.record(index, keys.getOrNull(index))
                            if (item != null) currentOnLongClickItem(index, item)
                        }
                    }

                cardContent.invoke(
                    index,
                    item,
                    cardModifier,
                    onClick,
                    onLongClick,
                )
            }
            if (showViewMore) {
                item(key = "item_row_view_more", contentType = "view_more") {
                    val cardModifier =
                        remember(items.size, target, homeRestoration) {
                            val targetModifier = if (target == items.size) Modifier.focusRequester(firstFocus) else Modifier
                            if (homeRestoration) targetModifier.onFocusChanged {
                                if (it.isFocused) anchor.record(items.size, "item_row_view_more")
                            } else targetModifier
                        }
                    viewMoreCardContent.invoke(cardModifier)
                }
            }
        }
    }
}

@Composable
@NonRestartableComposable
fun ItemRowTitle(
    title: String,
    modifier: Modifier = Modifier,
) = Text(
    text = title,
    style = if (LocalJellyLuHomeCards.current) {
        MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 22.sp)
    } else MaterialTheme.typography.titleLarge,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = modifier.padding(start = 8.dp),
)
