package com.github.damontecres.wholphin.ui.nav

import com.github.damontecres.wholphin.ui.setup.LocalLunocoUpdater
import com.github.damontecres.wholphin.ui.setup.LunocoUpdateHost
import com.github.damontecres.wholphin.ui.setup.observeUpdateInteraction
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate
import androidx.compose.foundation.background
import androidx.tv.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.rememberDrawerState
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Top scrim configuration for text readability (clock, season tabs)
const val TOP_SCRIM_ALPHA = 0.55f
const val TOP_SCRIM_END_FRACTION = 0.25f // Fraction of backdrop image height

/** Settings keeps its serialized full-screen marker, but uses the normal Lunoco chrome. */
internal val Destination.usesLunocoBrowseShell: Boolean
    get() = this is Destination.Settings || !fullScreen

@HiltViewModel
class ApplicationContentViewModel
    @Inject
    constructor(
        val backdropService: BackdropService,
    ) : ViewModel() {
        fun clearBackdrop() {
            viewModelScope.launchIO { backdropService.clearBackdrop() }
        }
    }

/**
 * This is generally the root composable of the of the app
 *
 * Here the navigation backstack is used and pages are rendered in the nav drawer or full screen
 */
@Composable
fun ApplicationContent(
    server: JellyfinServer,
    user: JellyfinUser,
    navigationManager: NavigationManager,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    enableTopScrim: Boolean = true,
    viewModel: ApplicationContentViewModel = hiltViewModel(),
) {
    val activityLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(activityLifecycle, navigationManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                navigationManager.onApplicationForeground(LocalDate.now().toEpochDay())
            }
        }
        activityLifecycle.addObserver(observer)
        onDispose { activityLifecycle.removeObserver(observer) }
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    Box(
        modifier = modifier.observeUpdateInteraction(LocalLunocoUpdater.current),
    ) {
        val backdropStyle = preferences.appPreferences.interfacePreferences.backdropStyle
        // Poster browsing stays flat. Detail pages keep their stable item artwork.
        val top = navigationManager.backStack.lastOrNull()
        val flatBrowsing = top is Destination.Settings || top is Destination.Home || top is Destination.Search ||
            top is Destination.Favorites || top is Destination.MoreHomeRow ||
            top is Destination.ItemGrid<*> || top is Destination.FilteredCollection ||
            (top is Destination.MediaItem && top.type in listOf(
                org.jellyfin.sdk.model.api.BaseItemKind.COLLECTION_FOLDER,
                org.jellyfin.sdk.model.api.BaseItemKind.USER_VIEW,
            ))
        if (flatBrowsing) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        } else {
            Backdrop(
                drawerIsOpen = drawerState.isOpen,
                backdropStyle = backdropStyle,
                enableTopScrim = enableTopScrim,
                viewModel = viewModel,
            )
        }
        val navDrawerListState = rememberLazyListState()
        NavDisplay(
            backStack = navigationManager.backStack,
            onBack = { navigationManager.goBack() },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider = { key ->
                key as Destination
                val contentKey = "${key}_${server?.id}_${user?.id}"
                NavEntry(key, contentKey = contentKey) {
                    if (!key.usesLunocoBrowseShell) {
                        DestinationContent(
                            destination = key,
                            preferences = preferences,
                            onClearBackdrop = viewModel::clearBackdrop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (user != null && server != null) {
                        NavDrawer(
                            destination = key,
                            preferences = preferences,
                            user = user,
                            server = server,
                            drawerState = drawerState,
                            navDrawerListState = navDrawerListState,
                            onClearBackdrop = viewModel::clearBackdrop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ErrorMessage("Trying to go to $key without a user logged in", null)
                    }
                }
            },
        )
        LunocoUpdateHost(top)
    }
}
