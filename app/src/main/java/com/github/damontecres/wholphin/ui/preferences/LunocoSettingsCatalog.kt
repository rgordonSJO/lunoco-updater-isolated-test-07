package com.github.damontecres.wholphin.ui.preferences

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.*
import com.github.damontecres.wholphin.ui.nav.Destination

typealias LunocoAppPreference = AppPreference<AppPreferences, out Any?>

enum class LunocoSettingsCategory(@param:StringRes val title: Int) {
    PLAYBACK(R.string.lunoco_settings_playback),
    AUDIO(R.string.lunoco_settings_audio),
    HOME(R.string.lunoco_settings_home),
    APPEARANCE(R.string.lunoco_settings_appearance),
    SCREENSAVER(R.string.lunoco_settings_screensaver),
    PROFILES(R.string.lunoco_settings_profiles),
    MEDIA(R.string.lunoco_settings_media),
    SUPPORT(R.string.lunoco_settings_support),
}

data class LunocoSettingEntry(
    val id: String,
    val preference: LunocoAppPreference? = null,
    @param:StringRes val title: Int = preference?.title ?: 0,
    @param:StringRes val section: Int = R.string.general,
    @param:StringRes val description: Int? = null,
    val destination: Destination? = null,
)

/** Presentation only: no API, native-library probe, account access or persistence. */
object LunocoSettingsCatalog {
    private val bindings: List<Pair<String, LunocoAppPreference>> = listOf(
        "play.skip_forward" to AppPreference.SkipForward,
        "play.skip_back" to AppPreference.SkipBack,
        "play.resume_back" to AppPreference.SkipBackOnResume,
        "play.controls_timeout" to AppPreference.ControllerTimeout,
        "play.one_click_pause" to AppPreference.OneClickPause,
        "play.cinema" to AppPreference.CinemaMode,
        "play.scale" to AppPreference.GlobalContentScale,
        "play.seek" to AppPreference.DpadSeekModePref,
        "play.debug" to AppPreference.PlaybackDebugInfo,
        "play.next_timing" to AppPreference.ShowNextUpTiming,
        "play.autoplay" to AppPreference.AutoPlayNextUp,
        "play.next_delay" to AppPreference.AutoPlayNextDelay,
        "play.pass_out" to AppPreference.PassOutProtection,
        "play.bitrate" to AppPreference.MaxBitrate,
        "play.resolution" to AppPreference.MaxResolutionPref,
        "play.refresh_rate" to AppPreference.RefreshRateSwitching,
        "play.resolution_switch" to AppPreference.ResolutionSwitching,
        "play.backend" to AppPreference.PlayerBackendPref,
        "play.external_app" to AppPreference.ExternalPlayerApp,
        "play.exo" to AppPreference.ExoPlayerSettings,
        "play.mpv" to AppPreference.MpvSettings,
        "play.hdr" to AppPreference.HdrFormatSettings,
        "play.ffmpeg" to AppPreference.FfmpegPreference,
        "play.downmix" to AppPreference.DownMixStereo,
        "play.ac3" to AppPreference.Ac3Supported,
        "play.ass" to AppPreference.AssSubtitleMode,
        "play.pgs" to AppPreference.DirectPlayPgs,
        "play.av1" to AppPreference.DecodeAv1,
        "play.hdr10" to AppPreference.Hdr10Override,
        "play.hdr10plus" to AppPreference.Hdr10PlusOverride,
        "play.dv5" to AppPreference.DoviProfile5Override,
        "play.dv7" to AppPreference.DoviProfile7Override,
        "play.dv8" to AppPreference.DoviProfile8Override,
        "play.mpv_hardware" to AppPreference.MpvHardwareDecoding,
        "play.mpv_gpu" to AppPreference.MpvGpuNext,
        "play.mpv_conf" to AppPreference.MpvConfFile,
        "play.segments" to AppPreference.SkipSegments,
        "play.intros" to AppPreference.SkipIntros,
        "play.outros" to AppPreference.SkipOutros,
        "play.commercials" to AppPreference.SkipCommercials,
        "play.previews" to AppPreference.SkipPreviews,
        "play.recaps" to AppPreference.SkipRecaps,
        "experimental.enabled" to ExperimentalPreference.Enable,
        "experimental.options" to ExperimentalPreference.ExperimentalSettings,
        "experimental.tunneling" to ExperimentalPreference.VideoTunneling,
        "experimental.prefer_dv" to ExperimentalPreference.PreferDolbyVisionOverHdr10Plus,
        "experimental.convert_dv7" to ExperimentalPreference.ConvertDolbyVisionProfile7,
        "experimental.ac3" to ExperimentalPreference.PreferAc3ForSurround,
        "experimental.offload" to ExperimentalPreference.DisableAudioOffload,
        "audio.profile" to AppPreference.UserProfileSettings,
        "audio.style" to AppPreference.SubtitleStyle,
        "home.customize" to AppPreference.CustomizeHome,
        "home.remember_tab" to AppPreference.RememberSelectedTab,
        "home.pins" to AppPreference.UserPinnedNavDrawerItems,
        "home.manage_media" to AppPreference.ManageMedia,
        "appearance.theme" to AppPreference.ThemeColors,
        "appearance.music" to AppPreference.PlayThemeMusic,
        "appearance.clock" to AppPreference.ShowClock,
        "appearance.backdrop" to AppPreference.BackdropStylePref,
        "appearance.logos" to AppPreference.ShowLogos,
        "appearance.ratings" to AppPreference.DisplayTogglesPref,
        "profile.auto_signin" to AppPreference.SignInAuto,
        "profile.protection" to AppPreference.ProtectProfilePreference,
        "profile.locale" to AppPreference.UserInterfaceLanguage,
        "profile.quick_connect" to AppPreference.QuickConnect,
        "profile.seerr" to AppPreference.SeerrIntegration,
        "media.photo_duration" to AppPreference.SlideshowDuration,
        "media.photo_videos" to AppPreference.SlideshowPlayVideos,
        "media.tv_header" to AppPreference.LiveTvShowHeader,
        "media.tv_favorites" to AppPreference.LiveTvFavoriteChannelsBeginning,
        "media.tv_watched" to AppPreference.LiveTvChannelSortByWatched,
        "media.tv_colors" to AppPreference.LiveTvColorCodePrograms,
        "support.version" to AppPreference.InstalledVersion,
        "support.update" to AppPreference.Update,
        "support.update_auto" to AppPreference.AutoCheckForUpdates,
        "support.update_url" to AppPreference.UpdateUrl,
        "support.licenses" to AppPreference.OssLicenseInfo,
        "support.cache_size" to AppPreference.ImageDiskCacheSize,
        "support.cache_clear" to AppPreference.ClearImageCache,
        "support.logs" to AppPreference.SendAppLogs,
        "support.crash_reports" to AppPreference.SendCrashReports,
        "support.debug_logging" to AppPreference.DebugLogging,
        "screensaver.enabled" to ScreensaverPreference.Enabled,
        "screensaver.delay" to ScreensaverPreference.StartDelay,
        "screensaver.duration" to ScreensaverPreference.Duration,
        "screensaver.clock" to ScreensaverPreference.ShowClock,
        "screensaver.animate" to ScreensaverPreference.Animate,
        "screensaver.age" to ScreensaverPreference.MaxAge,
        "screensaver.types" to ScreensaverPreference.ItemTypes,
        "screensaver.start" to ScreensaverPreference.Start,
        "screensaver.dim" to ScreensaverPreference.DimToggle,
        "screensaver.dim_percent" to ScreensaverPreference.DimPercentage,
    )
    val inventory: List<LunocoSettingEntry> = bindings.map { (id, pref) -> LunocoSettingEntry(id, pref) }
    private val byPreference = inventory.associateBy { it.preference }

