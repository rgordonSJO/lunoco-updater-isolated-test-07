package com.github.damontecres.wholphin.ui.discovery

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/** Presentation-only rows: never written to saved Home configuration. Stable even before loading. */
internal fun homeDiscoveryRows(state: MovieExploreState): List<HomeRowLoadingState.Success> = listOf(
    Triple("studios", R.string.lunoco_browse_studio, state.studios),
    Triple("genres", R.string.lunoco_browse_genre, state.genres),
).map { (key, title, facets) ->
    HomeRowLoadingState.Success(
        title = ResStringProvider(title),
        items = state.scope?.let { scope -> facets.map { facet ->
            BaseItem(BaseItemDto(id = facet.id, name = facet.name,
                type = if (facet.kind == MovieFacetKind.STUDIO) BaseItemKind.STUDIO else BaseItemKind.GENRE),
                destinationOverride = facet.destination(scope))
        } }.orEmpty(),
        viewOptions = HomeRowViewOptions(spacing = 12),
        showViewMore = false,
        presentationKey = "lunoco_discovery:$key",
    )
}
