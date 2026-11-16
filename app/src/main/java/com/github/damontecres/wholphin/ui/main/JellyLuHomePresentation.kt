package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

// Scoped to Home; detail/library/player surfaces retain their existing presentation.
val LocalJellyLuHomeCards = staticCompositionLocalOf { false }
// Compatibility name retained: this points to Home in the compact top navigation.
val LocalJellyLuRailFocus = staticCompositionLocalOf<FocusRequester?> { null }
// Read only when completing a deferred Home focus transition, never on poster focus.
internal val LocalJellyLuNavigationHasFocus = staticCompositionLocalOf<() -> Boolean> { { false } }

/** Keep a fully visible card still; scroll vertically only far enough to reveal the next card. */
internal class JellyLuHomeBringIntoViewSpec(private val inset: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        when {
            size > containerSize - inset * 2 -> offset - inset
            offset < inset -> offset - inset
            offset + size > containerSize - inset -> offset + size - containerSize + inset
            else -> 0f
        }
}
