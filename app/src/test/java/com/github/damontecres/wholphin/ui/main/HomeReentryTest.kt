package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.nav.Destination
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeReentryTest {
    private fun manager() = NavigationManager()
    @Test fun explicitHomeSelectionResetsEvenWhenAlreadyHome() {
        val nav = manager()
        nav.selectHome()
        nav.selectHome()
        assertEquals(2L, nav.homeResetGeneration.value)
        assertFalse(nav.homeNeedsRefresh)
        assertEquals(1, nav.backStack.size)
    }
    @Test fun libraryBackRestoresOnlyWhenHomeIsActuallyReached() {
        val nav = manager()
        nav.navigateToFromDrawer(Destination.Search())
        assertEquals(0L, nav.homeResetGeneration.value)
        nav.navigateTo(Destination.MediaItem(UUID(0, 1), BaseItemKind.MOVIE))
        nav.goBack()
        assertEquals(0L, nav.homeResetGeneration.value)
        nav.goBack()
        assertEquals(0L, nav.homeResetGeneration.value)
        assertTrue(nav.backStack.last() is Destination.Home)
        assertFalse(nav.homeNeedsRefresh)
    }
    @Test fun spotlightAndRowDetailsBackBothPreserveHome() {
        val nav = manager()
        nav.preserveSpotlightHomeReturn()
        nav.navigateTo(Destination.MediaItem(UUID(0, 1), BaseItemKind.MOVIE))
        nav.goBack()
        assertEquals(0L, nav.homeResetGeneration.value)
        assertFalse(nav.homeNeedsRefresh)
        nav.navigateTo(Destination.MediaItem(UUID(0, 2), BaseItemKind.MOVIE))
        nav.goBack()
        assertEquals(0L, nav.homeResetGeneration.value)
        assertEquals(0L, nav.homeSessionGeneration.value)
        assertFalse(nav.consumeHomeRefresh())
    }
    @Test fun navigationRefreshSuppressionIsOneShot() {
        val nav = manager()
        nav.selectHome()
        assertFalse(nav.consumeHomeRefresh())
        assertTrue(nav.consumeHomeRefresh())
    }
    @Test fun newDayForegroundRefreshesSpotlightSessionWithoutResettingHome() {
        val nav = manager()
        nav.onApplicationForeground(100)
        nav.onApplicationForeground(100)
        assertEquals(0L, nav.homeResetGeneration.value)
        nav.onApplicationForeground(101)
        assertEquals(0L, nav.homeResetGeneration.value)
        assertEquals(1L, nav.homeSessionGeneration.value)
        assertTrue(nav.homeNeedsRefresh)
    }
    @Test fun foregroundWhileInSpotlightDetailsDoesNotDisturbItsReturn() {
        val nav = manager()
        nav.onApplicationForeground(100)
        nav.preserveSpotlightHomeReturn()
        nav.navigateTo(Destination.MediaItem(UUID(0, 1), BaseItemKind.MOVIE))
        nav.onApplicationForeground(101)
        nav.goBack()
        assertEquals(0L, nav.homeResetGeneration.value)
        nav.selectHome()
        assertEquals(1L, nav.homeResetGeneration.value)
    }
    @Test fun nestedPlaybackStillRefreshesContentWithoutLosingSpotlightDetailsReturn() {
        val nav = manager()
        nav.preserveSpotlightHomeReturn()
        nav.navigateTo(Destination.MediaItem(UUID(0, 1), BaseItemKind.MOVIE))
        nav.navigateTo(Destination.Playback(itemId = UUID(0, 1), positionMs = 0))
        nav.goBack()
        nav.goBack()
        assertEquals(0L, nav.homeResetGeneration.value)
        assertTrue(nav.consumeHomeRefresh())
    }
    @Test fun explicitHomeFromALibraryResetsWithoutRefreshingOrReplacingTheHomeEntry() {
        val nav = manager()
        val original = nav.backStack.first()
        nav.navigateToFromDrawer(Destination.Search())
        nav.selectHome()
        assertSame(original, nav.backStack.single())
        assertEquals(1L, nav.homeResetGeneration.value)
        assertEquals(1L, nav.homeSessionGeneration.value)
        assertFalse(nav.consumeHomeRefresh())
    }

    @Test fun ordinaryGoToHomeDoesNotImplyExplicitTabSelection() {
        val nav = manager()
        nav.navigateTo(Destination.Search())
        nav.goToHome()
        assertEquals(0L, nav.homeResetGeneration.value)
    }

    @Test fun backNeverForcesHomeWhenAnotherDestinationPrecedesTheCurrentOne() {
        val nav = manager()
        val movie = Destination.MediaItem(UUID(0, 7), BaseItemKind.MOVIE)
        nav.navigateTo(movie)
        nav.navigateTo(Destination.Search())
        nav.goBack()
        assertSame(movie, nav.backStack.last())
        assertEquals(0L, nav.homeResetGeneration.value)
    }

    @Test fun playbackReturnRetainsExistingContentRefresh() {
        val nav = manager()
        nav.navigateTo(Destination.Playback(itemId = UUID(0, 1), positionMs = 0))
        nav.goBack()
        assertTrue(nav.homeNeedsRefresh)
        assertEquals(0L, nav.homeResetGeneration.value)
    }
}