    private fun entry(pref: LunocoAppPreference, section: Int) =
        checkNotNull(byPreference[pref]) { "Unmapped settings descriptor: ${pref.title}" }.copy(
            section = section,
            description = when (pref) {
                AppPreference.UserProfileSettings -> R.string.lunoco_settings_profile_hint
                AppPreference.ClearImageCache -> R.string.lunoco_settings_cache_hint
                AppPreference.UserPinnedNavDrawerItems -> R.string.lunoco_settings_pins_hint
                AppPreference.PlayerBackendPref -> R.string.lunoco_settings_player_scope
                AppPreference.MpvConfFile -> R.string.lunoco_settings_mpv_hint
                else -> null
            },
        )

    fun categoryFor(screen: PreferenceScreenOption): LunocoSettingsCategory = when (screen) {
        PreferenceScreenOption.BASIC, PreferenceScreenOption.EXO_PLAYER, PreferenceScreenOption.MPV,
        PreferenceScreenOption.HDR_FORMATS, PreferenceScreenOption.SKIP_SEGMENTS,
        PreferenceScreenOption.EXPERIMENTAL -> LunocoSettingsCategory.PLAYBACK
        PreferenceScreenOption.SCREENSAVER -> LunocoSettingsCategory.SCREENSAVER
        PreferenceScreenOption.ADVANCED -> LunocoSettingsCategory.SUPPORT
    }

    fun isNested(screen: PreferenceScreenOption) =
        screen != PreferenceScreenOption.BASIC && screen != PreferenceScreenOption.ADVANCED

    // BASIC is the concise entry point. The original eight categories remain only in Advanced.
    val primaryCategories = listOf(LunocoSettingsCategory.PLAYBACK,
        LunocoSettingsCategory.PROFILES, LunocoSettingsCategory.SUPPORT)

