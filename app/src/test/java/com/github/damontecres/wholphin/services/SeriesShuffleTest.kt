package com.github.damontecres.wholphin.services

import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.LocationType
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import kotlin.random.Random

class SeriesShuffleTest {
    private fun episode(season: Int) = BaseItemDto(
        id = UUID.randomUUID(), type = BaseItemKind.EPISODE,
        parentIndexNumber = season, locationType = LocationType.FILE_SYSTEM,
    )

    @Test fun includesEveryRegularEpisodeAcrossPagesAndSeasonsWithoutDuplicates() = runTest {
        val regular = List(205) { episode(it / 70 + 1) }
        val source = regular + regular.first() + episode(0) +
            episode(3).copy(locationType = LocationType.VIRTUAL)
        val calls = mutableListOf<Int>()
        val queue = loadSeriesShuffleEpisodes(Random(17)) { start, limit ->
            calls += start
            BaseItemDtoQueryResult(
                items = source.drop(start).take(limit), totalRecordCount = source.size, startIndex = start,
            )
        }
        assertEquals(listOf(0, 100, 200), calls)
        assertEquals(205, queue.size)
        assertEquals(regular.map { it.id }.toSet(), queue.map { it.id }.toSet())
        assertEquals(queue.size, queue.map { it.id }.distinct().size)
        assertNotEquals(regular.map { it.id }, queue.map { it.id })
        assertEquals(setOf(1, 2, 3), queue.map { it.parentIndexNumber }.toSet())
    }

    @Test fun emptySeriesAndServersIgnoringOffsetsTerminateSafely() = runTest {
        assertTrue(loadSeriesShuffleEpisodes { _, _ -> BaseItemDtoQueryResult(items = emptyList(), totalRecordCount = 0, startIndex = 0) }.isEmpty())
        var calls = 0
        val source = List(100) { episode(1) }
        val queue = loadSeriesShuffleEpisodes { _, _ ->
            calls++
            BaseItemDtoQueryResult(items = source, totalRecordCount = 200, startIndex = 0)
        }
        assertEquals(2, calls)
        assertEquals(100, queue.size)
    }
}
