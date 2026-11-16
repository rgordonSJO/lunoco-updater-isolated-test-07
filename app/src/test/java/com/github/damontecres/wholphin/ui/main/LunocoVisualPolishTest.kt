package com.github.damontecres.wholphin.ui.main

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.*
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.components.GridTitle
import com.github.damontecres.wholphin.ui.theme.colors.JellyLuThemeColors
import dagger.hilt.android.testing.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class LunocoVisualPolishTest {
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

    private fun assertCentered(actual: Rect, surface: Rect) {
        assertEquals("Horizontal content center", surface.center.x, actual.center.x, .75f)
        assertEquals("Vertical content center", surface.center.y, actual.center.y, .75f)
    }

    @Test fun playResumeAndDetailsCenterInTheActualSurfaceWithAndWithoutFocus() {
        val bounds = mutableMapOf<String, Rect>()
        val labels = listOf("Play" to 88.dp, "Resume" to 88.dp, "Details" to 80.dp)
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                Row(Modifier.padding(40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((label, width) in labels) {
                        SpotlightButton({}, Modifier.width(width).testTag("button-$label")
                            .onGloballyPositioned { bounds[label] = it.boundsInRoot() }, primary = label != "Details") {
                            Text(label)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val initial = bounds.toMap()
        fun verify() {
            for ((label, width) in labels) {
                val button = compose.onNodeWithTag("button-$label")
                button.assertHasClickAction().assertIsEnabled()
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                    .assertWidthIsEqualTo(width).assertHeightIsEqualTo(32.dp)
                val text = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertCentered(text, bounds.getValue(label))
                assertEquals(initial.getValue(label), bounds.getValue(label))
            }
        }
        verify()
        for ((label, _) in labels) {
            compose.onNodeWithTag("button-$label").requestFocus().assertIsFocused()
            compose.waitForIdle()
            verify() // The focused button and both unfocused peers must remain centered.
        }
    }

    @Test fun anIconAndLabelAreCenteredTogetherRatherThanIndependently() {
        var surface = Rect.Zero
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                SpotlightButton({}, Modifier.padding(40.dp).width(88.dp).testTag("group-button")
                    .onGloballyPositioned { surface = it.boundsInRoot() }, primary = true) {
                    Icon(Icons.Default.PlayArrow, "Play icon", Modifier.size(16.dp).testTag("icon"))
                    Spacer(Modifier.width(4.dp))
                    Text("Play")
                }
            }
        }
        compose.waitForIdle()
        fun verify() {
            val icon = compose.onNodeWithTag("icon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val label = compose.onNodeWithText("Play", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val group = Rect(minOf(icon.left, label.left), minOf(icon.top, label.top),
                maxOf(icon.right, label.right), maxOf(icon.bottom, label.bottom))
            assertCentered(group, surface)
            assertEquals(8f, label.left - icon.right, .75f) // 4dp at the TV's xhdpi density.
        }
        verify()
        compose.onNodeWithTag("group-button").requestFocus().assertIsFocused()
        compose.waitForIdle()
        verify()
    }

    private fun layout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        return results.single()
    }

    @Test fun allLibraryTitlesShareSoftCompactTypographyAndTheGridLeftEdge() {
        val names = listOf("Movies", "Kids Movies", "Shows", "Kids Shows", "Home Movies")
        val titleBounds = mutableMapOf<String, Rect>()
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    for (name in names) {
                        GridTitle(name, Modifier.onGloballyPositioned { titleBounds[name] = it.boundsInRoot() })
                        // CardGrid has no horizontal content inset; retain the same parent edge.
                        Box(Modifier.size(96.dp, 1.dp).testTag("grid-$name"))
                    }
                }
            }
        }
        compose.waitForIdle()
        for (name in names) {
            val result = layout(name)
            with(result.layoutInput.style) {
                assertEquals(28.sp, fontSize)
                assertEquals(34.sp, lineHeight)
                assertEquals(FontWeight.SemiBold, fontWeight)
                assertEquals(Color(0xFFEAF0F6), color)
                assertEquals(TextAlign.Start, textAlign)
            }
            assertEquals(1, result.lineCount)
            val grid = compose.onNodeWithTag("grid-$name").fetchSemanticsNode().boundsInRoot
            assertEquals(48f, titleBounds.getValue(name).left, .5f)
            assertEquals(grid.left, titleBounds.getValue(name).left, .5f)
            assertEquals("8dp top plus 4dp bottom", 24f,
                titleBounds.getValue(name).height - result.size.height, .5f)
        }
    }

    @Test fun actualLongLibraryNamesRemainAccessibleAndEllipsizeOnOneLine() {
        val name = "Our family library with a deliberately long custom name that must be retained"
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                Column(Modifier.width(240.dp).padding(horizontal = 24.dp)) {
                    GridTitle(name)
                }
            }
        }
        compose.onNodeWithText(name, useUnmergedTree = true).assertExists()
        val result = layout(name)
        assertEquals(name, result.layoutInput.text.text)
        assertEquals(1, result.layoutInput.maxLines)
        assertEquals(1, result.lineCount)
        assertTrue(result.isLineEllipsized(0))
    }
}