    fun categories(screen: PreferenceScreenOption): List<LunocoSettingsCategory> =
        if (screen == PreferenceScreenOption.BASIC) primaryCategories else LunocoSettingsCategory.entries

    @StringRes
    fun categoryTitle(category: LunocoSettingsCategory, screen: PreferenceScreenOption): Int =
        if (screen == PreferenceScreenOption.BASIC) when (category) {
            LunocoSettingsCategory.PROFILES -> R.string.lunoco_settings_connections
            LunocoSettingsCategory.SUPPORT -> R.string.about
            else -> category.title
        } else category.title

    fun workspaceEntries(
        category: LunocoSettingsCategory,
        preferences: AppPreferences,
        route: PreferenceScreenOption,
        page: PreferenceScreenOption = route,
    ): List<LunocoSettingEntry> =
        if (route == PreferenceScreenOption.BASIC) primaryEntries(category) else entries(category, preferences, page)

    fun primaryEntries(category: LunocoSettingsCategory): List<LunocoSettingEntry> = when (category) {
        LunocoSettingsCategory.PLAYBACK -> listOf(
            entry(AppPreference.AutoPlayNextUp, R.string.playback).copy(title = R.string.lunoco_settings_autoplay),
            entry(AppPreference.UserProfileSettings, R.string.lunoco_settings_audio).copy(
                title = R.string.lunoco_settings_language_defaults,
                destination = AppPreference.UserProfileSettings.destination),
            entry(AppPreference.SubtitleStyle, R.string.lunoco_settings_audio).copy(
                title = R.string.lunoco_settings_subtitle_appearance,
                destination = AppPreference.SubtitleStyle.destination),
        )
        LunocoSettingsCategory.PROFILES -> listOf(
            LunocoSettingEntry("connections.profiles", title = R.string.lunoco_settings_manage_profiles,
                section = R.string.profile, description = R.string.lunoco_settings_manage_profiles_hint,
                destination = Destination.ManageConnections()),
            LunocoSettingEntry("connections.servers", title = R.string.lunoco_settings_manage_servers,
                section = R.string.profile, description = R.string.lunoco_settings_manage_servers_hint,
                destination = Destination.ManageConnections(servers = true)),
            entry(AppPreference.UserInterfaceLanguage, R.string.profile).copy(title = R.string.lunoco_settings_app_language),
            entry(AppPreference.ProtectProfilePreference, R.string.profile),
        )
        LunocoSettingsCategory.SUPPORT -> listOf(
            entry(AppPreference.InstalledVersion, R.string.about),
            entry(AppPreference.Update, R.string.about),
            entry(AppPreference.OssLicenseInfo, R.string.about),
            LunocoSettingEntry("about.advanced", title = R.string.advanced_settings,
                section = R.string.lunoco_settings_more,
                description = R.string.lunoco_settings_advanced_hint,
                destination = Destination.Settings(PreferenceScreenOption.ADVANCED)),
        )
        else -> emptyList()
    }

