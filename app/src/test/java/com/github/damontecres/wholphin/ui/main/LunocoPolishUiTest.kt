package com.github.damontecres.wholphin.ui.main

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.*
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.cards.WatchedIconBackground
import com.github.damontecres.wholphin.ui.cards.WatchedIconColor
import com.github.damontecres.wholphin.ui.nav.BrowseDropdown
import com.github.damontecres.wholphin.ui.theme.colors.JellyLuThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.LunocoUiColors
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
class LunocoPolishUiTest {
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
    @Before fun setUp() { hilt.inject() }

    private fun menu(selected: Int = -1, choices: List<String> = listOf("Movies", "Kids Movies")): MutableState<Int> {
        val activated = mutableStateOf(-1)
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                Row(Modifier.padding(start = 160.dp, top = 20.dp)) {
                    BrowseDropdown("Movies", choices, selected, { activated.value = it })
                    Button(onClick = {}) { Text("Search") }
                }
            }
        }
        compose.onNodeWithText("Movies").requestFocus()
        return activated
    }

    @Test fun focusingTabDoesNotOpenMenuAndOkFocusesFirstChild() {
        menu()
        compose.onNodeWithText("Kids Movies").assertDoesNotExist()
        compose.onNodeWithText("Movies").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNode(hasText("Movies") and hasAnyAncestor(isPopup())).assertIsFocused()
        compose.onNodeWithText("Kids Movies").assertIsDisplayed()
    }

    @Test fun downSelectsKidsAndClosesMenuWithoutAnExtraActivation() {
        val selection = menu()
        compose.onNodeWithText("Movies").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNode(hasText("Movies") and hasAnyAncestor(isPopup())).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Kids Movies").assertIsFocused()
        compose.onNodeWithText("Kids Movies").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Kids Movies").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, selection.value) }
    }

    @Test fun backDismissesAndRestoresOriginWithoutSelecting() {
        val selection = menu()
        compose.onNodeWithText("Movies").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Kids Movies").requestFocus()
        compose.onNodeWithText("Kids Movies").performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithText("Kids Movies").assertDoesNotExist()
        compose.onNodeWithText("Movies").assertIsFocused()
        compose.runOnIdle { assertEquals(-1, selection.value) }
    }

    @Test fun activeChildIsSelectedInsideTheRealPopupWindow() {
        menu(selected = 1)
        compose.onNodeWithText("Movies").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNode(hasText("Kids Movies") and hasAnyAncestor(isPopup()))
            .assertIsSelected().assertIsDisplayed()
        compose.onNode(isPopup()).assertExists()
    }

    private val title = "A deliberately very long movie title for the compact resume footer"
    private fun cards() {
        val service = mockk<ImageUrlService>(relaxed = true)
        val dto = BaseItemDto(id = UUID(0, 1), type = BaseItemKind.MOVIE, name = title,
            runTimeTicks = 36_000_000_000L, userData = UserItemDataDto(playbackPositionTicks = 18_000_000_000L, played = false, playCount = 0, isFavorite = false, key = "test", itemId = UUID(0, 1)))
        val item = mockk<BaseItem>(relaxed = true)
        every { item.data } returns dto
        every { item.type } returns BaseItemKind.MOVIE
        every { item.title } returns title
        every { item.name } returns title
        compose.setContent {
            CompositionLocalProvider(LocalImageUrlService provides service) {
                MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                    CompactHomeResumeCard(item, 90.dp, 16f/9f, ImageType.THUMB,
                        ContentScale.Crop, true, {}, {}, Modifier.testTag("resume"))
                }
            }
        }
    }

    @Test fun footerTextIsPaddedAndCardGeometryDoesNotChangeOnFocus() {
        cards()
        val card = compose.onNodeWithTag("resume").assertWidthIsEqualTo(160.dp).assertHeightIsEqualTo(142.dp)
        val bounds = card.fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(20f, text.left - bounds.left, .5f)
        assertEquals(20f, bounds.right - text.right, .5f)
        assertTrue(text.top >= bounds.top + 180f + 16f)
        card.requestFocus()
        assertEquals(bounds.size, card.fetchSemanticsNode().boundsInRoot.size)
    }

    @Test fun resumeMetadataAndProgressRemainAccessible() {
        cards()
        compose.onNodeWithText("30 min left", useUnmergedTree = true).assertExists()
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo), useUnmergedTree = true).assertRangeInfoEquals(
            androidx.compose.ui.semantics.ProgressBarRangeInfo(.5f, 0f..1f))
    }

    @Test fun blueFillDarkFooterAndWatchedGlyphHaveDistinctFocusTreatment() {
        var background: androidx.compose.ui.graphics.Color? = null
        var glyph: androidx.compose.ui.graphics.Color? = null
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                background = WatchedIconBackground()
                glyph = WatchedIconColor()
            }
        }
        compose.runOnIdle {
            assertTrue(LunocoUiColors.Accent == background)
            assertTrue(JellyLuThemeColors.darkScheme.onPrimary == glyph)
            assertNotEquals(background, glyph)
            assertNotEquals(LunocoUiColors.Accent, LunocoUiColors.FocusOutline)
            assertEquals(androidx.compose.ui.graphics.Color(0xFF142132), LunocoUiColors.CardFooter)
        }
    }
}
