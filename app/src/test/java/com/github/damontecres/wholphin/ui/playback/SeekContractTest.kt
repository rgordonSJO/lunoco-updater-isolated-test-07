@file:OptIn(androidx.media3.common.util.UnstableApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.damontecres.wholphin.ui.playback

import androidx.media3.common.Player
import com.github.damontecres.wholphin.ui.playback.overlay.SeekBarState
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** Locks down existing seek timing/position semantics; no decoder, network or TV required. */
class SeekContractTest {
    private val player = mockk<Player>(relaxed = true)

    @Test fun burstSeeksCommitOnlyLatestPositionAfterExistingQuietPeriod() = runTest {
        val state = SeekBarState(player, backgroundScope)
        backgroundScope.launch { state.observe() }
        runCurrent()
        state.onValueChange(10_000L)
        runCurrent()
        advanceTimeBy(500)
        state.onValueChange(25_000L)
        runCurrent()
        advanceTimeBy(749)
        runCurrent()
        verify(exactly = 0) { player.seekTo(any<Long>()) }
        advanceTimeBy(1)
        runCurrent()
        verify(exactly = 1) { player.seekTo(25_000L) }
        verify(exactly = 1) { player.seekTo(any<Long>()) }
    }

    @Test fun leavingSeekObserverDoesNotCommitPendingSeek() = runTest {
        val state = SeekBarState(player, backgroundScope)
        val observer = backgroundScope.launch { state.observe() }
        runCurrent()
        state.onValueChange(15_000L)
        runCurrent()
        observer.cancel()
        advanceTimeBy(1_000)
        runCurrent()
        verify(exactly = 0) { player.seekTo(any<Long>()) }
    }

    @Test fun rewindClampsAtStart() {
        every { player.currentPosition } returns 3_000L
        player.seekBack(10.seconds)
        verify(exactly = 1) { player.seekTo(0L) }
    }

    @Test fun forwardClampsAtKnownEnd() {
        every { player.currentPosition } returns 95_000L
        every { player.duration } returns 100_000L
        player.seekForward(10.seconds)
        verify(exactly = 1) { player.seekTo(100_000L) }
    }

    @Test fun normalSeekOffsetsKeepMillisecondAccuracy() {
        every { player.currentPosition } returns 30_123L
        every { player.duration } returns 100_000L
        player.seekBack(10.seconds)
        player.seekForward(10.seconds)
        verify(exactly = 1) { player.seekTo(20_123L) }
        verify(exactly = 1) { player.seekTo(40_123L) }
    }
}
