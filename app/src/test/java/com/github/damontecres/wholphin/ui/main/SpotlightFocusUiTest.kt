package com.github.damontecres.wholphin.ui.main

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.lazy.LazyListState
import androidx.activity.compose.BackHandler
import com.github.damontecres.wholphin.ui.rememberPosition
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.nav.JellyLuHomeBackFocus
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.model.api.*
import org.junit.Assert.assertEquals
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
class SpotlightFocusUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val activityRegistration = object : TestWatcher() {
        override fun starting(description: Description?) {
            val app: Application = ApplicationProvider.getApplicationContext()
            shadowOf(app.packageManager).addOrUpdateActivity(ActivityInfo().apply {
                name = TestActivity::class.java.name
                packageName = app.packageName
            })
        }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<TestActivity>()
    private val viewModel = mockk<SpotlightViewModel>(relaxed = true)
    private val first = BaseItemDto(id = UUID(0, 1), type = BaseItemKind.MOVIE, name = "Feature A")
    private val second = first.copy(id = UUID(0, 2), name = "Feature B")

    @Before fun setUp() { hilt.inject() }

    private lateinit var homeListState: LazyListState
    private val homeVisible = mutableStateOf(true)
    private val resetGeneration = mutableLongStateOf(0L)
    private var renderedRows by mutableStateOf<List<HomeRowLoadingState>>(emptyList())

