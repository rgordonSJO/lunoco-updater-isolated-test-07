package com.github.damontecres.wholphin.services

import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.github.damontecres.wholphin.ui.nav.Destination
import org.acra.ACRA
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages navigating between pages and manages the app's back stack
 */
@Singleton
class NavigationManager
    @Inject
    constructor() {
        var backStack: MutableList<Destination> = NavBackStack(Destination.Home())
        private val _homeResetGeneration = MutableStateFlow(0L)
        val homeResetGeneration = _homeResetGeneration.asStateFlow()
        private val _homeSessionGeneration = MutableStateFlow(0L)
        val homeSessionGeneration = _homeSessionGeneration.asStateFlow()
        private var playbackRefreshPending = false
        var homeNeedsRefresh: Boolean = true
            private set

        /** Skip the navigation-only refresh once; later app foregrounds retain normal refresh. */
        fun consumeHomeRefresh(): Boolean = homeNeedsRefresh.also { homeNeedsRefresh = true }

        private var foregroundDay: Long? = null

        /** Called on actual Activity foreground, never on a NavEntry's Details/Back lifecycle. */
        fun onApplicationForeground(epochDay: Long) {
            val previous = foregroundDay
            foregroundDay = epochDay
            if (previous != null && previous != epochDay && backStack.lastOrNull() is Destination.Home) {
                // A new daily Spotlight session must not reset browsing/scroll positions.
                homeNeedsRefresh = true
                _homeSessionGeneration.value++
            }
        }

        /** Compatibility marker: all Back returns now preserve the existing Home entry. */
        fun preserveSpotlightHomeReturn() = Unit

        /** Only an explicit in-app Home selection resets Home's presentation. */
        fun selectHome() = goToHome(resetPresentation = true)

        private fun resetHomePresentation(refresh: Boolean) {
            homeNeedsRefresh = refresh
            _homeResetGeneration.value++
            _homeSessionGeneration.value++
        }

        /**
         * Go to the specified [com.github.damontecres.wholphin.ui.nav.Destination]
         */
        fun navigateTo(destination: Destination) {
            backStack.add(destination)
            log()
        }

        /**
         * Go to the specified [Destination], but reset the back stack to Home first
         */
        fun navigateToFromDrawer(destination: Destination) {
            goToHome(resetPresentation = false)
            backStack.add(destination)
            log()
        }

        /**
         * Go to the previous page
         */
        fun goBack() {
            synchronized(this) {
                if (backStack.size > 1) {
                    val leaving = backStack.removeLastOrNull()
                    if (leaving is Destination.Playback) playbackRefreshPending = true
                    if (backStack.lastOrNull() is Destination.Home) {
                        homeNeedsRefresh = playbackRefreshPending
                        playbackRefreshPending = false
                    }
                }
            }
            log()
        }

        /**
         * Go all the way back to the home page
         */
        fun goToHome(resetPresentation: Boolean = false) {
            val refresh = playbackRefreshPending || backStack.any { it is Destination.Playback }
            if (resetPresentation) {
                resetHomePresentation(refresh)
                playbackRefreshPending = false
            }
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
            if (backStack[0] !is Destination.Home) {
                backStack[0] = Destination.Home()
            }
            log()
        }

        /**
         * Go all the way back to the home page, and reload it from scratch
         */
        fun reloadHome() {
            goToHome()
            val id = (backStack[0] as Destination.Home).id + 1
            backStack[0] = Destination.Home(id)
            log()
        }

        /**
         * Resets the backstack to the specified destination
         */
        fun replace(destination: Destination) {
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
            if (backStack.isEmpty()) {
                backStack.add(0, destination)
            } else {
                backStack[0] = destination
            }
            log()
        }

        private fun log() {
            val dest = backStack.lastOrNull().toString()
            Timber.i("Current Destination: %s", dest)
            ACRA.errorReporter.putCustomData("destination", dest)
        }
    }
