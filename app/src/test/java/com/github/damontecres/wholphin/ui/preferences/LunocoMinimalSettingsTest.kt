package com.github.damontecres.wholphin.ui.preferences

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.*
import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LunocoMinimalSettingsTest {
    private val defaults get() = AppPreferencesSerializer().defaultValue
    private val primary get() = LunocoSettingsCatalog.primaryCategories.flatMap { LunocoSettingsCatalog.primaryEntries(it) }

    @Test fun exactlyThreePrimarySectionsAndElevenPurposefulRows() {
        assertEquals(listOf(LunocoSettingsCategory.PLAYBACK, LunocoSettingsCategory.PROFILES, LunocoSettingsCategory.SUPPORT),
            LunocoSettingsCatalog.categories(PreferenceScreenOption.BASIC))
        assertEquals(11, primary.size)
        assertEquals(11, primary.map { it.id }.distinct().size)
        assertEquals(R.string.lunoco_settings_connections,
            LunocoSettingsCatalog.categoryTitle(LunocoSettingsCategory.PROFILES, PreferenceScreenOption.BASIC))
        assertEquals(R.string.about,
            LunocoSettingsCatalog.categoryTitle(LunocoSettingsCategory.SUPPORT, PreferenceScreenOption.BASIC))
    }

    @Test fun primaryPlaybackUsesOnlyCanonicalAutoplayLanguageAndAccessibleCaptions() {
        val rows = LunocoSettingsCatalog.primaryEntries(LunocoSettingsCategory.PLAYBACK)
        assertEquals(listOf("play.autoplay", "audio.profile", "audio.style"), rows.map { it.id })
        assertSame(AppPreference.AutoPlayNextUp, rows[0].preference)
        assertEquals(Destination.UserAppPreferences, rows[1].destination)
        assertEquals(Destination.SubtitleSettings(false), rows[2].destination)
        assertSame(AppPreference.UserProfileSettings, rows[1].preference)
        assertSame(AppPreference.SubtitleStyle, rows[2].preference)
    }

    @Test fun connectionsRetainLanguageProtectionAndExplicitExistingManagementRoutes() {
        val rows = LunocoSettingsCatalog.primaryEntries(LunocoSettingsCategory.PROFILES)
        assertEquals(listOf("connections.profiles","connections.servers","profile.locale","profile.protection"), rows.map { it.id })
        assertEquals(Destination.ManageConnections(false), rows[0].destination)
        assertEquals(Destination.ManageConnections(true), rows[1].destination)
        assertSame(AppPreference.UserInterfaceLanguage, rows[2].preference)
        assertSame(AppPreference.ProtectProfilePreference, rows[3].preference)
    }

    @Test fun aboutKeepsVersionLicenseAndDiscreetAdvancedAccessWithoutUploadActions() {
        val rows = LunocoSettingsCatalog.primaryEntries(LunocoSettingsCategory.SUPPORT)
        assertEquals(listOf("support.version","support.update","support.licenses","about.advanced"), rows.map { it.id })
        assertEquals(Destination.Settings(PreferenceScreenOption.ADVANCED), rows.last().destination)
        assertFalse(primary.any { it.preference in listOf(AppPreference.SendAppLogs, AppPreference.ClearImageCache,
            AppPreference.PlayerBackendPref, AppPreference.ThemeColors, AppPreference.CustomizeHome) })
    }

    @Test fun allNinetyTwoDescriptorsRemainReachableWithTheirExistingConditions() {
        assertEquals(92, LunocoSettingsCatalog.inventory.size)
        val reachable = PlayerBackend.entries.filter { it != PlayerBackend.UNRECOGNIZED }.flatMap { backend ->
            val prefs = defaults.updatePlaybackPreferences { playerBackend = backend }
                .updateExperimentalPreferences { enabled = true }
            LunocoSettingsCategory.entries.flatMap { category ->
                LunocoSettingsCatalog.entries(category, prefs, updatingEnabled = true, discoverEnabled = true)
            } + PreferenceScreenOption.entries.flatMap { screen ->
                LunocoSettingsCatalog.entries(LunocoSettingsCatalog.categoryFor(screen), prefs, screen, true, true)
            }
        }.map { it.id }.toSet()
        assertTrue(reachable.containsAll(LunocoSettingsCatalog.inventory.map { it.id }))
        assertEquals(8, LunocoSettingsCatalog.categories(PreferenceScreenOption.ADVANCED).size)
    }

    @Test fun browsingEitherCatalogPreservesNonDefaultAndUnknownStoredValues() {
        val prefs = defaults.updatePlaybackPreferences { playerBackend = PlayerBackend.MPV; skipForwardMs = 47000 }
            .updateInterfacePreferences { appThemeColorsValue = 999 }
        val before = prefs.toByteArray()
        repeat(20) {
            PreferenceScreenOption.entries.forEach { screen ->
                LunocoSettingsCatalog.categories(screen).forEach { category ->
                    LunocoSettingsCatalog.workspaceEntries(category, prefs, screen,
                        if (LunocoSettingsCatalog.isNested(screen)) screen else PreferenceScreenOption.BASIC)
                }
            }
        }
        assertArrayEquals(before, prefs.toByteArray())
        assertEquals(PlayerBackend.MPV, prefs.playbackPreferences.playerBackend)
        assertEquals(999, prefs.interfacePreferences.appThemeColorsValue)
    }

    @Test fun existingDefaultBuildGatesStillExcludeUpstreamUpdaterAndRetainSeerrGate() {
        val hidden = LunocoSettingsCategory.entries.flatMap {
            LunocoSettingsCatalog.entries(it, defaults, updatingEnabled = false, discoverEnabled = false)
        }.map { it.preference }
        assertFalse(hidden.contains(AppPreference.Update))
        assertFalse(hidden.contains(AppPreference.AutoCheckForUpdates))
        assertFalse(hidden.contains(AppPreference.UpdateUrl))
        assertFalse(hidden.contains(AppPreference.SeerrIntegration))
    }

    @Test fun newManagementRouteRoundTripsWithoutEmbeddingAccountOrServerIdentity() {
        for (servers in listOf(false, true)) {
            val route: Destination = Destination.ManageConnections(servers)
            val encoded = Json.encodeToString(route)
            assertEquals(route, Json.decodeFromString<Destination>(encoded))
            assertFalse(encoded.contains("settings.invalid"))
            assertTrue(route.fullScreen)
        }
        assertEquals(listOf("BASIC","ADVANCED","EXO_PLAYER","MPV","SCREENSAVER","SKIP_SEGMENTS","EXPERIMENTAL","HDR_FORMATS"),
            PreferenceScreenOption.entries.map { it.name })
    }
}
