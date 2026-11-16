package com.github.damontecres.wholphin.ui.detail.series

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import com.github.damontecres.wholphin.ui.theme.colors.JellyLuThemeColors
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.tv.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.main.homeItemDestination
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import dagger.hilt.android.testing.*
import io.mockk.*
import org.jellyfin.sdk.model.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.util.UUID

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class RecentSeriesFocusUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val registration = object : TestWatcher() {
        override fun starting(description: Description?) {
            val app: Application = ApplicationProvider.getApplicationContext()
            shadowOf(app.packageManager).addOrUpdateActivity(ActivityInfo().apply {
                name = TestActivity::class.java.name; packageName = app.packageName
            })
        }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<TestActivity>()
    @Before fun setup() { hilt.inject() }
    private val series = BaseItem(BaseItemDto(id = UUID(1, 1), type = BaseItemKind.SERIES, name = "A series"))
    private val seasons = List(4) { BaseItem(BaseItemDto(id = UUID(2, it.toLong()), type = BaseItemKind.SEASON,
        indexNumber = it, name = "Season $it")) }
    private val episodes = List(18) { BaseItem(BaseItemDto(id = UUID(3, it.toLong() + 1),
        type = BaseItemKind.EPISODE, name = "Episode ${it + 1}", indexNumber = it + 1,
        parentIndexNumber = 3, seasonId = seasons[3].id, seriesId = series.id)) }
    private val selected = RecentSeriesEpisodes(seasons[3], 3, episodes, 6, episodes[6].id)
    private var selection by mutableStateOf<RecentSeriesEpisodes?>(null)
    private var loading by mutableStateOf(false)
    private var favorite by mutableStateOf(false)
    private val clicked = mutableListOf<UUID>()
    private var currentSeries by mutableStateOf(series)
    private var seriesVisible by mutableStateOf(true)
    private var openDetailsOnEpisode = false
    private fun render(target: Boolean = true, delayed: Boolean = false, library: UUID? = null, seasonCount: Int = 4) {
        val routeTarget = if (library == null) {
            if (target) selected.episodeId.toString() else null
        } else {
            (homeItemDestination(HomeRowConfig.RecentlyAdded(library), episodes.last()) as Destination.MediaItem)
                .recentEpisode?.episodeId?.toString()
        }
        selection = if (routeTarget != null && !delayed) selected else null
        loading = delayed
        val images = mockk<ImageUrlService>(relaxed = true)
        every { images.getItemImageUrl(any<BaseItem>(), any(), any(), any(), any()) } returns null
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                CompositionLocalProvider(LocalImageUrlService provides images) {
                    val savedPages = rememberSaveableStateHolder()
                    if (seriesVisible) {
                    savedPages.SaveableStateProvider("series-entry") {
                    SeriesDetailsContent(
                        preferences = UserPreferences(AppPreferences.getDefaultInstance(), null),
                        series = currentSeries, seasons = seasons.take(seasonCount), similar = emptyList(), trailers = emptyList(),
                        extras = emptyList(), people = emptyList(), discovered = emptyList(), played = false,
                        favorite = favorite, canDelete = false, onClickItem = { _, _ -> }, onClickPerson = {},
                        onLongClickItem = { _, _ -> }, overviewOnClick = {}, playOnClick = {},
                        watchOnClick = {}, favoriteOnClick = {}, trailerOnClick = {}, onClickExtra = { _, _ -> },
                        onShowContextMenu = {}, actions = mockk<ContextMenuActions>(relaxed = true),
                        onClickDiscover = { _, _ -> }, discoverSeries = null, onClickDiscoverSeries = {},
                        recentEpisodes = selection, recentEpisodeLoading = loading,
                        recentEpisodeKey = routeTarget,
                        onClickRecentEpisode = { clicked += it.id; if (openDetailsOnEpisode) seriesVisible = false },
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    )
                    }
                    } else Text("Episode details")
                }
            }
        }
        compose.waitForIdle()
    }
    private fun episode(index: Int) = compose.onNodeWithTag("recent-series-episode-${episodes[index].id}")
    @Test fun targetsSeasonThreeEpisodeSevenWithoutAutoplay() {
        render()
        episode(6).assertIsDisplayed().assertIsFocused()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val output = File("build/reports/lunoco-recent09b/targeted-series.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        assertTrue(clicked.isEmpty())
        episode(6).performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf(episodes[6].id), clicked)
    }
    @Test fun pagingAndRecompositionDoNotResetEpisodeFocus() {
        render()
        episode(6).performKeyInput { pressKey(Key.DirectionRight) }
        episode(7).assertIsFocused()
        compose.runOnIdle { favorite = true }
        episode(7).assertIsFocused()
        assertTrue(clicked.isEmpty())
    }
    @Test fun delayedLoadingCannotStealFocusAfterUserInput() {
        render(delayed = true)
        compose.onNodeWithText("Play").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Shuffle All").assertIsFocused()
        compose.runOnIdle { selection = selected; loading = false }
        compose.onNodeWithText("Shuffle All").assertIsFocused()
        episode(6).assertDoesNotExist() // No late row insertion underneath an active browser.
        assertTrue(clicked.isEmpty())
    }
    private fun assertRecentOverview(library: UUID, seasonCount: Int) {
        render(library = library, seasonCount = seasonCount)
        compose.onNodeWithText("Play").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithText("A series").assertIsDisplayed()
        episode(17).assertDoesNotExist()
        assertTrue(clicked.isEmpty())
    }
    @Test fun showsSingleSeasonStartsAtOverviewAndPlay() = assertRecentOverview(UUID(10, 1), 1)
    @Test fun showsMultipleSeasonsStartAtOverviewAndPlay() = assertRecentOverview(UUID(10, 1), 4)
    @Test fun kidsShowsSingleSeasonStartsAtOverviewAndPlay() = assertRecentOverview(UUID(10, 2), 1)
    @Test fun kidsShowsMultipleSeasonsStartAtOverviewAndPlay() = assertRecentOverview(UUID(10, 2), 4)

    @Test fun differentSeriesDoesNotInheritActionFocus() {
        render(target = false)
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Shuffle All").assertIsFocused()
        compose.runOnIdle {
            currentSeries = BaseItem(BaseItemDto(id = UUID(8, 8), type = BaseItemKind.SERIES, name = "Another series"))
        }
        compose.onNodeWithText("Play").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithText("Another series").assertIsDisplayed()
        assertTrue(clicked.isEmpty())
    }

    @Test fun existingEpisodeSelectionSurvivesDetailsBackRestoration() {
        openDetailsOnEpisode = true
        render() // Retained legacy targeted destination; not a new Recently Added entry.
        episode(6).performKeyInput { pressKey(Key.DirectionRight) }
        episode(7).assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Episode details").assertExists()
        compose.runOnIdle { seriesVisible = true }
        episode(7).assertIsDisplayed().assertIsFocused()
        assertEquals(listOf(episodes[7].id), clicked)
    }

    @Test fun ordinarySeriesKeepsPlayFocusAndNoRecentEpisodeRow() {
        render(target = false)
        compose.onNodeWithText("Play").assertIsFocused()
        episode(6).assertDoesNotExist()
        assertTrue(clicked.isEmpty())
    }
}
