package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.HomeDataService
import com.github.damontecres.wholphin.services.HomePageResolvedSettings
import com.github.damontecres.wholphin.services.HomePageSettingsSource
import com.github.damontecres.wholphin.services.HomeRowConfigDisplay
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.services.NavDrawerItemState
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/** Correctness checks for work cancellation and item identity; these are not frame-time benchmarks. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomePerformanceTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = mockk<ServerRepository>(relaxed = true)
    private val drawer = mockk<NavDrawerService>(relaxed = true)
    private val settings = mockk<HomeSettingsService>(relaxed = true)
    private val homeData = mockk<HomeDataService>(relaxed = true)
    private val backdrops = mockk<BackdropService>(relaxed = true)
    private val preferences = mockk<UserPreferencesService>(relaxed = true)
    private val dates = mockk<DatePlayedService>(relaxed = true)
    private val drawerState = MutableStateFlow(NavDrawerItemState())

    @Before
    fun setUp() {
        WholphinDispatchers.configure(dispatcher)
        every { repository.currentUserDtoFlow } returns MutableStateFlow<ServerUserConfig?>(null)
        every { settings.currentSettings } returns MutableStateFlow(HomePageResolvedSettings.EMPTY)
        every { drawer.state } returns drawerState
        coEvery { drawer.getAllUserLibraries(any(), any()) } returns emptyList()
        coEvery { preferences.getCurrent() } returns
            UserPreferences(AppPreferences.getDefaultInstance(), null)
    }

    @After
    fun tearDown() {
        store.clear()
        WholphinDispatchers.reset()
    }

    private fun createViewModel(): HomeViewModel =
        HomeViewModel(
            context = mockk<Context>(relaxed = true),
            navigationManager = mockk(relaxed = true),
            serverRepository = repository,
            serverReportService = mockk(relaxed = true),
            navDrawerService = drawer,
            homeSettingsService = settings,
            homeDataService = homeData,
            favoriteWatchManager = mockk(relaxed = true),
            datePlayedService = dates,
            backdropService = backdrops,
            userPreferencesService = preferences,
            mediaManagementService = mockk(relaxed = true),
            latestNextUpService = mockk(relaxed = true),
        ).also { store.put("home", it) }

    @Test
    fun rapidFocusChangesCancelObsoleteBackdropWork() = runTest(dispatcher) {
        val completed = mutableListOf<UUID>()
        coEvery { backdrops.submit(any<BaseItem>()) } coAnswers {
            val item = firstArg<BaseItem>()
            delay(500)
            completed += item.id
        }
        val viewModel = createViewModel()
        val first = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE))
        val last = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE))

        viewModel.updateBackdrop(first)
        runCurrent()
        viewModel.updateBackdrop(last)
        advanceUntilIdle()

        assertEquals(listOf(last.id), completed)
    }

    @Test
    fun cancellingHomeLoadCancelsItsInFlightRowRequests() = runTest(dispatcher) {
        var started = false
        var cancelled = false
        coEvery {
            homeData.fetchDataForRow(any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            started = true
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val viewModel = createViewModel()
        val userId = UUID.randomUUID()
        val user = mockk<ServerUserConfig>(relaxed = true) {
            every { id } returns userId
        }
        val resolved =
            HomePageResolvedSettings(
                userId,
                HomePageSettingsSource.UNSET,
                listOf(HomeRowConfigDisplay(0, StringStringProvider("Continue Watching"), HomeRowConfig.ContinueWatching())),
            )

        val loading = launch { viewModel.loadHomeRows(user, resolved) }
        runCurrent()
        assertTrue(started)
        loading.cancelAndJoin()
        runCurrent()

        assertTrue("The cancelled load must not leave row requests running", cancelled)
    }

    @Test
    fun musicStatusDoesNotEmitAnUnchangedLibraryList() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val emissions = mutableListOf<List<ServerNavDrawerItem>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.libraryItems.collect { emissions += it }
        }
        val library = mockk<ServerNavDrawerItem>(relaxed = true)
        drawerState.value = NavDrawerItemState(items = listOf(library))
        runCurrent()
        assertEquals(listOf(library), emissions.last())
        val count = emissions.size

        drawerState.value = drawerState.value.copy(nowPlayingEnabled = true, nowPlayingTitle = "Track")
        runCurrent()

        assertEquals(count, emissions.size)
    }

    @Test
    fun posterIdentitySurvivesReorderingAndDuplicateIdsRemainUnique() {
        val first = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE))
        val second = BaseItem(BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE))
        val before = homeItemKeys(listOf(first, second))
        val after = homeItemKeys(listOf(second, first))

        assertEquals(before[0], after[1])
        assertEquals(before[1], after[0])
        val duplicatesAndPlaceholders = homeItemKeys(listOf(first, null, first, null, second))
        assertEquals(5, duplicatesAndPlaceholders.toSet().size)
    }
}
