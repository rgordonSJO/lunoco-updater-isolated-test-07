package com.github.damontecres.wholphin.ui.preferences

import android.app.Application
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.*
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.nav.Destination
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
import java.io.File

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class LunocoSettingsNavigationUiTest {
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
    private var prefs by mutableStateOf(AppPreferencesSerializer().defaultValue)
    private var writes = 0
    private var navigations = 0
    private val nav = NavigationManager()
    private var replacement: List<LunocoSettingEntry>? by mutableStateOf(null)
    private val controllerErrors = mutableListOf<String>()
    private val errorTree = object : timber.log.Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority >= android.util.Log.ERROR) controllerErrors.add(message + "\n" + t?.stackTraceToString().orEmpty())
        }
    }

    @Before fun setup() {
        hilt.inject()
        timber.log.Timber.plant(errorTree)
        // Legacy canonical formatters depend on this application resource facade.
        // Do not initialize the real Application, accounts, services or crash reporter.
        val app: Application = ApplicationProvider.getApplicationContext()
        val brand = io.mockk.mockk<com.github.damontecres.wholphin.WholphinApplication>(relaxed = true)
        io.mockk.mockkObject(com.github.damontecres.wholphin.WholphinApplication.Companion)
        io.mockk.every { brand.resources } returns app.resources
        io.mockk.every { brand.getString(any()) } answers { app.getString(firstArg()) }
        io.mockk.every { brand.getString(any(), *anyVararg()) } answers { app.getString(firstArg(), *secondArg<Array<Any>>()) }
        io.mockk.every { com.github.damontecres.wholphin.WholphinApplication.instance } returns brand
    }
    @After fun teardown() {
        timber.log.Timber.uproot(errorTree)
        io.mockk.unmockkObject(com.github.damontecres.wholphin.WholphinApplication.Companion)
    }
    private fun render(screen: PreferenceScreenOption = PreferenceScreenOption.ADVANCED, fontScale: Float = 1f) {
        nav.navigateTo(Destination.Settings(screen))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 24.dp)) {
                        // Same 56dp shell height; production top navigation is not modified.
                        Box(Modifier.fillMaxWidth().height(56.dp)) { Text("Lunoco", color = MaterialTheme.colorScheme.onSurface) }
                        NavDisplay(
                            backStack = nav.backStack,
                            onBack = { nav.goBack() },
                            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                            entryProvider = { destination ->
                                NavEntry(destination) {
                                    when (destination) {
                                        is Destination.Settings -> LunocoSettingsWorkspace(
                                            prefs, destination.screen, Modifier.fillMaxSize(),
                                            entriesFor = { category, data, page ->
                                                replacement ?: LunocoSettingsCatalog.entries(category, data, page)
                                            },
                                        ) { entry, modifier, interactions ->
                                            @Suppress("UNCHECKED_CAST")
                                            val pref = entry.preference as AppPreference<AppPreferences, Any>?
                                            if (pref == null || pref is AppClickablePreference<*>) {
                                                LunocoPreferenceRow(stringResource(entry.title), { writes++ },
                                                    modifier, interactionSource = interactions)
                                            } else {
                                                val value = when (pref) {
                                                    AppPreference.SendCrashReports -> prefs.sendCrashReports
                                                    AppPreference.DebugLogging -> prefs.debugLogging
                                                    else -> pref.getter(prefs)
                                                }
                                                LunocoSettingPreference(pref, value, { next ->
                                                    val result = pref.validate(prefs, next)
                                                    assertTrue(result is PreferenceValidation.Valid)
                                                    writes++
                                                    prefs = pref.setter(prefs, next)
                                                }, {
                                                    navigations++
                                                    nav.navigateTo(it)
                                                }, modifier, interactions)
                                            }
                                        }
                                        is Destination.Home -> Text("Prior browsing entry", Modifier.testTag("prior-home"))
                                        else -> Button(onClick = { nav.goBack() }, modifier = Modifier.testTag("existing-editor")) {
                                            Text("Existing specialized editor")
                                        }
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
    private fun category(category: LunocoSettingsCategory): SemanticsNodeInteraction {
        val tag = "settings-category-${category.name}"
        compose.onNodeWithTag("settings-categories").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    private fun enter(category: LunocoSettingsCategory) {
        category(category).requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }
    private fun row(id: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("settings-detail-pane").performScrollToNode(hasTestTag("settings-row-$id"))
        return compose.onNodeWithTag("settings-row-$id")
    }
    private fun back() { compose.waitForIdle(); compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle() }
    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(1920, bitmap.width)
        assertEquals(1080, bitmap.height)
        val file = File("build/reports/lunoco-settings06/$name.png")
        checkNotNull(file.parentFile).mkdirs()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun initialFocusIsOnCategoryAndNoPreferenceChanges() {
        render()
        category(LunocoSettingsCategory.PLAYBACK).assertIsFocused().assertIsSelected()
        assertEquals(0, writes)
        assertEquals(0, navigations)
        screenshot("settings06-initial")
    }

    @Test fun allEightCategoriesWorkWithDpadWithoutInvokingActions() {
        render()
        for (cat in LunocoSettingsCategory.entries) {
            enter(cat)
            compose.onNode(isFocused()).assertHasClickAction()
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionLeft) }
            category(cat).assertIsFocused().assertIsSelected()
        }
        assertEquals(0, writes)
        assertEquals(0, navigations)
    }

    @Test fun categoryFocusAndSelectionAreDistinctAndRightEntersSelectedPane() {
        render()
        category(LunocoSettingsCategory.AUDIO).requestFocus()
        category(LunocoSettingsCategory.PLAYBACK).assertIsSelected()
        category(LunocoSettingsCategory.AUDIO).assertIsNotSelected().assertIsFocused()
        category(LunocoSettingsCategory.AUDIO).performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("settings-row-play.skip_forward").assertIsFocused()
        assertEquals(0, writes)
    }

    @Test fun switchesHaveAccessibleStateAndOnlyChangeOnActivation() {
        render()
        enter(LunocoSettingsCategory.APPEARANCE)
        val node = row("appearance.clock")
        node.requestFocus()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsOn()
        assertEquals(0, writes)
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        node.assertIsOff().assertIsFocused()
        assertEquals(1, writes)
        assertFalse(prefs.interfacePreferences.showClock)
    }

    @Test fun choiceFocusesCurrentSelectionAndBackReturnsToExactRowWithoutWriting() {
        prefs = AppPreference.ThemeColors.setter(prefs, AppThemeColors.GREEN)
        render()
        enter(LunocoSettingsCategory.APPEARANCE)
        row("appearance.theme").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("settings-choice-2").assertIsFocused().assertIsSelected()
        back()
        row("appearance.theme").assertIsFocused()
        assertEquals(AppThemeColors.GREEN, prefs.interfacePreferences.appThemeColors)
        assertEquals(0, writes)
    }

    @Test fun selectingChoiceCommitsCanonicalValueAndRestoresOrigin() {
        render()
        enter(LunocoSettingsCategory.APPEARANCE)
        row("appearance.theme").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("settings-choice-0").assertIsFocused()
        compose.onNodeWithTag("settings-choice-0").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("settings-choice-1").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        row("appearance.theme").assertIsFocused()
        assertEquals(AppThemeColors.BLUE, prefs.interfacePreferences.appThemeColors)
        assertEquals(1, writes)
    }

    @Test fun multichoiceWritesOncePerActivationAndTracksExternalState() {
        prefs = prefs.updateInterfacePreferences { clearDisplayToggles(); addDisplayToggles(DisplayToggle.CRITIC_RATING) }
        render()
        enter(LunocoSettingsCategory.APPEARANCE)
        row("appearance.ratings").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("settings-choice-1").assertIsFocused().assertIsOn()
        compose.onNodeWithTag("settings-choice-0").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(1, writes)
        assertTrue(DisplayToggle.OFFICIAL_RATING in prefs.interfacePreferences.displayTogglesList)
        compose.runOnIdle { prefs = prefs.updateInterfacePreferences { clearDisplayToggles(); addDisplayToggles(DisplayToggle.COMMUNITY_RATING) } }
        compose.onNodeWithTag("settings-choice-0").assertIsOff()
        compose.onNodeWithTag("settings-choice-2").assertIsOn()
        back()
        row("appearance.ratings").assertIsFocused()
        assertEquals(1, writes)
    }

    @Test fun sliderHasExplicitAdjustmentModeAndReliableBackAndLeftExit() {
        render()
        category(LunocoSettingsCategory.PLAYBACK).performKeyInput { pressKey(Key.DirectionRight) }
        val node = row("play.skip_forward")
        node.assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(0, writes)
        node.requestFocus().performKeyInput { pressKey(Key.DirectionCenter); pressKey(Key.DirectionRight) }
        assertEquals(35_000L, prefs.playbackPreferences.skipForwardMs)
        assertEquals(1, writes)
        compose.onNodeWithText(compose.activity.getString(R.string.lunoco_settings_adjusting)).assertExists()
        back() // Finish adjustment, not leave Settings.
        node.assertIsFocused()
        node.performKeyInput { pressKey(Key.DirectionLeft) }
        category(LunocoSettingsCategory.PLAYBACK).assertIsFocused()
    }

    @Test fun sliderExposesAccessibleRangeAndClampsValues() {
        render()
        enter(LunocoSettingsCategory.PLAYBACK)
        row("play.skip_forward").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(500f)) }
        assertEquals(300_000L, prefs.playbackPreferences.skipForwardMs)
        row("play.skip_forward").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
    }

    @Test fun nestedCompatibilityPageBackRestoresItsExactLinkAndCategory() {
        render()
        enter(LunocoSettingsCategory.PLAYBACK)
        val node = row("play.segments").requestFocus()
        val before = node.fetchSemanticsNode().boundsInRoot
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("settings-row-play.intros").assertIsFocused()
        back()
        row("play.segments").assertIsFocused()
        assertEquals(before, row("play.segments").fetchSemanticsNode().boundsInRoot)
        assertEquals(1, navigations)
        assertEquals(0, writes)
    }

    @Test fun specializedEditorBackRestoresAudioProfileLink() {
        render()
        enter(LunocoSettingsCategory.AUDIO)
        row("audio.profile").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("existing-editor").assertExists()
        back()
        row("audio.profile").assertIsFocused()
        category(LunocoSettingsCategory.AUDIO).assertIsSelected()
    }

    @Test fun rootBackPreservesNavigationStackAndDoesNotResetHome() {
        render()
        enter(LunocoSettingsCategory.APPEARANCE)
        back()
        compose.onNodeWithTag("prior-home").assertExists()
        assertEquals(0L, nav.homeResetGeneration.value)
        assertEquals(0L, nav.homeSessionGeneration.value)
        assertEquals(0, writes)
    }

    @Test fun changingUnrelatedValuesDoesNotStealRowFocus() {
        render()
        enter(LunocoSettingsCategory.APPEARANCE)
        row("appearance.clock").requestFocus()
        compose.runOnIdle { prefs = AppPreference.ShowLogos.setter(prefs, false) }
        row("appearance.clock").assertIsFocused()
        assertEquals(0, writes)
    }

    @Test fun removedConditionalRowRestoresNearestValidPreference() {
        replacement = LunocoSettingsCatalog.entries(LunocoSettingsCategory.APPEARANCE, prefs).take(3)
        render()
        enter(LunocoSettingsCategory.PLAYBACK)
        row("appearance.backdrop").requestFocus()
        compose.runOnIdle { replacement = replacement!!.dropLast(1) }
        row("appearance.clock").assertIsFocused()
    }

    @Test fun emptyCategoryKeepsAValidCategoryFocusTarget() {
        replacement = emptyList()
        render()
        enter(LunocoSettingsCategory.AUDIO)
        category(LunocoSettingsCategory.AUDIO).assertIsFocused()
        assertEquals(0, writes)
        back()
        compose.onNodeWithTag("prior-home").assertExists()
    }

    @Test fun largeFontCategoryIndexScrollsIndependentlyAndLabelsAreNotClipped() {
        render(fontScale = 1.6f)
        for (cat in LunocoSettingsCategory.entries) {
            category(cat).assertIsDisplayed().requestFocus().assertIsFocused()
            val label = compose.onNode(hasText(compose.activity.getString(cat.title)) and hasAnyAncestor(hasTestTag("settings-category-${cat.name}")), useUnmergedTree = true)
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            assertFalse("Category must not ellipsize at large font", layouts.single().hasVisualOverflow)
        }
        assertEquals(0, writes)
        screenshot("settings06-large-font")
    }

    @Test fun localVisualEvidenceShowsWideLunocoRows() {
        render()
        enter(LunocoSettingsCategory.APPEARANCE)
        row("appearance.theme").requestFocus()
        screenshot("settings06-appearance-focused")
        assertTrue(compose.onNodeWithTag("settings-detail-pane").fetchSemanticsNode().boundsInRoot.width > 1200f)
    }

    @Test fun beforeEvidenceReconstructsTheOriginalNarrowPresentationUsingLegacyWidgets() {
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Column(Modifier.fillMaxWidth(.4f).fillMaxHeight().align(androidx.compose.ui.Alignment.TopEnd)) {
                        Text("Lunoco Settings", modifier = Modifier.padding(16.dp))
                        for (pref in basicPreferences.first().preferences) {
                            @Suppress("UNCHECKED_CAST")
                            pref as AppPreference<AppPreferences, Any>
                            ComposablePreference(pref, pref.getter(prefs), {}, {}, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        screenshot("settings05-legacy-layout-reconstruction")
    }
    private class MemoryPreferences(initial: AppPreferences) : androidx.datastore.core.DataStore<AppPreferences> {
        val state = kotlinx.coroutines.flow.MutableStateFlow(initial)
        var changes = 0
        private val mutex = kotlinx.coroutines.sync.Mutex()
        override val data: kotlinx.coroutines.flow.Flow<AppPreferences> = state
        override suspend fun updateData(transform: suspend (AppPreferences) -> AppPreferences): AppPreferences {
            mutex.lock()
            try { return transform(state.value).also { state.value = it; changes++ } }
            finally { mutex.unlock() }
        }
    }

    private fun actualController(initial: AppPreferences = prefs): Pair<MemoryPreferences, PreferencesViewModel> {
        val data = MemoryPreferences(initial)
        val vm = io.mockk.mockk<PreferencesViewModel>(relaxed = true)
        val updater = io.mockk.mockk<com.github.damontecres.wholphin.ui.setup.UpdateViewModel>(relaxed = true)
        val seerr = io.mockk.mockk<com.github.damontecres.wholphin.ui.setup.seerr.SwitchSeerrViewModel>(relaxed = true)
        io.mockk.every { vm.preferenceDataStore } returns data
        io.mockk.every { vm.navigationManager } returns nav
        io.mockk.every { vm.currentUser } returns kotlinx.coroutines.flow.MutableStateFlow(null)
        io.mockk.every { vm.externalPlayers } returns kotlinx.coroutines.flow.MutableStateFlow(listOf(ExternalPlayerApp("System default", null, "")))
        io.mockk.every { vm.serverSupported } returns kotlinx.coroutines.flow.MutableStateFlow(null to com.github.damontecres.wholphin.ui.setup.ServerVersionSupported.SUPPORTED)
        io.mockk.every { vm.seerrConnection } returns kotlinx.coroutines.flow.MutableStateFlow(com.github.damontecres.wholphin.services.SeerrState())
        io.mockk.every { updater.state } returns kotlinx.coroutines.flow.MutableStateFlow(com.github.damontecres.wholphin.ui.setup.InstallUpdateState())
        io.mockk.every { updater.currentVersion } returns com.github.damontecres.wholphin.util.Version.fromString("0.0.0-local")
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                PreferencesContent(initial, PreferenceScreenOption.ADVANCED, Modifier.fillMaxSize(),
                    viewModel = vm, updateVM = updater, seerrVm = seerr)
            }
        }
        compose.waitForIdle()
        return data to vm
    }

    @Test fun actualControllerCategoryFocusDoesNotWriteOrExecuteSpecialActions() {
        val (data, vm) = actualController()
        for (cat in listOf(LunocoSettingsCategory.AUDIO, LunocoSettingsCategory.HOME,
            LunocoSettingsCategory.MEDIA, LunocoSettingsCategory.PLAYBACK)) {
            enter(cat)
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionLeft) }
        }
        assertEquals(0, data.changes)
        io.mockk.verify(exactly = 0) { vm.sendAppLogs() }
        io.mockk.verify(exactly = 0) { vm.authorizeQuickConnect(any()) }
        io.mockk.verify(exactly = 0) { vm.resetSubtitleSettings() }
    }

    @Test fun actualExperimentalEnableStillRequiresConfirmationAndRestoresFocusOnCancel() {
        val (data, _) = actualController()
        enter(LunocoSettingsCategory.PLAYBACK)
        row("experimental.enabled").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText(compose.activity.getString(R.string.confirm_enable_experimental_title)).assertExists()
        assertEquals(0, data.changes)
        back()
        row("experimental.enabled").assertIsFocused()
        assertFalse(data.state.value.experimentalPreferences.enabled)
        row("experimental.enabled").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText(compose.activity.getString(R.string.confirm))
            .requestFocus().assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText(compose.activity.getString(R.string.confirm_enable_experimental_title)).assertDoesNotExist()
        compose.waitUntil(timeoutMillis = 5_000) {
            assertTrue(controllerErrors.joinToString("\n"), controllerErrors.isEmpty())
            data.state.value.experimentalPreferences.enabled
        }
        compose.waitForIdle()
        assertTrue(data.state.value.experimentalPreferences.enabled)
        assertEquals(1, data.changes)
    }

    @Test fun actualControllerDoesNotProbeOrChangeLegacyMpvValueOnRenderingOrCategoryFocus() {
        val initial = prefs.updatePlaybackPreferences { playerBackend = PlayerBackend.MPV }
        val (data, _) = actualController(initial)
        enter(LunocoSettingsCategory.PLAYBACK)
        assertEquals(PlayerBackend.MPV, data.state.value.playbackPreferences.playerBackend)
        assertEquals(0, data.changes)
        compose.onNodeWithText(compose.activity.getString(R.string.lunoco_settings_player_scope)).assertExists()
    }

    @Test fun unknownStoredChoiceRemainsUnchangedAndDoesNotTrapFocus() {
        prefs = prefs.updateInterfacePreferences { appThemeColorsValue = 999 }
        render()
        enter(LunocoSettingsCategory.APPEARANCE)
        row("appearance.theme").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        // The canonical ThemeColors descriptor intentionally displays its default for an unknown enum.
        compose.onNodeWithTag("settings-choice-0").assertIsFocused().assertIsSelected()
        back()
        row("appearance.theme").assertIsFocused()
        assertEquals(999, prefs.interfacePreferences.appThemeColorsValue)
        assertEquals(0, writes)
    }


}
