package com.github.damontecres.wholphin.ui.preferences

import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.data.model.JellyfinUserPreferences
import com.github.damontecres.wholphin.preferences.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LunocoSettingsCatalogTest {
    private val defaults get() = AppPreferencesSerializer().defaultValue
    private fun all(prefs: AppPreferences = defaults) = LunocoSettingsCategory.entries.flatMap {
        LunocoSettingsCatalog.entries(it, prefs, updatingEnabled = true, discoverEnabled = true)
    }
    private fun flattened(groups: List<PreferenceGroup<AppPreferences>>): Set<LunocoAppPreference> =
        groups.flatMap { it.preferences + it.conditionalPreferences.flatMap { it.preferences } }.toSet()

    @Test fun eightOrderedCategoriesAndUniqueSemanticInventory() {
        assertEquals(listOf("PLAYBACK","AUDIO","HOME","APPEARANCE","SCREENSAVER","PROFILES","MEDIA","SUPPORT"),
            LunocoSettingsCategory.entries.map { it.name })
        val inventory = LunocoSettingsCatalog.inventory
        assertEquals(inventory.size, inventory.map { it.id }.distinct().size)
        assertEquals(inventory.size, inventory.map { it.preference }.distinct().size)
        assertTrue(inventory.all { it.preference != null && it.title != 0 })
    }

    @Test fun everyPreviouslyActiveDescriptorIsPresentOrAnExplicitCompatibilityRoute() {
        val original = flattened(basicPreferences + advancedPreferences + ExoPlayerPreferences +
            MpvPreferences + HdrFormatPreferences + SkipSegmentPreferences + experimentalPreferences + screensaverPreferences)
        val navigationOnly = setOf(AppPreference.AdvancedSettings, AppPreference.ScreensaverSettings)
        val catalog = LunocoSettingsCatalog.inventory.map { it.preference }.toSet()
        assertEquals("No existing visible preference may be lost", emptySet<LunocoAppPreference>(),
            original - catalog - navigationOnly)
        assertFalse(catalog.contains(AppPreference.SeekBarSteps))
        assertFalse(catalog.contains(AppPreference.NavDrawerSwitchOnFocus))
    }

    @Test fun everyCatalogEntryIsReachableThroughItsCategoryOrLegacySubpage() {
        val reachable = PlayerBackend.entries.filter { it != PlayerBackend.UNRECOGNIZED }.flatMap { backend ->
            val prefs = defaults.updatePlaybackPreferences { playerBackend = backend }
                .updateExperimentalPreferences { enabled = true }
            all(prefs) + PreferenceScreenOption.entries.flatMap { screen ->
                LunocoSettingsCatalog.entries(LunocoSettingsCatalog.categoryFor(screen), prefs, screen, true, true)
            }
        }.map { it.id }.toSet()
        assertEquals(emptySet<String>(), LunocoSettingsCatalog.inventory.map { it.id }.toSet() - reachable)
    }

    @Test fun allLegacyScreenOptionsResolveWithoutChangingTheirSerializedNames() {
        val prefs = defaults.updateExperimentalPreferences { enabled = true }
        for (screen in PreferenceScreenOption.entries) {
            assertSame(screen, PreferenceScreenOption.fromString(screen.name))
            val category = LunocoSettingsCatalog.categoryFor(screen)
            val entries = LunocoSettingsCatalog.entries(category, prefs, screen)
            assertTrue(screen.name, entries.isNotEmpty())
            assertEquals(entries.size, entries.map { it.id }.distinct().size)
        }
        assertEquals(PreferenceScreenOption.BASIC, PreferenceScreenOption.fromString("unknown"))
        assertEquals(LunocoSettingsCategory.SUPPORT, LunocoSettingsCatalog.categoryFor(PreferenceScreenOption.ADVANCED))
        assertEquals(LunocoSettingsCategory.SCREENSAVER, LunocoSettingsCatalog.categoryFor(PreferenceScreenOption.SCREENSAVER))
    }

    @Test fun updaterAndDiscoverGatesRemainEffective() {
        val none = LunocoSettingsCategory.entries.flatMap {
            LunocoSettingsCatalog.entries(it, defaults, updatingEnabled = false, discoverEnabled = false)
        }.map { it.preference }
        for (pref in listOf(AppPreference.Update, AppPreference.AutoCheckForUpdates, AppPreference.UpdateUrl, AppPreference.SeerrIntegration)) {
            assertFalse(none.contains(pref))
            assertTrue(all().any { it.preference == pref })
        }
        assertFalse("The local default build must not enable the upstream updater", BuildConfig.UPDATING_ENABLED)
    }

    @Test fun backendSpecificOptionsAndExperimentalChildrenAreConditional() {
        for (backend in listOf(PlayerBackend.EXO_PLAYER, PlayerBackend.MPV, PlayerBackend.PREFER_MPV, PlayerBackend.EXTERNAL_PLAYER)) {
            val prefs = defaults.updatePlaybackPreferences { playerBackend = backend }
            val ids = LunocoSettingsCatalog.entries(LunocoSettingsCategory.PLAYBACK, prefs).map { it.id }
            assertEquals(backend == PlayerBackend.EXTERNAL_PLAYER, "play.external_app" in ids)
            assertEquals(backend == PlayerBackend.EXO_PLAYER, "play.ffmpeg" in ids)
            assertEquals(backend == PlayerBackend.MPV, "play.mpv_conf" in ids)
            assertEquals(backend == PlayerBackend.PREFER_MPV, "play.mpv" in ids)
        }
        val screen = PreferenceScreenOption.EXPERIMENTAL
        val disabled = LunocoSettingsCatalog.entries(LunocoSettingsCategory.PLAYBACK, defaults, screen)
        assertEquals(listOf("experimental.enabled"), disabled.map { it.id })
        val enabled = LunocoSettingsCatalog.entries(LunocoSettingsCategory.PLAYBACK,
            defaults.updateExperimentalPreferences { this.enabled = true }, screen)
        assertEquals(6, enabled.size)
    }

    @Test fun metadataEnumerationDoesNotInvokeAnyGetterSetterOrAction() {
        // The catalog returns the canonical descriptors, not evaluated values or new controllers.
        val before = defaults.toByteArray()
        repeat(50) { all(); PreferenceScreenOption.entries.forEach { screen ->
            LunocoSettingsCatalog.entries(LunocoSettingsCatalog.categoryFor(screen), defaults, screen)
        } }
        assertArrayEquals(before, defaults.toByteArray())
        assertSame(AppPreference.ClearImageCache, all().first { it.id == "support.cache_clear" }.preference)
        assertSame(AppPreference.PlayerBackendPref, all().first { it.id == "play.backend" }.preference)
    }

    @Test fun typedSettersRetainUnitsAndDisplaySwitchDependenciesAcrossSerialization() = runBlocking {
        var prefs = defaults
        prefs = AppPreference.SkipForward.setter(prefs, 45)
        prefs = AppPreference.SkipBackOnResume.setter(prefs, 3)
        prefs = AppPreference.PassOutProtection.setter(prefs, 1)
        prefs = AppPreference.MaxBitrate.setter(prefs, 5)
        prefs = AppPreference.ResolutionSwitching.setter(prefs, true)
        assertEquals(45_000, prefs.playbackPreferences.skipForwardMs)
        assertEquals(3_000, prefs.playbackPreferences.skipBackOnResumeSeconds)
        assertEquals(3_600_000, prefs.playbackPreferences.passOutProtectionMs)
        assertEquals(AppPreference.bitrateValues[5], prefs.playbackPreferences.maxBitrate)
        assertTrue(prefs.playbackPreferences.refreshRateSwitching)
        val serializer = AppPreferencesSerializer()
        val bytes = ByteArrayOutputStream().also { serializer.writeTo(prefs, it) }.toByteArray()
        val restored = serializer.readFrom(ByteArrayInputStream(bytes))
        assertEquals(prefs, restored)
        val disabled = AppPreference.RefreshRateSwitching.setter(restored, false)
        assertFalse(disabled.playbackPreferences.resolutionSwitching)
        assertEquals(defaults.homePagePreferences, restored.homePagePreferences)
        assertEquals(defaults.interfacePreferences, restored.interfacePreferences)
    }

    @Test fun profileOverridesKeepInheritAndAnyLanguageSentinelsIsolated() {
        val profileA = JellyfinUserPreferences()
        val profileB = profileA.copy(preferredAudioLanguage = "eng", preferredSubtitleLanguage = UserProfileSettings.PREFER_ANY_LANGUAGE)
        assertEquals(UserProfileSettings.USE_USER_PROFILE, profileA.preferredAudioLanguage)
        assertEquals(UserProfileSettings.USE_USER_PROFILE, profileA.preferredSubtitleLanguage)
        assertEquals("eng", profileB.preferredAudioLanguage)
        assertEquals("_any-language", profileB.preferredSubtitleLanguage)
        assertNotEquals(profileA, profileB)
    }

    @Test fun restoreUsesSemanticIdThenNearestValidOrdinalAndHandlesEmptyRows() {
        val entries = all().take(5)
        val selected = entries[3]
        assertEquals(selected.id, restoredSettingId(entries.reversed(), selected.id, 3))
        val removed = entries.filter { it.id != selected.id }
        assertEquals(removed[3].id, restoredSettingId(removed, selected.id, 3))
        assertEquals(removed.last().id, restoredSettingId(removed, "missing", 99))
        assertNull(restoredSettingId(emptyList(), "missing", 3))
    }

    @Test fun importantSpecializedEditorsStayOnTheirExistingDestinations() {
        val all = all()
        for (pref in listOf(AppPreference.CustomizeHome, AppPreference.UserProfileSettings,
            AppPreference.SubtitleStyle, AppPreference.OssLicenseInfo)) {
            assertSame(pref, all.first { it.preference == pref }.preference)
        }
        assertTrue(all.any { it.id == "support.diagnostics" })
        assertTrue(all.any { it.id == "home.search" })
        assertTrue(all.any { it.id == "media.music" })
    }
}