    fun entries(
        category: LunocoSettingsCategory,
        preferences: AppPreferences,
        screen: PreferenceScreenOption = PreferenceScreenOption.BASIC,
        updatingEnabled: Boolean = BuildConfig.UPDATING_ENABLED,
        discoverEnabled: Boolean = BuildConfig.DISCOVER_ENABLED,
    ): List<LunocoSettingEntry> = buildList {
        fun group(title: Int, vararg prefs: LunocoAppPreference) {
            addAll(prefs.map { entry(it, title) })
        }
        fun groups(groups: List<PreferenceGroup<AppPreferences>>) {
            for (group in groups) {
                val visible = group.preferences + group.conditionalPreferences
                    .filter { it.condition(preferences) }.flatMap { it.preferences }
                addAll(visible.map { entry(it, group.title) })
            }
        }
        if (isNested(screen) && category == categoryFor(screen)) {
            when (screen) {
                PreferenceScreenOption.EXO_PLAYER -> groups(ExoPlayerPreferences)
                PreferenceScreenOption.MPV -> groups(MpvPreferences)
                PreferenceScreenOption.HDR_FORMATS -> groups(HdrFormatPreferences)
                PreferenceScreenOption.SKIP_SEGMENTS -> groups(SkipSegmentPreferences)
                PreferenceScreenOption.SCREENSAVER -> groups(screensaverPreferences)
                PreferenceScreenOption.EXPERIMENTAL -> {
                    group(R.string.experimental_settings, ExperimentalPreference.Enable)
                    if (preferences.experimentalPreferences.enabled) groups(experimentalPreferences)
                }
                else -> Unit
            }
            return@buildList
        }
        when (category) {
            LunocoSettingsCategory.PLAYBACK -> {
                group(R.string.lunoco_settings_remote, AppPreference.SkipForward, AppPreference.SkipBack,
                    AppPreference.SkipBackOnResume, AppPreference.OneClickPause, AppPreference.ControllerTimeout,
                    AppPreference.DpadSeekModePref)
                group(R.string.next_up, AppPreference.ShowNextUpTiming, AppPreference.AutoPlayNextUp,
                    AppPreference.AutoPlayNextDelay, AppPreference.PassOutProtection, AppPreference.SkipSegments)
                group(R.string.lunoco_settings_quality, AppPreference.CinemaMode, AppPreference.GlobalContentScale,
                    AppPreference.MaxResolutionPref, AppPreference.MaxBitrate, AppPreference.RefreshRateSwitching,
                    AppPreference.ResolutionSwitching)
                group(R.string.lunoco_settings_advanced, AppPreference.PlayerBackendPref)
                when (preferences.playbackPreferences.playerBackend) {
                    PlayerBackend.EXO_PLAYER -> groups(ExoPlayerPreferences)
                    PlayerBackend.MPV -> groups(MpvPreferences)
                    PlayerBackend.PREFER_MPV -> group(R.string.player_backend,
                        AppPreference.ExoPlayerSettings, AppPreference.MpvSettings)
                    PlayerBackend.EXTERNAL_PLAYER -> group(R.string.player_backend, AppPreference.ExternalPlayerApp)
                    else -> Unit
                }
                group(R.string.lunoco_settings_advanced, AppPreference.PlaybackDebugInfo, ExperimentalPreference.Enable)
                if (preferences.experimentalPreferences.enabled) group(R.string.experimental_settings,
                    ExperimentalPreference.ExperimentalSettings)
            }
            LunocoSettingsCategory.AUDIO -> {
                group(R.string.lunoco_settings_languages, AppPreference.UserProfileSettings, AppPreference.SubtitleStyle)
                if (preferences.playbackPreferences.playerBackend == PlayerBackend.EXO_PLAYER ||
                    preferences.playbackPreferences.playerBackend == PlayerBackend.PREFER_MPV) {
                    group(R.string.exoplayer_options, AppPreference.DownMixStereo, AppPreference.Ac3Supported,
                        AppPreference.AssSubtitleMode, AppPreference.DirectPlayPgs)
                }
            }
            LunocoSettingsCategory.HOME -> {
                group(R.string.home, AppPreference.CustomizeHome, AppPreference.RememberSelectedTab,
                    AppPreference.UserPinnedNavDrawerItems, AppPreference.ManageMedia)
                add(LunocoSettingEntry("home.search", title = R.string.search,
                    description = R.string.lunoco_settings_search_hint, destination = Destination.Search()))
            }
            LunocoSettingsCategory.APPEARANCE -> group(R.string.ui_interface,
                AppPreference.ThemeColors, AppPreference.ShowClock, AppPreference.BackdropStylePref,
                AppPreference.ShowLogos, AppPreference.DisplayTogglesPref, AppPreference.PlayThemeMusic)
            LunocoSettingsCategory.SCREENSAVER -> groups(screensaverPreferences)
            LunocoSettingsCategory.PROFILES -> {
                group(R.string.profile, AppPreference.SignInAuto, AppPreference.ProtectProfilePreference,
                    AppPreference.UserInterfaceLanguage, AppPreference.QuickConnect)
                if (discoverEnabled) group(R.string.more, AppPreference.SeerrIntegration)
            }
            LunocoSettingsCategory.MEDIA -> {
                group(R.string.live_tv, AppPreference.LiveTvShowHeader, AppPreference.LiveTvFavoriteChannelsBeginning,
                    AppPreference.LiveTvChannelSortByWatched, AppPreference.LiveTvColorCodePrograms)
                group(R.string.photos, AppPreference.SlideshowDuration, AppPreference.SlideshowPlayVideos)
                add(LunocoSettingEntry("media.music", title = R.string.lunoco_settings_music,
                    description = R.string.lunoco_settings_music_hint, destination = Destination.NowPlaying))
            }
            LunocoSettingsCategory.SUPPORT -> {
                group(R.string.about, AppPreference.InstalledVersion, AppPreference.OssLicenseInfo)
                if (updatingEnabled) group(R.string.updates,
                    AppPreference.Update, AppPreference.AutoCheckForUpdates, AppPreference.UpdateUrl)
                group(R.string.lunoco_settings_cache, AppPreference.ImageDiskCacheSize, AppPreference.ClearImageCache)
                group(R.string.lunoco_settings_reporting, AppPreference.SendCrashReports, AppPreference.SendAppLogs,
                    AppPreference.DebugLogging)
                add(LunocoSettingEntry("support.diagnostics", title = R.string.lunoco_settings_diagnostics,
                    description = R.string.lunoco_settings_diagnostics_hint, destination = Destination.Debug))
            }
        }
    }
}
