package com.github.damontecres.wholphin.ui.main

import android.app.Application
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.Release
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.setup.LunocoUpdateDialog
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.util.Version
import dagger.hilt.android.testing.*
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
import java.io.File

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LunocoUpdateDialogTest {
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
    private val open = mutableStateOf(false)
    private val downloading = mutableStateOf(false)
    private val checking = mutableStateOf(false)
    private var updates = 0
    private var dismissals = 0
    private val release = Release(Version.fromString("1.1.0"), null, null,
        "Navigation polish. https://example.invalid is plain release-note text.", emptyList())
    private fun render() {
        compose.setContent {
            WholphinTheme {
                val origin = remember { FocusRequester() }
                Column {
                    Text("Existing Settings")
                    TextButton(onClick = { open.value = true }, modifier = Modifier.focusRequester(origin)) {
                        Text("Check for Updates")
                    }
                }
                LaunchedEffect(Unit) { origin.requestFocus() }
                if (open.value) LunocoUpdateDialog(if (checking.value) null else release, downloading.value, 37,
                    onDismiss = { open.value = false; dismissals++ },
                    onUpdate = { updates++; downloading.value = true }, checking = checking.value)
            }
        }
        compose.onNodeWithText("Check for Updates").requestFocus().assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        println("UPDATE_DIALOG_OPEN=" + open.value)
        println(compose.onAllNodes(isRoot()).printToString())
        assertTrue("D-pad activation must open the real update dialog", open.value)
    }
    @Test fun laterHasSafeInitialFocusAndRestoresOrigin() {
        render()
        compose.onNodeWithText("Later").assertIsFocused()
        compose.onNodeWithText(release.body!!).assertHasNoClickAction()
        val file = File("build/reports/lunoco-updates-artwork-perf/update-dialog.png")
        file.parentFile!!.mkdirs()
        compose.onNode(isDialog()).captureToImage().asAndroidBitmap().let { bitmap ->
            // The title/body occupy the first half: a real dark Dialog must provide ivory content.
            var readablePixels = 0
            for (y in 0 until bitmap.height / 2) for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(pixel) > 180 &&
                    android.graphics.Color.green(pixel) > 180 && android.graphics.Color.blue(pixel) > 180)
                    readablePixels++
            }
            assertTrue("Dialog title/body must have high-contrast light text", readablePixels > 500)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Later").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onNodeWithText("Check for Updates").assertIsFocused()
        compose.onNodeWithText("Update Available").assertDoesNotExist()
        assertEquals(0, updates); assertEquals(1, dismissals)
    }
    @Test fun backDismissesWithoutStartingUpdate() {
        render()
        compose.runOnUiThread {
            (org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.activity.ComponentDialog)
                .onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        compose.onNodeWithText("Update Available").assertDoesNotExist()
        compose.onNodeWithText("Check for Updates").assertIsFocused()
        assertEquals(0, updates); assertEquals(1, dismissals)
    }
    @Test fun updateAndCancelUseTheSameDialogWithoutMovingParentFocus() {
        render()
        compose.onNodeWithText("Update Now").requestFocus().assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onNodeWithText("Downloading update… 37%").assertExists()
        compose.onNodeWithText("Cancel").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onNodeWithText("Check for Updates").assertIsFocused()
        assertEquals(1, updates); assertEquals(1, dismissals)
    }
    @Test fun automaticOfferNeverAppearsOverPlaybackOrSettings() {
        val updater = io.mockk.mockk<com.github.damontecres.wholphin.services.UpdateChecker>(relaxed = true)
        io.mockk.every { updater.state } returns kotlinx.coroutines.flow.MutableStateFlow(
            com.github.damontecres.wholphin.services.LunocoUpdateState(release = release))
        val destination = mutableStateOf<com.github.damontecres.wholphin.ui.nav.Destination>(
            com.github.damontecres.wholphin.ui.nav.Destination.Playback(java.util.UUID(1,1), 0))
        compose.setContent {
            CompositionLocalProvider(com.github.damontecres.wholphin.ui.setup.LocalLunocoUpdater provides updater) {
                com.github.damontecres.wholphin.ui.setup.LunocoUpdateHost(destination.value)
            }
        }
        compose.waitForIdle()
        assertFalse(com.github.damontecres.wholphin.ui.setup.lunocoUpdateSurfaceAllowed(false, destination.value))
        assertFalse(com.github.damontecres.wholphin.ui.setup.lunocoUpdateSurfaceAllowed(true, destination.value))
        assertTrue(com.github.damontecres.wholphin.ui.setup.lunocoUpdateSurfaceAllowed(false,
            com.github.damontecres.wholphin.ui.nav.Destination.Home()))
        compose.onNodeWithText("Update Available").assertDoesNotExist()
        compose.runOnIdle {
            destination.value = com.github.damontecres.wholphin.ui.nav.Destination.Settings(
                com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption.BASIC)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Update Available").assertDoesNotExist()
        io.mockk.verify(exactly = 0) { updater.presented() }
    }

    @Test fun manualCheckingCanBeCanceledBeforeAnyUpdateAction() {
        checking.value = true
        render()
        compose.onNodeWithText("Checking for updates…").assertExists()
        compose.onNodeWithText("Update Now").assertDoesNotExist()
        compose.onNodeWithText("Cancel").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onNodeWithText("Checking for updates…").assertDoesNotExist()
        compose.onNodeWithText("Check for Updates").assertIsFocused()
        assertEquals(0, updates); assertEquals(1, dismissals)
    }

}
