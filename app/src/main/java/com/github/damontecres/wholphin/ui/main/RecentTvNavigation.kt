package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

/** Only recent additions open the whole series; resume/Next Up and custom rows stay unchanged. */
internal fun homeItemDestination(rowType: HomeRowConfig?, item: BaseItem): Destination {
    if (rowType is HomeRowConfig.RecentlyAdded && item.data.extraType == null) {
        val seriesId = when (item.type) {
            BaseItemKind.SERIES -> item.id
            BaseItemKind.SEASON, BaseItemKind.EPISODE -> item.data.seriesId
            else -> null
        }?.takeUnless { it == UUID(0, 0) }
        // New entries start at the series overview, not a targeted episode.
        if (seriesId != null) return Destination.MediaItem(seriesId, BaseItemKind.SERIES)
    }
    return item.destination()
}

/** Validate only an activated parent link, never during focus, layout or row loading. */
internal suspend fun resolveHomeItemDestination(
    rowType: HomeRowConfig?,
    item: BaseItem,
    seriesAvailable: suspend (UUID) -> Boolean,
): Destination {
    val target = homeItemDestination(rowType, item)
    if (rowType !is HomeRowConfig.RecentlyAdded ||
        item.type !in listOf(BaseItemKind.EPISODE, BaseItemKind.SEASON) ||
        target !is Destination.MediaItem || target.type != BaseItemKind.SERIES
    ) return target
    return try {
        if (seriesAvailable(target.itemId)) target else item.destination()
    } catch (ex: CancellationException) {
        throw ex
    } catch (_: Exception) {
        item.destination()
    }
}