    private fun render(initial: SpotlightState = SpotlightState(
        scope = SpotlightScope(UUID(1, 1), UUID(2, 2), UUID(3, 3)), movies = listOf(first, second),
    ), rowCount: Int = 1, rowSize: Int = 1,
        rotationLifecycle: androidx.lifecycle.LifecycleOwner? = null,
        artworkLoader: coil3.ImageLoader? = null): MutableStateFlow<SpotlightState> {
        val state = MutableStateFlow(initial)
        every { viewModel.state } returns state
        val imageService = mockk<ImageUrlService>(relaxed = true)
        val dto = BaseItemDto(id = UUID(0, 3), type = BaseItemKind.MOVIE)
        val item = mockk<BaseItem>(relaxed = true)
        every { item.id } returns dto.id
        every { item.type } returns dto.type
        every { item.data } returns dto
        every { item.title } returns "Resume movie"
        every { item.name } returns "Resume movie"
        val rows = List(rowCount) { index ->
            HomeRowLoadingState.Success(StringStringProvider(if (index == 0) "Continue Watching" else "Row $index"),
                if (rowSize == 1) listOf(item) else List(rowSize) { column ->
                    mockk<BaseItem>(relaxed = true) {
                        every { id } returns UUID(index.toLong() + 1, column.toLong() + 1)
                        every { type } returns BaseItemKind.MOVIE
                        every { data } returns dto.copy(id = UUID(index.toLong() + 1, column.toLong() + 1))
                        every { title } returns "Row $index movie $column"
                        every { name } returns "Row $index movie $column"
                    }
                }, rowType = HomeRowConfig.ContinueWatching())
        }
        renderedRows = rows
        every { viewModel.details(any()) } answers { homeVisible.value = false }
        compose.setContent {
            val current by state.collectAsState()
            val holder = rememberSaveableStateHolder()
            val homeFocus = remember { FocusRequester() }
            var navigationFocused by remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalImageUrlService provides imageService,
                LocalSpotlightImageLoader provides artworkLoader,
                LocalJellyLuHomeCards provides true, LocalJellyLuRailFocus provides homeFocus) {
                MaterialTheme {
                    Column(Modifier.fillMaxSize()) {
                        Button(onClick = {}, modifier = Modifier.focusRequester(homeFocus)
                            .onFocusChanged { navigationFocused = it.isFocused }) { Text("Home navigation") }
                        JellyLuHomeBackFocus(!navigationFocused, homeFocus)
                        if (!homeVisible.value) {
                            BackHandler { homeVisible.value = true }
                            Button(onClick = { homeVisible.value = true }) { Text("Movie details destination") }
                        } else holder.SaveableStateProvider("home") {
                        var position by rememberPosition()
                        val listState = rememberLazyListState(cacheWindow = remember {
                            LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 2f)
                        })
                        SideEffect { homeListState = listState }
                        HomePageContent(
                            homeRows = renderedRows, listState = listState, resetGeneration = resetGeneration.longValue, position = RowColumn(-1, -1), getPosition = { position },
                            onFocusPosition = { position = it }, onClickItem = { _, _ -> },
                            onLongClickItem = { _, _ -> }, onClickPlay = { _, _ -> },
                            showClock = false, onUpdateBackdrop = {}, showLogo = false, showViewMore = false,
                            jellyLuHome = true, classicHome = true, spotlightAvailable = current.movies.isNotEmpty(),
                            spotlightPending = current.loading || !current.resolved,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            spotlightContent = { focus, rowFocus ->
                                CompositionLocalProvider(LocalHomeResetGeneration provides resetGeneration.longValue) {
                                    val owner = rotationLifecycle ?: androidx.lifecycle.compose.LocalLifecycleOwner.current
                                    CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                                        JellyLuSpotlight(viewModel, focus, rowFocus) { position = RowColumn(-1, it) }
                                    }
                                }
                            },
                        )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        // Initial focus must be established by the actual Home, not forced by the test.
        return state
    }

    private fun advanceRotationTime(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun traceRotation(label: String) {
        val pages = compose.onAllNodes(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription] }
        println("ROTATION09C $label clock=${compose.mainClock.currentTime} pages=$pages")
    }

    @Test fun pendingArtworkDetailsActsOnVisibleMovieAndBackKeepsIt() {
        val a = first.copy(backdropImageTags = listOf("a"))
        val b = second.copy(backdropImageTags = listOf("b"))
        val loader = mockk<coil3.ImageLoader>()
        val pending = kotlinx.coroutines.CompletableDeferred<coil3.request.ImageResult>()
        var loads = 0
        coEvery { loader.execute(any()) } coAnswers {
            if (++loads == 1) mockk<coil3.request.ErrorResult>() else pending.await()
        }
        render(SpotlightState(scope = SpotlightScope(UUID(1, 1), UUID(2, 2), UUID(3, 3)),
            movies = listOf(a, b)), artworkLoader = loader)
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Feature A").assertExists()
        compose.onNodeWithText("Details").requestFocus()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        verify(exactly = 1) { viewModel.details(a) }
        verify(exactly = 0) { viewModel.details(b) }
        compose.onNodeWithText("Movie details destination").assertExists()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Feature A").assertExists()
        compose.onNodeWithText("Details").assertIsFocused()
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @Test fun enteringControlsCancelsUnseenAutomaticArtworkWithoutReloadingVisibleImage() {
        val a = first.copy(backdropImageTags = listOf("a"))
        val b = second.copy(backdropImageTags = listOf("b"))
        val loader = mockk<coil3.ImageLoader>()
        val pending = kotlinx.coroutines.CompletableDeferred<coil3.request.ImageResult>()
        var loads = 0
        coEvery { loader.execute(any()) } coAnswers {
            if (++loads == 1) mockk<coil3.request.ErrorResult>() else pending.await()
        }
        render(SpotlightState(scope = SpotlightScope(UUID(1, 1), UUID(2, 2), UUID(3, 3)),
            movies = listOf(a, b)), artworkLoader = loader)
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.autoAdvance = false
        advanceRotationTime(10_100)
        compose.onNodeWithText("Feature A").assertExists()
        coVerify(exactly = 2) { loader.execute(any()) }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        advanceRotationTime(100)
        compose.runOnIdle { pending.complete(mockk<coil3.request.ErrorResult>()) }
        advanceRotationTime(11_000)
        compose.onNodeWithText("Feature A").assertExists()
        compose.onNodeWithText("Play").assertIsFocused()
        coVerify(exactly = 2) { loader.execute(any()) }
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @Test fun explicitManualPageCanFinishAfterLeavingAndReenteringControls() {
        val a = first.copy(backdropImageTags = listOf("a"))
        val b = second.copy(backdropImageTags = listOf("b"))
        val loader = mockk<coil3.ImageLoader>()
        val pending = kotlinx.coroutines.CompletableDeferred<coil3.request.ImageResult>()
        var loads = 0
        coEvery { loader.execute(any()) } coAnswers {
            if (++loads == 1) mockk<coil3.request.ErrorResult>() else pending.await()
        }
        render(SpotlightState(scope = SpotlightScope(UUID(1, 1), UUID(2, 2), UUID(3, 3)),
            movies = listOf(a, b)), artworkLoader = loader)
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Feature A").assertExists()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithContentDescription("Next Spotlight movie").assertIsFocused()
        compose.runOnIdle { pending.complete(mockk<coil3.request.ErrorResult>()) }
        advanceRotationTime(100)
        compose.onNodeWithText("Feature B").assertExists()
        compose.onNodeWithContentDescription("Next Spotlight movie").assertIsFocused()
        coVerify(exactly = 2) { loader.execute(any()) }
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @Test fun automaticPagingWrapsWithoutMovingRowFocusOrFetchingData() {
        render(rowSize = 20)
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        repeat(3) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) } }
        compose.mainClock.autoAdvance = false
        val before = compose.runOnIdle { homeListState.firstVisibleItemIndex to homeListState.firstVisibleItemScrollOffset }
        advanceRotationTime(10_100)
        compose.onNodeWithText("Feature B").assertExists()
        compose.onNodeWithContentDescription("Row 0 movie 3").assertIsFocused()
        advanceRotationTime(10_100)
        compose.onNodeWithText("Feature A").assertExists()
        compose.onNodeWithContentDescription("Row 0 movie 3").assertIsFocused()
        compose.runOnIdle { assertEquals(before, homeListState.firstVisibleItemIndex to homeListState.firstVisibleItemScrollOffset) }
        verify(exactly = 0) { viewModel.load(any()) }
        verify(exactly = 0) { viewModel.play(any()) }
        verify(exactly = 0) { viewModel.details(any()) }
    }

    @Test fun spotlightControlFocusPausesAndLeavingRestartsAFullInterval() {
        render()
        compose.mainClock.autoAdvance = false
        advanceRotationTime(15_100)
        compose.onNodeWithText("Feature A").assertIsDisplayed()
        compose.onNodeWithText("Play").assertIsFocused()
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        advanceRotationTime(10_100)
        compose.onNodeWithText("Feature B").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next Spotlight movie").assertIsFocused()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        advanceRotationTime(9_800)
        compose.onNodeWithText("Feature B").assertExists()
        advanceRotationTime(400)
        compose.onNodeWithText("Feature A").assertExists()
        compose.onNodeWithContentDescription("Resume movie").assertIsFocused()
    }

    @Test fun manualPagingRestartsTheUnfocusedTimerAndActionsUseCurrentMovie() {
        render()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.autoAdvance = false
        advanceRotationTime(9_000)
        traceRotation("before manual")
        // Accessibility activation invokes the callback without the touch focus transfer.
        compose.onNodeWithContentDescription("Next Spotlight movie")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        traceRotation("after manual action")
        // Allow state recomposition and the following layout frame before timing the new page.
        advanceRotationTime(100)
        traceRotation("after manual render")
        compose.onNodeWithContentDescription("Resume movie").assertIsFocused()
        compose.onNodeWithText("Feature B").assertExists()
        advanceRotationTime(9_800)
        compose.onNodeWithText("Feature B").assertExists()
        advanceRotationTime(400)
        compose.onNodeWithText("Feature A").assertExists()
        compose.onNodeWithText("Play").requestFocus()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionCenter) }
        verify(exactly = 1) { viewModel.play(first) }
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @Test fun manualPagingCannotBeOverwrittenByAnAlreadyWaitingOldTick() {
        render()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.autoAdvance = false
        advanceRotationTime(9_000)
        compose.onNodeWithContentDescription("Next Spotlight movie")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        // Deliberately cross the old deadline without first flushing a composition frame.
        advanceRotationTime(1_200)
        compose.onNodeWithText("Feature B").assertExists()
        compose.onNodeWithContentDescription("Resume movie").assertIsFocused()
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @Test fun backgroundedHomeStopsAndResumeStartsAFreshInterval() {
        val owner = object : androidx.lifecycle.LifecycleOwner {
            override val lifecycle = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply {
                currentState = androidx.lifecycle.Lifecycle.State.RESUMED
            }
        }
        render(rotationLifecycle = owner)
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.autoAdvance = false
        advanceRotationTime(9_000)
        compose.runOnUiThread { owner.lifecycle.currentState = androidx.lifecycle.Lifecycle.State.CREATED }
        advanceRotationTime(20_000)
        compose.onNodeWithText("Feature A").assertExists()
        compose.runOnUiThread { owner.lifecycle.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        advanceRotationTime(9_800)
        compose.onNodeWithText("Feature A").assertExists()
        advanceRotationTime(400)
        compose.onNodeWithText("Feature B").assertExists()
        compose.onNodeWithContentDescription("Resume movie").assertIsFocused()
    }

    @Test fun longDetailsVisitRetainsSelectedMovieAndDetailsFocus() {
        render()
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Details").requestFocus()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Movie details destination").assertExists()
        compose.mainClock.autoAdvance = false
        advanceRotationTime(30_000)
        // Restore with normal frame delivery, as in the existing Details/Back regression.
        compose.mainClock.autoAdvance = true
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Details").assertIsFocused()
        compose.mainClock.autoAdvance = false
        advanceRotationTime(10_000)
        compose.onNodeWithText("Feature B").assertIsDisplayed()
        compose.onNodeWithText("Details").assertIsFocused()
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @Test fun offscreenSpotlightDoesNotRotateWhileBrowsingDeepRows() {
        render(rowCount = 4)
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        repeat(4) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) } }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { org.junit.Assert.assertTrue(homeListState.firstVisibleItemIndex > 0) }
        advanceRotationTime(20_000)
        repeat(4) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) } }
        advanceRotationTime(300)
        compose.onNodeWithText("Feature B").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next Spotlight movie").assertIsFocused()
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @Test fun zeroAndOneMovieNeverAutoAdvanceOrTrapFocus() {
        val state = render(SpotlightState(movies = listOf(first), resolved = true))
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.autoAdvance = false
        advanceRotationTime(20_000)
        compose.onNodeWithText("Feature A").assertExists()
        compose.onNodeWithContentDescription("Next Spotlight movie").assertDoesNotExist()
        compose.runOnIdle { state.value = SpotlightState(resolved = true) }
        advanceRotationTime(20_000)
        compose.onNodeWithContentDescription("Resume movie").assertIsFocused()
        compose.onNodeWithText("Play").assertDoesNotExist()
        verify(exactly = 0) { viewModel.load(any()) }
        verify(exactly = 0) { viewModel.play(any()) }
    }

    @Test fun explicitHomeResetStartsCarouselAndKeepsFeaturedMovieWithoutQueries() {
        render(rowSize = 20)
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        compose.onNodeWithContentDescription("Next Spotlight movie").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Play").requestFocus()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        repeat(10) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) } }
        compose.onNodeWithContentDescription("Row 0 movie 10").assertIsFocused()
        compose.runOnIdle { resetGeneration.longValue++ }
        compose.onNodeWithText("Play").assertIsFocused()
        compose.onNodeWithText("Feature B").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, homeListState.firstVisibleItemIndex)
            assertEquals(0, homeListState.firstVisibleItemScrollOffset)
        }
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("Row 0 movie 0").assertIsFocused()
        compose.onNodeWithContentDescription("Row 0 movie 10").assertDoesNotExist()
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @Test fun verticalMovesDoNotResetHorizontalFocusOrSelection() {
        render(rowCount = 3, rowSize = 20)
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        repeat(7) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) } }
        compose.onNodeWithContentDescription("Row 0 movie 7").assertIsFocused()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithContentDescription("Row 0 movie 7").assertIsFocused()
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @Test fun resetAfterHomeDisposalDoesNotRestoreAnOffscreenCard() {
        render(rowCount = 3, rowSize = 20)
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        repeat(8) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) } }
        compose.runOnIdle { homeVisible.value = false }
        compose.onNodeWithText("Movie details destination").assertExists()
        compose.runOnIdle { resetGeneration.longValue++; homeVisible.value = true }
        compose.onNodeWithText("Play").assertIsFocused()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("Row 0 movie 0").assertIsFocused()
    }

    @Test fun resetAllCarouselsIncludingRowsOutsideTheViewport() {
        render(rowCount = 4, rowSize = 20)
        for (row in 0..3) {
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
            repeat(6) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) } }
        }
        val configuration = compose.runOnIdle { renderedRows }
        compose.runOnIdle { resetGeneration.longValue++ }
        compose.onNodeWithText("Play").assertIsFocused()
        for (row in 0..3) {
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
            compose.onNodeWithContentDescription("Row $row movie 0").assertIsFocused()
        }
        compose.runOnIdle { assertEquals(configuration, renderedRows) }
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @Test fun removedRowsDoNotLeaveAStaleFocusTargetAfterReset() {
        render(rowCount = 3, rowSize = 20)
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        repeat(6) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) } }
        compose.runOnIdle { renderedRows = renderedRows.drop(1); resetGeneration.longValue++ }
        compose.onNodeWithText("Play").assertIsFocused()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("Row 1 movie 0").assertIsFocused()
        compose.runOnIdle { assertEquals(2, renderedRows.size) }
    }

    @Test fun onlyANewDailySnapshotReestablishesTheLeadMovie() {
        val state = render()
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        compose.onNodeWithContentDescription("Next Spotlight movie").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Feature B").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(selectionDay = 101) }
        compose.onNodeWithText("Feature A").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next Spotlight movie").assertIsFocused()
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @Test fun emptyConfiguredRowsStillAllowSpotlightAfterReset() {
        render(rowCount = 0)
        compose.runOnIdle { resetGeneration.longValue++ }
        compose.onNodeWithText("Play").assertIsFocused()
    }

    @Test fun upwardReturnFromLowerRowsRevealsWholeBannerAndKeepsSelectedMovie() {
        render(rowCount = 4)
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        compose.onNodeWithContentDescription("Next Spotlight movie").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Details").requestFocus()
        compose.onNodeWithText("Details").performKeyInput { pressKey(Key.DirectionDown) }
        repeat(2) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) } }
        compose.runOnIdle {
            org.junit.Assert.assertTrue(homeListState.firstVisibleItemIndex > 0 ||
                homeListState.firstVisibleItemScrollOffset > 0)
        }
        repeat(3) { compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) } }
        compose.onNodeWithText("Details").assertIsFocused()
        compose.onNodeWithText("Feature B").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, homeListState.firstVisibleItemIndex)
            assertEquals(0, homeListState.firstVisibleItemScrollOffset)
        }
        compose.onNodeWithText("Home navigation").assertIsDisplayed()
    }

    @Test fun detailsBackRestoresDetailsAfterHomeCompositionWasDisposed() {
        render()
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        compose.onNodeWithContentDescription("Next Spotlight movie").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Details").requestFocus()
        compose.onNodeWithText("Details").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Movie details destination").assertExists()
        compose.onNodeWithText("Feature B").assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Details").assertIsFocused()
        compose.onNodeWithText("Feature B").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, homeListState.firstVisibleItemScrollOffset) }
    }

    @Test fun anotherDestinationRestoresHomeRowInsteadOfStealingFocusForSpotlight() {
        render()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        val before = compose.runOnIdle { homeListState.firstVisibleItemScrollOffset }
        compose.runOnIdle { homeVisible.value = false }
        compose.onNodeWithText("Movie details destination").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("Resume movie").assertIsFocused()
        compose.runOnIdle { assertEquals(before, homeListState.firstVisibleItemScrollOffset) }
    }

    @Test fun pendingFreshEntryPrefersPlayWhenMoviesArriveBeforeBoundedWait() {
        compose.mainClock.autoAdvance = false
        val state = render(SpotlightState(loading = true))
        compose.runOnIdle { state.value = SpotlightState(
            scope = SpotlightScope(UUID(1, 1), UUID(2, 2), UUID(3, 3)), movies = listOf(first)) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.onNodeWithText("Play").assertIsFocused()
        compose.mainClock.autoAdvance = true
    }

    @Test fun unresolvedOrEmptySpotlightFallsBackWithoutTrappingFocus() {
        render(SpotlightState(resolved = true))
        compose.onNodeWithContentDescription("Resume movie").assertIsFocused()
        compose.onNodeWithContentDescription("Resume movie").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Home navigation").assertIsFocused()
        compose.onNodeWithText("Play").assertDoesNotExist()
    }

    @Test fun loadingTimeoutFallsBackAndLateMoviesDoNotStealTheRow() {
        val state = render(SpotlightState(loading = true))
        compose.mainClock.advanceTimeBy(1600)
        compose.onNodeWithContentDescription("Resume movie").assertIsFocused()
        compose.runOnIdle { state.value = SpotlightState(
            scope = SpotlightScope(UUID(1, 1), UUID(2, 2), UUID(3, 3)), movies = listOf(first)) }
        compose.onNodeWithContentDescription("Resume movie").assertIsFocused()
        compose.onNodeWithContentDescription("Resume movie").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Play").assertIsFocused()
        compose.runOnIdle { assertEquals(0, homeListState.firstVisibleItemScrollOffset) }
    }

    @Test fun repeatedPagingNeverRequestsACandidatePoolAndRetainsNextFocus() {
        render()
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        repeat(12) {
            compose.onNodeWithContentDescription("Next Spotlight movie").performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithContentDescription("Next Spotlight movie").assertIsFocused()
        }
        verify(exactly = 0) { viewModel.load(any()) }
        compose.onNodeWithText("Feature A").assertIsDisplayed()
    }

    @Test fun lateSparsePoolRemainsReachableAboveTheConfiguredFirstRow() {
        val state = render(SpotlightState())
        compose.onNodeWithText("Play").assertDoesNotExist()
        compose.onNodeWithContentDescription("Resume movie").requestFocus()
        compose.runOnIdle { state.value = SpotlightState(
            scope = SpotlightScope(UUID(1, 1), UUID(2, 2), UUID(3, 3)), movies = listOf(first)) }
        compose.onNodeWithContentDescription("Resume movie").assertIsFocused()
        compose.onNodeWithContentDescription("Resume movie").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Play").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithText("Feature A").assertIsDisplayed()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("Resume movie").assertIsFocused()
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @Test fun dpadMovesBetweenControlsAndRowThenBackFocusesHome() {
        render()
        compose.onNodeWithText("Play").assertIsFocused()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Details").assertIsFocused()
        compose.onNodeWithText("Details").performKeyInput { pressKey(Key.DirectionDown) }
        val card = compose.onNodeWithContentDescription("Resume movie")
        card.assertIsFocused().assertWidthIsEqualTo(160.dp)
        val focusedSize = card.fetchSemanticsNode().boundsInRoot.size
        card.performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Details").assertIsFocused()
        assertEquals(focusedSize, card.fetchSemanticsNode().boundsInRoot.size)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Home navigation").assertIsFocused()
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @OptIn(InternalComposeTracingApi::class)
    @Test fun warmPagingDoesNotRecomposeStableButtonBodies() {
        val entries = java.util.concurrent.atomic.AtomicInteger()
        Composer.setTracer(object : CompositionTracer {
            override fun isTraceInProgress() = true
            override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
                if (info.startsWith("com.github.damontecres.wholphin.ui.main.SpotlightButton ")) {
                    entries.incrementAndGet()
                }
            }
            override fun traceEventEnd() {}
        })
        try {
            render()
            org.junit.Assert.assertTrue("Compiler tracing must observe real button composition", entries.get() >= 4)
            compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
            compose.runOnIdle { entries.set(0) }
            repeat(24) {
                compose.onNodeWithContentDescription("Next Spotlight movie")
                    .performKeyInput { pressKey(Key.DirectionCenter) }
                compose.onNodeWithContentDescription("Next Spotlight movie").assertIsFocused()
            }
            val count = compose.runOnIdle { entries.get() }
            println("SPOTLIGHT_24_PAGE_BUTTON_BODY_ENTRIES=$count")
            assertEquals("Unchanged button bodies should skip movie-only updates", 0, count)
            compose.onNodeWithText("Feature A").assertIsDisplayed()
            verify(exactly = 0) { viewModel.load(any()) }
        } finally {
            Composer.setTracer(null)
        }
    }

    @Test fun stableActionsUseTheDisplayedMovieAfterMetadataReplacement() {
        val state = render()
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        compose.onNodeWithContentDescription("Next Spotlight movie")
            .performKeyInput { pressKey(Key.DirectionCenter) }
        val updated = second.copy(name = "Updated Feature B", runTimeTicks = 6_000_000_000L,
            userData = UserItemDataDto(playbackPositionTicks = 600_000_000L, played = false,
                playCount = 0, isFavorite = false, key = "test", itemId = second.id))
        compose.runOnIdle { state.value = state.value.copy(movies = listOf(first, updated)) }
        compose.onNodeWithText("Updated Feature B").assertIsDisplayed()
        compose.onNodeWithText("Resume").requestFocus()
        compose.onNodeWithText("Resume").performKeyInput { pressKey(Key.DirectionCenter) }
        verify(exactly = 1) { viewModel.play(updated) }
        compose.onNodeWithText("Details").requestFocus()
        compose.onNodeWithText("Details").performKeyInput { pressKey(Key.DirectionCenter) }
        verify(exactly = 1) { viewModel.details(updated) }
        compose.onNodeWithText("Movie details destination").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Details").assertIsFocused()
        compose.onNodeWithText("Updated Feature B").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous Spotlight movie").requestFocus()
        compose.onNodeWithContentDescription("Previous Spotlight movie")
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Play").assertExists()
        compose.onNodeWithText("Resume").assertDoesNotExist()
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @Test fun callbacksFollowReplacementLineupAndDailySelectionState() {
        val state = render()
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        compose.onNodeWithContentDescription("Next Spotlight movie")
            .performKeyInput { pressKey(Key.DirectionCenter) }
        val third = first.copy(id = UUID(0, 4), name = "Feature C")
        compose.runOnIdle { state.value = state.value.copy(selectionDay = 101, movies = listOf(third, first)) }
        compose.onNodeWithText("Feature C").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next Spotlight movie").assertIsFocused()
        compose.onNodeWithContentDescription("Next Spotlight movie")
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Feature A").assertIsDisplayed()
        compose.onNodeWithText("Play").requestFocus()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionCenter) }
        verify(exactly = 1) { viewModel.play(first) }
        compose.onNodeWithContentDescription("Previous Spotlight movie").requestFocus()
        compose.onNodeWithContentDescription("Previous Spotlight movie")
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Feature C").assertIsDisplayed()
        verify(exactly = 0) { viewModel.load(any()) }
    }

    @Test fun pagingUpdatesAccessibleAnnouncementWithoutMovingControlBounds() {
        render()
        fun bounds() = listOf(
            compose.onNodeWithText("Play").fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithText("Details").fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithContentDescription("Previous Spotlight movie").fetchSemanticsNode().boundsInRoot,
            compose.onNodeWithContentDescription("Next Spotlight movie").fetchSemanticsNode().boundsInRoot,
        )
        val initial = bounds()
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        compose.onNodeWithContentDescription("Next Spotlight movie")
            .performKeyInput { pressKey(Key.DirectionCenter) }
        val description = compose.activity.getString(
            com.github.damontecres.wholphin.R.string.jellylu_spotlight_page, 2, 2, "Feature B")
        compose.onNode(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, description),
            useUnmergedTree = true).assertExists()
        assertEquals(initial, bounds())
        compose.onNodeWithContentDescription("Next Spotlight movie").assertIsFocused()
    }

    @Test fun manualPagingChangesOnlyTheFeatureAndButtonsKeepTheirActions() {
        render()
        compose.onNodeWithContentDescription("Next Spotlight movie").requestFocus()
        compose.onNodeWithContentDescription("Next Spotlight movie").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithContentDescription("Next Spotlight movie").assertIsFocused()
        compose.onNodeWithText("Feature B").assertIsDisplayed()
        compose.onNodeWithText("Details").requestFocus()
        compose.onNodeWithText("Details").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Movie details destination").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Details").assertIsFocused()
        compose.onNodeWithText("Feature B").assertIsDisplayed()
        compose.onNodeWithText("Play").requestFocus()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionCenter) }
        verify(exactly = 1) { viewModel.details(second) }
        verify(exactly = 1) { viewModel.play(second) }
        verify(exactly = 0) { viewModel.load(any()) }
        compose.onNodeWithContentDescription("Resume movie").requestFocus()
        compose.onNodeWithText("Feature B").assertExists()
    }
}
