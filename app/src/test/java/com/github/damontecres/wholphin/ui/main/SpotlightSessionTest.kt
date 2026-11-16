package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.ViewModelStore
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.jellyfin.sdk.model.api.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SpotlightSessionTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val server = mockk<ServerRepository>()
    private val repository = mockk<SpotlightRepository>()
    private val navigation = mockk<NavigationManager>(relaxed = true)
    private val serverId = UUID(1, 1)
    private val userId = UUID(2, 2)
    private val libraryId = UUID(3, 3)
    private val serverModel = mockk<JellyfinServer> { every { id } returns serverId }
    private val userModel = mockk<JellyfinUser> { every { id } returns userId }
    private val account = MutableStateFlow<CurrentUser?>(CurrentUser(serverModel, userModel))
    private val primary = ServerNavDrawerItem(libraryId, "Movies",
        Destination.MediaItem(libraryId, BaseItemKind.COLLECTION_FOLDER, CollectionType.MOVIES), CollectionType.MOVIES)

    @Before fun setUp() {
        WholphinDispatchers.configure(dispatcher)
        every { server.current } returns account
        every { server.currentServer } answers { account.value?.server }
        every { server.currentUser } answers { account.value?.user }
    }
    @After fun tearDown() { store.clear(); WholphinDispatchers.reset() }

    private fun create(): SpotlightViewModel = SpotlightViewModel(server, repository, navigation,
        mockk<Context>(relaxed = true)).also { store.put("spotlight", it) }

    @Test fun repeatedHomeOrFocusDrivenRecompositionCannotRefetchThePool() = runTest(dispatcher) {
        val items = listOf(BaseItemDto(id = UUID(0, 1), type = BaseItemKind.MOVIE))
        coEvery { repository.load(any(), any()) } returns items
        val vm = create()
        runCurrent()
        vm.load(listOf(primary))
        advanceUntilIdle()
        val before = vm.state.value
        repeat(24) {
            spotlightPageIndex(0, 5, 1)
            vm.load(listOf(primary))
        }
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.load(SpotlightScope(serverId, userId, libraryId), any()) }
        assertEquals(before, vm.state.value)
        assertEquals(items, vm.state.value.movies)
    }

    @Test fun freshSessionRefreshesOnlyOnANewLocalCalendarDay() = runTest(dispatcher) {
        val items = listOf(BaseItemDto(id = UUID(0, 1), type = BaseItemKind.MOVIE))
        coEvery { repository.load(any(), any()) } returns items
        val vm = create()
        vm.load(listOf(primary), homeSession = 0, epochDay = 100)
        advanceUntilIdle()
        val before = vm.state.value
        vm.load(listOf(primary), homeSession = 1, epochDay = 100)
        advanceUntilIdle()
        assertEquals(before, vm.state.value)
        coVerify(exactly = 1) { repository.load(any(), any()) }
        // Midnight during the SAME browsing/Details session must not change the snapshot.
        vm.load(listOf(primary), homeSession = 1, epochDay = 101)
        advanceUntilIdle()
        assertEquals(before, vm.state.value)
        // A genuine new session on the new day does refresh the bounded pool once.
        vm.load(listOf(primary), homeSession = 2, epochDay = 101)
        advanceUntilIdle()
        assertEquals(101L, vm.state.value.selectionDay)
        coVerify(exactly = 1) { repository.load(any(), 100) }
        coVerify(exactly = 1) { repository.load(any(), 101) }
    }

    @Test fun detailsAcrossMidnightKeepsSelectionAndDoesNotRefetch() = runTest(dispatcher) {
        val item = BaseItemDto(id = UUID(0, 1), type = BaseItemKind.MOVIE)
        coEvery { repository.load(any(), any()) } returns listOf(item)
        val vm = create()
        vm.load(listOf(primary), homeSession = 7, epochDay = 100)
        advanceUntilIdle()
        vm.details(item)
        vm.load(listOf(primary), homeSession = 7, epochDay = 101)
        advanceUntilIdle()
        assertEquals(100L, vm.state.value.selectionDay)
        assertEquals(listOf(item), vm.state.value.movies)
        verify(exactly = 1) { navigation.preserveSpotlightHomeReturn() }
        coVerify(exactly = 1) { repository.load(any(), any()) }
    }

    @Test fun newerDayCancelsAnOlderInFlightSelection() = runTest(dispatcher) {
        val old = BaseItemDto(id = UUID(0, 1), type = BaseItemKind.MOVIE)
        val fresh = old.copy(id = UUID(0, 2))
        coEvery { repository.load(any(), 100) } coAnswers { kotlinx.coroutines.delay(1000); listOf(old) }
        coEvery { repository.load(any(), 101) } returns listOf(fresh)
        val vm = create()
        vm.load(listOf(primary), homeSession = 0, epochDay = 100)
        runCurrent()
        vm.load(listOf(primary), homeSession = 1, epochDay = 101)
        advanceUntilIdle()
        assertEquals(101L, vm.state.value.selectionDay)
        assertEquals(listOf(fresh), vm.state.value.movies)
    }

    @Test fun unavailablePrimaryDoesNotFallBackToKidsOrMakeRequests() = runTest(dispatcher) {
        val kidsId = UUID(0, 9)
        val kids = ServerNavDrawerItem(kidsId, "Kids Movies",
            Destination.MediaItem(kidsId, BaseItemKind.COLLECTION_FOLDER, CollectionType.MOVIES), CollectionType.MOVIES)
        val vm = create()
        vm.load(listOf(kids))
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.load(any(), any()) }
        assertTrue(vm.state.value.movies.isEmpty())
    }

    @Test fun accountChangeImmediatelyRemovesTheOldUsersSelection() = runTest(dispatcher) {
        coEvery { repository.load(any(), any()) } returns listOf(BaseItemDto(id = UUID(0, 1), type = BaseItemKind.MOVIE))
        val vm = create()
        runCurrent()
        vm.load(listOf(primary))
        advanceUntilIdle()
        assertFalse(vm.state.value.movies.isEmpty())
        account.value = null
        advanceUntilIdle()
        assertTrue(vm.state.value.movies.isEmpty())
        assertNull(vm.state.value.scope)
    }

    @Test fun detailsUseTheSelectedMovieButRejectForeignItems() = runTest(dispatcher) {
        val selected = BaseItemDto(id = UUID(0, 1), type = BaseItemKind.MOVIE)
        coEvery { repository.load(any(), any()) } returns listOf(selected)
        val vm = create()
        vm.load(listOf(primary))
        advanceUntilIdle()
        vm.details(selected)
        vm.details(selected.copy(id = UUID(0, 99)))
        verify(exactly = 1) { navigation.navigateTo(Destination.MediaItem(selected.id, BaseItemKind.MOVIE)) }
        verify(exactly = 1) { navigation.navigateTo(any()) }
    }
}
