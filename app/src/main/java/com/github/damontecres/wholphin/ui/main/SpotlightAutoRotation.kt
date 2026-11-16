package com.github.damontecres.wholphin.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal const val SPOTLIGHT_ROTATION_INTERVAL_MS = 10_000L

// A State is provided so scrolling invalidates only the timer, not the Home composition.
internal val LocalSpotlightVisibility = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(true) }

internal fun spotlightMayAutoRotate(count: Int, controlsFocused: Boolean, visible: Boolean) =
    count > 1 && !controlsFocused && visible

/** One cancellable foreground loop; no tick state, metadata access or focus requests. */
internal suspend fun rotateSpotlightWhileResumed(lifecycle: Lifecycle, advance: () -> Unit) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (currentCoroutineContext().isActive) {
            delay(SPOTLIGHT_ROTATION_INTERVAL_MS)
            advance()
        }
    }
}

@Composable
internal fun SpotlightAutoRotation(
    movieCount: Int,
    selection: State<String?>,
    controlsFocused: State<Boolean>,
    onNext: () -> Unit,
) {
    val viewport = LocalSpotlightVisibility.current
    val visible by viewport
    val selectedId = selection.value // Immutable snapshot for the old tick's identity guard.
    val interacting by controlsFocused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val advance by rememberUpdatedState(onNext)
    // Changing the displayed ID (including manual paging) starts a fresh interval.
    // Focus/visibility/lifecycle cancellation never changes the selected movie.
    LaunchedEffect(lifecycle, selection, movieCount, selectedId, interacting, visible) {
        if (spotlightMayAutoRotate(movieCount, interacting, visible)) {
            rotateSpotlightWhileResumed(lifecycle) {
                // A manual action/focus event may precede effect cancellation by one frame.
                // Never let an already-waiting tick overwrite that newer interaction.
                if (selection.value == selectedId &&
                    spotlightMayAutoRotate(movieCount, controlsFocused.value, viewport.value)) {
                    advance()
                }
            }
        }
    }
}
