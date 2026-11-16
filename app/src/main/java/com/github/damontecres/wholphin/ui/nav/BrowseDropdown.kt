package com.github.damontecres.wholphin.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.*
import com.github.damontecres.wholphin.R

internal class BrowseMenuPosition(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val left = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left
            else anchorBounds.right - popupContentSize.width
        val below = anchorBounds.bottom + gap
        val top = if (below + popupContentSize.height <= windowSize.height - margin) below
            else anchorBounds.top - gap - popupContentSize.height
        return IntOffset(
            left.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
            top.coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)),
        )
    }
}

/** Focusable popup anchored below its originating tab; mounted only after explicit OK. */
@Composable
internal fun BrowseDropdown(
    label: String,
    choices: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (choices.isEmpty()) return
    val origin = remember { FocusRequester() }
    var open by remember { mutableStateOf(false) }
    var restoreOrigin by remember { mutableStateOf(false) }
    val dismiss = {
        open = false
        restoreOrigin = true
    }
    LaunchedEffect(open, restoreOrigin) {
        if (!open && restoreOrigin) {
            withFrameNanos { }
            origin.requestFocus()
            restoreOrigin = false
        }
    }
    BackHandler(open) { dismiss() }
    val menuTitle = stringResource(R.string.lunoco_browse_menu, label)
    val menuState = stringResource(if (open) R.string.lunoco_menu_expanded else R.string.lunoco_menu_collapsed)
    Box(modifier) {
        Button(
            onClick = { restoreOrigin = false; open = true },
            scale = ButtonDefaults.scale(focusedScale = 1f),
            colors = ButtonDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = if (selectedIndex >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContentColor = MaterialTheme.colorScheme.primary,
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp).focusRequester(origin)
                .semantics { stateDescription = menuState },
        ) {
            Text(label, fontSize = 15.sp, maxLines = 1)
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(14.dp))
        }
        if (open) {
            val focus = remember(choices.size) { List(choices.size) { FocusRequester() } }
            var placed by remember { mutableStateOf(false) }
            val density = LocalDensity.current
            val position = remember(density) {
                with(density) { BrowseMenuPosition(4.dp.roundToPx(), 8.dp.roundToPx()) }
            }
            Popup(
                popupPositionProvider = position,
                onDismissRequest = dismiss,
                properties = PopupProperties(focusable = true, clippingEnabled = true),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.width(224.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.border.copy(alpha = .4f), RoundedCornerShape(6.dp))
                        .padding(6.dp).semantics { paneTitle = menuTitle }
                        .onPreviewKeyEvent {
                            if (it.key == Key.Back) {
                                if (it.type == KeyEventType.KeyUp) dismiss()
                                true
                            } else false
                        }.focusGroup().onPlaced { placed = true },
                ) {
                    choices.forEachIndexed { index, title ->
                        key(index, title) {
                            ListItem(
                                selected = index == selectedIndex,
                                scale = ListItemDefaults.scale(focusedScale = 1f),
                                onClick = {
                                    restoreOrigin = false
                                    open = false
                                    onSelect(index)
                                },
                                headlineContent = { Text(title, fontSize = 16.sp, maxLines = 2) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .focusRequester(focus[index]).focusProperties {
                                        if (index == 0) up = FocusRequester.Cancel
                                        if (index == choices.lastIndex) down = FocusRequester.Cancel
                                    },
                            )
                        }
                    }
                }
            }
            LaunchedEffect(placed) {
                if (placed) {
                    withFrameNanos { }
                    focus.first().requestFocus()
                }
            }
        }
    }
}
