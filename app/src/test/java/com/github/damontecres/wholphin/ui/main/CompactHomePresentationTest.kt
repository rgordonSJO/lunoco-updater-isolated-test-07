package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.ui.AspectRatio
import org.jellyfin.sdk.model.api.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CompactHomePresentationTest {
    private val minute = 600_000_000L
    private fun item(runtime: Long? = 90 * minute, resume: Long = 30 * minute,
        played: Boolean = false, percent: Double? = null, type: BaseItemKind = BaseItemKind.MOVIE) =
        BaseItemDto(id = UUID(0, 1), type = type, runTimeTicks = runtime,
            userData = UserItemDataDto(playbackPositionTicks = resume, played = played, playedPercentage = percent,
                playCount = 0, isFavorite = false, key = "test", itemId = UUID(0, 1)))

    @Test fun remainingTimeComesFromTheSpecificItemAndRoundsUp() {
        val value = homeWatchProgress(item())
        assertEquals(60L, value.remainingMinutes)
        assertEquals(1f / 3f, value.fraction!!, .00001f)
        val episode = homeWatchProgress(item(10 * minute, 8 * minute + minute / 2, type = BaseItemKind.EPISODE))
        assertEquals(2L, episode.remainingMinutes)
    }

    @Test fun shortRemainderAndCompletionAreHandledWithoutTimers() {
        val short = homeWatchProgress(item(minute, minute - 1))
        assertEquals(1L, short.remainingTicks)
        assertEquals(1L, short.remainingMinutes)
        assertNull(homeWatchProgress(item(minute, minute)).remainingTicks)
        assertNull(homeWatchProgress(item(minute, minute + 1)).remainingTicks)
        assertNull(homeWatchProgress(item(played = true)).fraction)
        assertNull(homeWatchProgress(item(played = true)).remainingTicks)
    }

    @Test fun missingInvalidAndAggregateRuntimeDoNotInventRemainingTime() {
        assertNull(homeWatchProgress(item(null)).remainingTicks)
        assertNull(homeWatchProgress(item(0)).remainingTicks)
        assertNull(homeWatchProgress(item(-1)).remainingTicks)
        assertNull(homeWatchProgress(item(resume = -1)).remainingTicks)
        assertNull(homeWatchProgress(item(resume = 0)).remainingTicks)
        assertNull(homeWatchProgress(item(type = BaseItemKind.SERIES)).remainingTicks)
        assertNull(homeWatchProgress(item(type = BaseItemKind.SEASON)).remainingTicks)
    }

    @Test fun percentageFallbackIsFiniteAndClamped() {
        assertEquals(.5f, homeWatchProgress(item(null, percent = 50.0)).fraction!!, .00001f)
        assertEquals(1f, homeWatchProgress(item(null, percent = 300.0)).fraction!!, .00001f)
        assertEquals(0f, homeWatchProgress(item(null, percent = -10.0)).fraction!!, .00001f)
        assertNull(homeWatchProgress(item(null, percent = Double.NaN)).fraction)
        assertNull(homeWatchProgress(item(null, percent = Double.POSITIVE_INFINITY)).fraction)
    }

    @Test fun longRuntimeCannotOverflowMinuteRounding() {
        assertTrue(homeWatchProgress(item(Long.MAX_VALUE, 1)).remainingMinutes!! > 0)
    }

    @Test fun defaultsCompactWithoutModifyingSavedRowObjectsOrOrder() {
        val rows = listOf(HomeRowConfig.NextUp(), HomeRowConfig.RecentlyAdded(UUID(0, 8)),
            HomeRowConfig.ContinueWatching(), HomeRowConfig.ByParent(UUID(0, 9)))
        val before = rows.toList()
        val displayed = rows.map { compactHomeOptions(it, it.viewOptions) }
        assertEquals(before, rows)
        assertEquals(90, displayed[0].heightDp)
        assertEquals(144, displayed[1].heightDp)
        assertEquals(90, displayed[2].heightDp)
        assertEquals(rows[3].viewOptions, displayed[3])
        assertEquals(AspectRatio.WIDE, displayed[2].aspectRatio)
        assertEquals(4, rows.size)
    }

    @Test fun customPresentationAndCombinedRowBehaviorArePreserved() {
        val custom = HomeRowViewOptions(heightDp = 200, spacing = 20, showTitles = true)
        assertSame(custom, compactHomeOptions(HomeRowConfig.ContinueWatching(), custom))
        assertSame(custom, compactHomeOptions(HomeRowConfig.RecentlyAdded(UUID(0, 1)), custom))
        assertTrue(homeResumeRow(HomeRowConfig.ContinueWatchingCombined()))
        assertTrue(homeResumeRow(HomeRowConfig.NextUp()))
        assertFalse(homeResumeRow(HomeRowConfig.RecentlyAdded(UUID(0, 1))))
    }

    @Test fun restoredRowsTakePrecedenceAndAbsentSpotlightDoesNotTrapFocus() {
        assertEquals(HomeEntryTarget.SPOTLIGHT, homeEntryTarget(-1, true))
        assertEquals(HomeEntryTarget.ROW, homeEntryTarget(-1, false))
        assertEquals(HomeEntryTarget.ROW, homeEntryTarget(3, true))
        assertEquals(HomeEntryTarget.ROW, homeEntryTarget(0, false))
    }
}
