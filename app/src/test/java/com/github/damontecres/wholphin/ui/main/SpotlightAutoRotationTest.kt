@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.github.damontecres.wholphin.ui.main

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class SpotlightAutoRotationTest {
    private val dispatcher = StandardTestDispatcher()
    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun eligibilityRequiresMultipleMoviesVisibleAndNoControlInteraction() {
        assertFalse(spotlightMayAutoRotate(0, false, true))
        assertFalse(spotlightMayAutoRotate(1, false, true))
        assertFalse(spotlightMayAutoRotate(5, true, true))
        assertFalse(spotlightMayAutoRotate(5, false, false))
        assertTrue(spotlightMayAutoRotate(5, false, true))
    }

    @Test fun foregroundTicksEveryTenSecondsAndWrapsTheExistingIndex() = runTest(dispatcher) {
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        var index = 0
        val visited = mutableListOf<Int>()
        val job = launch { rotateSpotlightWhileResumed(owner.lifecycle) {
            index = spotlightPageIndex(index, 3, 1); visited += index
        } }
        runCurrent()
        advanceTimeBy(9_999); runCurrent()
        assertTrue(visited.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(1), visited)
        advanceTimeBy(20_000); runCurrent()
        assertEquals(listOf(1, 2, 0), visited)
        job.cancel()
    }

    @Test fun backgroundCancelsPartialIntervalAndResumeWaitsTenFreshSeconds() = runTest(dispatcher) {
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        var ticks = 0
        val job = launch { rotateSpotlightWhileResumed(owner.lifecycle) { ticks++ } }
        runCurrent(); advanceTimeBy(9_000); runCurrent()
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        runCurrent(); advanceTimeBy(30_000); runCurrent()
        assertEquals(0, ticks)
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        runCurrent(); advanceTimeBy(9_999); runCurrent()
        assertEquals(0, ticks)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, ticks)
        job.cancel()
    }

    @Test fun disposalAndDestroyedLifecycleStopAllFutureTicks() = runTest(dispatcher) {
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        var ticks = 0
        val job = launch { rotateSpotlightWhileResumed(owner.lifecycle) { ticks++ } }
        runCurrent(); advanceTimeBy(10_000); runCurrent()
        assertEquals(1, ticks)
        job.cancel(); runCurrent(); advanceTimeBy(20_000); runCurrent()
        assertEquals(1, ticks)
        val replacement = launch { rotateSpotlightWhileResumed(owner.lifecycle) { ticks++ } }
        runCurrent()
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        runCurrent(); advanceTimeBy(20_000); runCurrent()
        assertEquals(1, ticks)
        assertTrue(replacement.isCompleted)
    }
}
