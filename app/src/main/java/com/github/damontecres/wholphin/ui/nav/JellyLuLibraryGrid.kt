package com.github.damontecres.wholphin.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.data.filter.DefaultFilterOptions
import com.github.damontecres.wholphin.data.filter.DefaultTvFilterOptions
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderView
import com.github.damontecres.wholphin.ui.components.ViewOptionsPoster
import com.github.damontecres.wholphin.ui.components.ViewOptionsWide
import com.github.damontecres.wholphin.ui.discovery.LunocoMoviesWorkspace
import com.github.damontecres.wholphin.ui.data.MovieSortOptions
import com.github.damontecres.wholphin.ui.data.SeriesSortOptions
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.toServerString
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType

/** Direct entry into the existing paged grid, including its saved filters/sort and focus. */
@Composable
fun JellyLuLibraryGrid(
    destination: Destination.MediaItem,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    if (destination.collectionType == CollectionType.MOVIES) {
        LunocoMoviesWorkspace(destination.itemId, modifier) { gridModifier, onPosition ->
            DirectLibraryGrid(destination, preferences, gridModifier, viewModel, onPosition)
        }
    } else DirectLibraryGrid(destination, preferences, modifier, viewModel)
}

@Composable
private fun DirectLibraryGrid(
    destination: Destination.MediaItem,
    preferences: UserPreferences,
    modifier: Modifier,
    viewModel: PreferencesViewModel,
    positionCallback: ((Int, Int) -> Unit)? = null,
) {
    val series = destination.collectionType == CollectionType.TVSHOWS
    val movies = destination.collectionType == CollectionType.MOVIES
    val filter = remember(destination.itemId, destination.collectionType) {
        CollectionFolderFilter(
            filter = GetItemsFilter(
                includeItemTypes = listOf(when {
                    series -> BaseItemKind.SERIES
                    movies -> BaseItemKind.MOVIE
                    else -> BaseItemKind.VIDEO
                }),
            ),
        )
    }
    val onClickItem = remember(viewModel) {
        { _: Int, item: com.github.damontecres.wholphin.data.model.BaseItem ->
            viewModel.navigationManager.navigateTo(item.destination())
        }
    }
    CollectionFolderView(
        preferences = preferences,
        itemId = destination.itemId,
        // Match the existing library grid key; persisted display info remains keyed by library ID.
        viewModelKey = if (movies) "${destination.itemId}_library" else destination.itemId.toServerString(),
        initialFilter = filter,
        recursive = true,
        onClickItem = onClickItem,
        sortOptions = when {
            series -> SeriesSortOptions
            movies -> MovieSortOptions
            else -> VideoSortOptions
        },
        filterOptions = if (series) DefaultTvFilterOptions else DefaultFilterOptions,
        playEnabled = !series,
        defaultViewOptions = if (series || movies) ViewOptionsPoster else ViewOptionsWide,
        showTitle = true,
        positionCallback = positionCallback,
        modifier = modifier,
    )
}
