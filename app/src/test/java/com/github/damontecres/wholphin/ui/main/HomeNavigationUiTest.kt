package com.github.damontecres.wholphin.ui.main

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import dagger.hilt.android.testing.*
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.model.api.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class HomeNavigationUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val registration = object : TestWatcher() {
        override fun starting(description: Description?) {
            val app: Application = ApplicationProvider.getApplicationContext()
            shadowOf(app.packageManager).addOrUpdateActivity(ActivityInfo().apply {
                name = TestActivity::class.java.name
                packageName = app.packageName
            })
        }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<TestActivity>()
    private val navigation = NavigationManager()
    private val spotlight = mockk<SpotlightViewModel>(relaxed = true)
    private lateinit var homeState: LazyListState
    private var rows by mutableStateOf<List<HomeRowLoadingState>>(emptyList())

    @Before fun setup() { hilt.inject() }

    private fun render(withSpotlight: Boolean = true, recentTv: Boolean = false) {
        val movie = BaseItemDto(id = UUID(8, 1), type = BaseItemKind.MOVIE, name = "Spotlight A")
        val movies = if (withSpotlight) listOf(movie, movie.copy(id = UUID(8, 2), name = "Spotlight B")) else emptyList()
        every { spotlight.state } returns MutableStateFlow(SpotlightState(resolved = true, movies = movies))
        every { spotlight.details(any()) } answers {
            navigation.navigateTo(Destination.MediaItem(firstArg<BaseItemDto>().id, BaseItemKind.MOVIE))
        }
        rows = List(4) { row ->
            HomeRowLoadingState.Success(StringStringProvider("Row $row"), List(24) { index ->
                val dto = BaseItemDto(id = UUID(row.toLong() + 1, index.toLong() + 1),
                    type = if (recentTv && row == 1) BaseItemKind.EPISODE else BaseItemKind.MOVIE,
                    seriesId = if (recentTv && row == 1) UUID(90, 1) else null,
                    seasonId = if (recentTv && row == 1) UUID(91, 3) else null,
                    parentIndexNumber = 3, indexNumber = index + 1)
                mockk<BaseItem>(relaxed = true) {
                    every { id } returns dto.id
                    every { type } returns dto.type
                    every { data } returns dto
                    every { title } returns "Row $row movie $index"
                    every { name } returns "Row $row movie $index"
                    every { imageUrlOverride } returns null
                }
            }, rowType = if (row == 0) HomeRowConfig.ContinueWatching() else HomeRowConfig.RecentlyAdded(UUID(3, row.toLong())))
        }
        val images = mockk<ImageUrlService>(relaxed = true)
        every { images.getItemImageUrl(any<BaseItem>(), any(), any(), any(), any()) } returns null
        compose.setContent {
            val generation by navigation.homeResetGeneration.collectAsState()
            val homeFocus = remember { FocusRequester() }
            MaterialTheme {
                CompositionLocalProvider(LocalImageUrlService provides images, LocalJellyLuHomeCards provides true,
                    LocalJellyLuRailFocus provides homeFocus) {
                    Column(Modifier.fillMaxSize()) {
                        Button(onClick = { navigation.selectHome() }, modifier = Modifier.focusRequester(homeFocus)) {
                            Text("Home tab")
                        }
                        NavDisplay(
                            backStack = navigation.backStack,
                            onBack = { navigation.goBack() },
                            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                            modifier = Modifier.weight(1f),
                            entryProvider = { destination ->
                                NavEntry(destination) {
                                    if (destination is Destination.Home) {
                                        var position by rememberPosition()
                                        val list = rememberLazyListState(cacheWindow = remember {
                                            LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 2f)
                                        })
                                        SideEffect { homeState = list }
                                        HomePageContent(
                                            homeRows = rows, position = RowColumn(-1, -1), getPosition = { position },
                                            onFocusPosition = { position = it },
                                            onClickItem = { selected, item ->
                                                position = selected
                                                navigation.navigateTo(if (recentTv) homeItemDestination(
                                                    (rows.getOrNull(selected.row) as? HomeRowLoadingState.Success)?.rowType, item,
                                                ) else Destination.MediaItem(item.id, item.type))
                                            },
                                            onLongClickItem = { _, _ -> }, onClickPlay = { _, _ -> },
                                            showClock = false, onUpdateBackdrop = {}, showLogo = false, showViewMore = false,
                                            listState = list, jellyLuHome = true, classicHome = true,
                                            spotlightAvailable = withSpotlight, spotlightPending = false,
                                            resetGeneration = generation,
                                            spotlightContent = { group, row ->
                                                JellyLuSpotlight(spotlight, group, row) { position = RowColumn(-1, it) }
                                            },
                                        )
                                    } else {
                                        Button(onClick = { navigation.goBack() }) { Text("Other destination") }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun key(key: Key) = compose.onNode(isFocused()).performKeyInput { pressKey(key) }
    private fun title(row: Int, index: Int) = hasContentDescription("Row $row movie $index") or hasText("Row $row movie $index")
    private fun card(row: Int, index: Int) = compose.onNode(title(row, index))
    private fun open(row: Int = 1, index: Int = 15) {
        repeat(row + 1) { key(Key.DirectionDown) }
        repeat(index) { key(Key.DirectionRight) }
        card(row, index).assertIsFocused()
        key(Key.DirectionCenter)
        compose.onNodeWithText("Other destination").assertExists()
    }
    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun horizontal(row: Int, index: Int): Float = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and
            hasAnyDescendant(title(row, index)), useUnmergedTree = true,
    ).fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()

    @Test fun row15DetailsBackRestoresExactFocusAndBothScrollOffsets() {
        render()
        repeat(2) { key(Key.DirectionDown) }
        repeat(15) { key(Key.DirectionRight) }
        card(1, 15).assertIsFocused()
        val horizontal = horizontal(1, 15)
        val vertical = compose.runOnIdle { homeState.firstVisibleItemIndex to homeState.firstVisibleItemScrollOffset }
        val bounds = card(1, 15).fetchSemanticsNode().boundsInRoot
        key(Key.DirectionCenter)
        compose.onNodeWithText("Other destination").assertExists()
        back()
        card(1, 15).assertIsDisplayed().assertIsFocused()
        assertEquals(horizontal, horizontal(1, 15))
        assertEquals(bounds, card(1, 15).fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(vertical, homeState.firstVisibleItemIndex to homeState.firstVisibleItemScrollOffset) }
        assertEquals(0L, navigation.homeResetGeneration.value)
        verify(exactly = 0) { spotlight.load(any()) }
    }

    @Test fun backPreservesTheOtherRowsAndBackFromALibraryRestoresTheExistingEntry() {
        render()
        key(Key.DirectionDown)
        repeat(6) { key(Key.DirectionRight) }
        card(0, 6).assertIsFocused()
        key(Key.DirectionDown)
        repeat(15) { key(Key.DirectionRight) }
        val entry = navigation.backStack.first()
        compose.runOnIdle { navigation.navigateToFromDrawer(Destination.Search()) }
        compose.onNodeWithText("Other destination").assertExists()
        back()
        card(1, 15).assertIsFocused()
        assertSame(entry, navigation.backStack.single())
        key(Key.DirectionUp)
        card(0, 6).assertIsFocused()
        assertEquals(0L, navigation.homeResetGeneration.value)
    }

    private fun explicitReset(fromLibrary: Boolean) {
        render()
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        key(Key.DirectionCenter)
        repeat(4) {
            key(Key.DirectionDown)
            repeat(6) { key(Key.DirectionRight) }
        }
        if (fromLibrary) {
            compose.runOnIdle { navigation.navigateToFromDrawer(Destination.Search()) }
            compose.onNodeWithText("Other destination").assertExists()
        }
        compose.onNodeWithText("Home tab").requestFocus()
        key(Key.DirectionCenter)
        try {
            compose.onNodeWithText("Play").assertIsFocused()
        } catch (failure: AssertionError) {
            val actual = compose.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString { it.config.toString() }
            throw AssertionError("Explicit Home must focus Play. Actual focused semantics: $actual", failure)
        }
        compose.onNodeWithText("Spotlight B").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, homeState.firstVisibleItemIndex)
            assertEquals(0, homeState.firstVisibleItemScrollOffset)
        }
        repeat(4) { row ->
            key(Key.DirectionDown)
            try {
                card(row, 0).assertIsFocused()
            } catch (failure: AssertionError) {
                val actual = compose.onAllNodes(isFocused()).fetchSemanticsNodes().joinToString { it.config.toString() }
                throw AssertionError("Explicit Home must reset row $row to its first card. Actual focus: $actual", failure)
            }
        }
        verify(exactly = 0) { spotlight.load(any()) }
    }
    @Test fun explicitHomeFromAnotherDestinationResetsEveryRowIncludingOffscreen() = explicitReset(true)
    @Test fun explicitHomeReselectionResetsEveryRowWithoutReshufflingSpotlight() = explicitReset(false)

    @Test fun removedFocusedItemFallsBackToTheNextValidCard() {
        render()
        open()
        compose.runOnIdle {
            rows = rows.mapIndexed { row, value ->
                if (row == 1) (value as HomeRowLoadingState.Success).copy(items = value.items.filterIndexed { index, _ -> index != 15 })
                else value
            }
        }
        back()
        card(1, 16).assertIsDisplayed().assertIsFocused()
    }

    @Test fun reorderedFocusedItemIsRestoredByIdentityAtItsNewIndex() {
        render()
        open()
        compose.runOnIdle {
            val row = rows[1] as HomeRowLoadingState.Success
            val reordered = row.items.toMutableList().apply { add(1, removeAt(15)) }
            rows = rows.toMutableList().apply { set(1, row.copy(items = reordered)) }
        }
        back()
        card(1, 15).assertIsDisplayed().assertIsFocused()
        key(Key.DirectionRight)
        card(1, 1).assertIsFocused()
    }

    @Test fun emptiedRowFallsBackToANearbyUsableRow() {
        render()
        open()
        compose.runOnIdle { rows = rows.toMutableList().apply {
            val row = get(1) as HomeRowLoadingState.Success
            set(1, row.copy(items = emptyList()))
        } }
        back()
        card(0, 0).assertIsDisplayed().assertIsFocused()
    }

    @Test fun noRowsAndUnavailableSpotlightReturnFocusToHomeTab() {
        render(withSpotlight = false)
        card(0, 0).assertIsFocused()
        repeat(5) { key(Key.DirectionRight) }
        key(Key.DirectionCenter)
        compose.onNodeWithText("Other destination").assertExists()
        compose.runOnIdle { rows = emptyList() }
        back()
        compose.onNodeWithText("Home tab").assertIsFocused()
    }

    @Test fun recentTvOverviewBackRestoresExactCardFifteenWithoutHomeReset() {
        render(recentTv = true)
        repeat(2) { key(Key.DirectionDown) }
        repeat(15) { key(Key.DirectionRight) }
        card(1, 15).assertIsFocused()
        val offset = horizontal(1, 15)
        val vertical = compose.runOnIdle { homeState.firstVisibleItemIndex to homeState.firstVisibleItemScrollOffset }
        key(Key.DirectionCenter)
        compose.runOnIdle {
            val route = navigation.backStack.last() as Destination.MediaItem
            assertEquals(UUID(90, 1), route.itemId)
            assertNull(route.recentEpisode)
        }
        back()
        card(1, 15).assertIsFocused().assertIsDisplayed()
        assertEquals(offset, horizontal(1, 15))
        compose.runOnIdle { assertEquals(vertical, homeState.firstVisibleItemIndex to homeState.firstVisibleItemScrollOffset) }
        assertEquals(0L, navigation.homeResetGeneration.value)
        verify(exactly = 0) { spotlight.load(any()) }
    }

    @Test fun continueWatchingDetailsBackAlsoRestoresItsCard() {
        render()
        open(row = 0, index = 9)
        back()
        card(0, 9).assertIsDisplayed().assertIsFocused()
    }
}
