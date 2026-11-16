package com.github.damontecres.wholphin.services

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.LocationType
import kotlin.random.Random

/**
 * Collect a deterministic, user-scoped series listing before shuffling once.
 * Paging Random on the server can repeat/omit episodes; never randomize each page.
 */
internal suspend fun loadSeriesShuffleEpisodes(
    random: Random = Random.Default,
    fetchPage: suspend (startIndex: Int, limit: Int) -> BaseItemDtoQueryResult,
): List<BaseItemDto> {
    val episodes = linkedMapOf<java.util.UUID, BaseItemDto>()
    var offset = 0
    val pageSize = 100
    while (true) {
        val page = fetchPage(offset, pageSize)
        if (page.items.isEmpty()) break
        val newItems = page.items.filter { it.id !in episodes }
        // Also protects against servers/plugins that ignore the requested start index.
        if (newItems.isEmpty()) break
        newItems.forEach { episodes[it.id] = it }
        offset += page.items.size
        if (page.totalRecordCount > 0 && offset >= page.totalRecordCount) break
        if (page.totalRecordCount == 0 && page.items.size < pageSize) break
    }
    return episodes.values.filter {
        it.type == BaseItemKind.EPISODE &&
            it.parentIndexNumber != 0 &&
            it.locationType != LocationType.VIRTUAL
    }.shuffled(random)
}
