package com.github.damontecres.wholphin.ui.discovery

import androidx.lifecycle.ViewModelStore
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MovieExploreSessionTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val server = mockk<ServerRepository>()
    private val repository = mockk<MovieExploreRepository>()
    private val navigation = mockk<NavigationManager>(relaxed = true)
    private val host = mockk<JellyfinServer> { every { id } returns UUID(1, 1) }
    private val user = mockk<JellyfinUser> { every { id } returns UUID(2, 2) }
    private val account = MutableStateFlow<CurrentUser?>(CurrentUser(host, user))
    private val scope = MovieExploreScope(host.id, user.id, UUID(3, 3))
    private val facet = MovieFacet(UUID(4, 4), "Studio", MovieFacetKind.STUDIO)

    @Before fun setup() {
        WholphinDispatchers.configure(dispatcher)
        every { server.current } returns account
        every { server.currentUser } answers { account.value?.user }
        every { server.currentServer } answers { account.value?.server }
        coEvery { repository.load(any()) } answers {
            MovieExploreState(firstArg(), studios = listOf(facet))
        }
    }
    @After fun teardown() { store.clear(); WholphinDispatchers.reset() }
    private fun create() = MovieExploreViewModel(server, repository, navigation).also { store.put("explore", it) }

    @Test fun focusAndBackReentryDoNotRequestMetadataOrTriggerNavigation() = runTest(dispatcher) {
        val vm = create()
        vm.load(scope.library)
        advanceUntilIdle()
        repeat(24) { vm.load(scope.library); restoredFacetIndex(vm.state.value.studios, facet.key, 0) }
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.load(scope) }
        verify(exactly = 0) { navigation.navigateTo(any()) }
        vm.open(facet)
        verify(exactly = 1) { navigation.navigateTo(facet.destination(scope)) }
    }

    @Test fun foreignOrRemovedFacetCannotNavigateAndSignOutClearsCachedViewState() = runTest(dispatcher) {
        val vm = create()
        vm.load(scope.library)
        advanceUntilIdle()
        vm.open(facet.copy(id = UUID(99, 99)))
        verify(exactly = 0) { navigation.navigateTo(any()) }
        account.value = null
        advanceUntilIdle()
        assertNull(vm.state.value.scope)
        assertTrue(vm.state.value.studios.isEmpty())
        vm.open(facet)
        verify(exactly = 0) { navigation.navigateTo(any()) }
    }

    @Test fun switchingMovieLibrariesUsesSeparateScopeWithoutMutatingPreferences() = runTest(dispatcher) {
        val vm = create()
        vm.load(scope.library)
        advanceUntilIdle()
        val kids = UUID(8, 8)
        vm.load(kids)
        advanceUntilIdle()
        assertEquals(kids, vm.state.value.scope!!.library)
        coVerify(exactly = 1) { repository.load(scope.copy(library = kids)) }
        vm.open(facet)
        verify { navigation.navigateTo(facet.destination(scope.copy(library = kids))) }
    }
}
