package com.github.damontecres.wholphin.ui.preferences

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.imageLoader
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.AppSwitchPreference
import com.github.damontecres.wholphin.preferences.ExperimentalPreference
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.ScreensaverPreference
import com.github.damontecres.wholphin.services.Release
import com.github.damontecres.wholphin.services.SeerrConnectionStatus
import com.github.damontecres.wholphin.services.UpdateChecker
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.ScrollableDialog
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings
import com.github.damontecres.wholphin.ui.serverSupportedMessage
import com.github.damontecres.wholphin.ui.setup.ReleaseNotes
import com.github.damontecres.wholphin.ui.setup.UpdateViewModel
import com.github.damontecres.wholphin.ui.setup.seerr.SeerrDialog
import com.github.damontecres.wholphin.ui.setup.seerr.SeerrDialogMode
import com.github.damontecres.wholphin.ui.setup.seerr.SwitchSeerrViewModel
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PreferencesContent(
    initialPreferences: AppPreferences,
    preferenceScreenOption: PreferenceScreenOption,
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = hiltViewModel(),
    updateVM: UpdateViewModel = hiltViewModel(),
    seerrVm: SwitchSeerrViewModel = hiltViewModel(),
    onFocus: (Int, Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    var preferences by remember { mutableStateOf(initialPreferences) }
    val currentUser by viewModel.currentUser.collectAsState()
    var showPinFlow by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var restoreVersionFocus by remember { mutableStateOf<(() -> Unit)?>(null) }
    val players by viewModel.externalPlayers.collectAsState()
    val serverSupported by viewModel.serverSupported.collectAsState()
    val serverVersionMessage =
        remember(resources, serverSupported) {
            resources.serverSupportedMessage(serverSupported.second, serverSupported.first)
        }

    var cacheUsage by remember { mutableStateOf(CacheUsage(0, 0, 0)) }
    val seerrConnection by viewModel.seerrConnection.collectAsState()
    var seerrDialogMode by remember { mutableStateOf<SeerrDialogMode>(SeerrDialogMode.None) }
    var showQuickConnectDialog by remember { mutableStateOf(false) }
    var showLocaleChoiceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.preferenceDataStore.data.collect {
            preferences = it
        }
    }
    var updateCache by remember { mutableStateOf(false) }
    LaunchedEffect(updateCache) {
        val imageUsedMemory = context.imageLoader.memoryCache?.size ?: 0L
        val imageMaxMemory = context.imageLoader.memoryCache?.maxSize ?: 0L
        val imageDisk = context.imageLoader.diskCache?.size ?: 0L
        cacheUsage = CacheUsage(imageUsedMemory, imageMaxMemory, imageDisk)
        updateCache = false
    }

    val installedVersionLabel = com.github.damontecres.wholphin.BuildConfig.VERSION_NAME


    LunocoSettingsWorkspace(
        preferences = preferences,
        screen = preferenceScreenOption,
        message = serverVersionMessage,
        modifier = modifier,
    ) { entry, focusModifier, interactionSource ->
        if (entry.preference == null || entry.destination != null) {
            LunocoPreferenceRow(
                title = stringResource(entry.title),
                onClick = { entry.destination?.let(viewModel.navigationManager::navigateTo) },
                modifier = focusModifier,
                interactionSource = interactionSource,
            )
        } else {
            @Suppress("UNCHECKED_CAST")
            val pref = entry.preference as AppPreference<AppPreferences, Any>
        when (pref) {
            AppPreference.InstalledVersion -> {
                val restore = LocalLunocoSettingPresentation.current?.restoreFocus
                ClickPreference(
                    title = stringResource(R.string.installed_version),
                    onClick = {
                        restoreVersionFocus = restore
                        showVersionDialog = true
                    },
                    onLongClick = {
                        viewModel.navigationManager.navigateTo(Destination.Debug)
                    },
                    summary = stringResource(R.string.jellylu_version_summary, installedVersionLabel),
                    interactionSource = interactionSource,
                    modifier = focusModifier,
                )
            }

            AppPreference.Update -> {
                val updater = com.github.damontecres.wholphin.ui.setup.LocalLunocoUpdater.current
                ClickPreference(
                    title = stringResource(R.string.check_for_updates),
                    onClick = { scope.launch { updater?.checkForUpdates(manual = true) } },
                    onLongClick = null,
                    summary = installedVersionLabel,
                    interactionSource = interactionSource,
                    modifier = focusModifier,
                )
            }

            AppPreference.ClearImageCache -> {
                val summary =
                    remember(cacheUsage) {
                        cacheUsage.let {
                            val diskMB =
                                it.imageDiskUsed / AppPreference.MEGA_BIT
                            val memoryUsedMB =
                                it.imageMemoryUsed / AppPreference.MEGA_BIT
                            val memoryMaxMB =
                                it.imageMemoryMax / AppPreference.MEGA_BIT
                            "Disk: ${diskMB}mb, Memory: ${memoryUsedMB}mb/${memoryMaxMB}mb"
                        }
                    }
                ClickPreference(
                    title = stringResource(pref.title),
                    onClick = {
                        SingletonImageLoader.get(context).let {
                            it.memoryCache?.clear()
                            it.diskCache?.clear()
                            updateCache = true
                        }
                    },
                    modifier = focusModifier,
                    summary = summary,
                    onLongClick = {},
                    interactionSource = interactionSource,
                )
            }

            AppPreference.UserPinnedNavDrawerItems -> {
                NavDrawerPreference(
                    title = stringResource(pref.title),
                    summary = pref.summary(context, null),
                    modifier = focusModifier,
                    interactionSource = interactionSource,
                )
            }

            AppPreference.SendAppLogs -> {
                ClickPreference(
                    title = stringResource(pref.title),
                    onClick = {
                        viewModel.sendAppLogs()
                    },
                    modifier = focusModifier,
                    summary = pref.summary(context, null),
                    onLongClick = {},
                    interactionSource = interactionSource,
                )
            }

            SubtitleSettings.Reset -> {
                ClickPreference(
                    title = stringResource(pref.title),
                    onClick = {
                        viewModel.resetSubtitleSettings()
                    },
                    modifier = focusModifier,
                    summary = pref.summary(context, null),
                    onLongClick = {},
                    interactionSource = interactionSource,
                )
            }

            AppPreference.ProtectProfilePreference -> {
                val summary =
                    when {
                        currentUser?.requireLogin == true -> R.string.require_login
                        currentUser?.hasPin == true -> R.string.require_pin_code
                        else -> R.string.none
                    }
                ChoicePreference(
                    title = stringResource(pref.title),
                    summary = stringResource(summary),
                    interactionSource = interactionSource,
                    possibleValues =
                        listOf(
                            stringResource(R.string.none),
                            stringResource(R.string.require_pin_code),
                            stringResource(R.string.require_login),
                        ),
                    selectedIndex =
                        when {
                            currentUser?.requireLogin == true -> ProfileProtection.LOGIN
                            currentUser?.hasPin == true -> ProfileProtection.PIN
                            else -> ProfileProtection.NONE
                        }.ordinal,
                    onValueChange = {
                        currentUser?.let { user ->
                            when (ProfileProtection.entries[it]) {
                                ProfileProtection.NONE -> {
                                    viewModel.removeLoginAndPin(user)
                                }

                                ProfileProtection.PIN -> {
                                    showPinFlow = true
                                }

                                ProfileProtection.LOGIN -> {
                                    viewModel.setRequireLogin(user)
                                }
                            }
                        }
                    },
                    modifier = focusModifier,
                )
            }

            AppPreference.SeerrIntegration -> {
                ClickPreference(
                    title = stringResource(pref.title),
                    onClick = {
                        seerrDialogMode =
                            when (val conn = seerrConnection.connection) {
                                is SeerrConnectionStatus.Error -> {
                                    SeerrDialogMode.Error(
                                        conn.server,
                                        conn.user,
                                        conn.ex,
                                    )
                                }

                                SeerrConnectionStatus.NotConfigured -> {
                                    SeerrDialogMode.Add
                                }

                                is SeerrConnectionStatus.Success -> {
                                    SeerrDialogMode.Remove(
                                        conn.current,
                                    )
                                }
                            }
                    },
                    modifier = focusModifier,
                    summary =
                        when (seerrConnection.connection) {
                            is SeerrConnectionStatus.Error -> {
                                stringResource(R.string.voice_error_server)
                            }

                            SeerrConnectionStatus.NotConfigured -> {
                                stringResource(R.string.add_server)
                            }

                            is SeerrConnectionStatus.Success -> {
                                stringResource(R.string.enabled)
                            }
                        },
                    onLongClick = {},
                    interactionSource = interactionSource,
                )
            }

            AppPreference.QuickConnect -> {
                ClickPreference(
                    title = stringResource(pref.title),
                    onClick = {
                        if (currentUser != null) {
                            viewModel.resetQuickConnectStatus()
                            showQuickConnectDialog = true
                        }
                    },
                    modifier = focusModifier,
                    summary = pref.summary(context, null),
                    onLongClick = {},
                    interactionSource = interactionSource,
                )
            }

            ScreensaverPreference.Start -> {
                ClickPreference(
                    title = stringResource(pref.title),
                    onClick = {
                        viewModel.screensaverService.start()
                    },
                    modifier = focusModifier,
                    summary = pref.summary(context, null),
                    onLongClick = {},
                    interactionSource = interactionSource,
                )
            }

            AppPreference.ExternalPlayerApp -> {
                val value = pref.getter.invoke(preferences).toString()
                val selectedIndex =
                    remember(value, players) {
                        players.indexOfFirstOrNull { it.identifier == value }
                    } ?: 0
                ChoicePreference(
                    title = stringResource(pref.title),
                    summary = players.getOrNull(selectedIndex)?.name ?: stringResource(R.string.system_default),
                    interactionSource = interactionSource,
                    possibleValues = players,
                    selectedIndex = selectedIndex,
                    onValueChange = { index ->
                        scope.launch(ExceptionHandler()) {
                            val newValue =
                                players.getOrNull(index)?.identifier ?: ""
                            preferences =
                                viewModel.preferenceDataStore.updateData { prefs ->
                                    pref.setter.invoke(prefs, newValue)
                                }
                        }
                    },
                    valueDisplay = { index, item ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (item.icon != null) {
                                Image(
                                    bitmap = item.icon,
                                    contentDescription = null,
                                    modifier = Modifier.width(40.dp),
                                )
                            }
                            Text(item.name)
                        }
                    },
                    modifier = focusModifier,
                )
            }

            AppPreference.UserInterfaceLanguage -> {
                val locale =
                    remember(currentUser?.uiLanguage) {
                        currentUser?.uiLanguage?.let { Locale.forLanguageTag(it) }
                            ?: Locale.getDefault()
                    }
                ClickPreference(
                    title = stringResource(pref.title),
                    onClick = {
                        showLocaleChoiceDialog = true
                    },
                    modifier = focusModifier,
                    summary = locale.getDisplayName(locale),
                    onLongClick = null,
                    interactionSource = interactionSource,
                )
            }

            ExperimentalPreference.Enable -> {
                var showConfirm by remember { mutableStateOf(false) }
                val restore = LocalLunocoSettingPresentation.current?.restoreFocus
                BackHandler(showConfirm) { showConfirm = false; restore?.invoke() }
                pref as AppSwitchPreference<AppPreferences>
                val value = pref.getter.invoke(preferences)
                SwitchPreference(
                    title = stringResource(pref.title),
                    value = value,
                    onClick = {
                        if (value) {
                            scope.launch(ExceptionHandler()) {
                                preferences =
                                    viewModel.preferenceDataStore.updateData { prefs ->
                                        pref.setter.invoke(prefs, false)
                                    }
                            }
                        } else {
                            showConfirm = true
                        }
                    },
                    summaryOn = pref.summaryOn?.let { stringResource(pref.summaryOn) },
                    summaryOff = pref.summaryOff?.let { stringResource(pref.summaryOff) },
                    modifier = focusModifier,
                    interactionSource = interactionSource,
                )
                if (showConfirm) {
                    LunocoSettingsConfirmation(
                        title = stringResource(R.string.confirm_enable_experimental_title),
                        body = stringResource(R.string.confirm_enable_experimental_body),
                        onCancel = { showConfirm = false; restore?.invoke() },
                        onConfirm = {
                            showConfirm = false
                            restore?.invoke()
                            scope.launch(ExceptionHandler()) {
                                preferences =
                                    viewModel.preferenceDataStore.updateData { prefs ->
                                        pref.setter.invoke(prefs, true)
                                    }
                            }
                        },
                    )
                }
            }

            else -> {
                val value = pref.getter.invoke(preferences)
                LunocoSettingPreference(
                    preference = pref,
                    value = value,
                    onNavigate = viewModel.navigationManager::navigateTo,
                    onValueChange = { newValue ->
                        val effectiveValue = if (pref == AppPreference.PlayerBackendPref && newValue == PlayerBackend.MPV) {
                                    try {
                                        System.loadLibrary("mpv")
                                        System.loadLibrary("player")
                                        newValue
                                    } catch (_: UnsatisfiedLinkError) {
                                        scope.launch { showToast(context, "MPV is not supported on this device") }
                                        PlayerBackend.EXO_PLAYER
                                    }
                                } else newValue
                                val validation = pref.validate(preferences, effectiveValue)
                        when (validation) {
                            is PreferenceValidation.Invalid -> {
                                // TODO?
                                Toast
                                    .makeText(
                                        context,
                                        validation.message,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }

                            PreferenceValidation.Valid -> {
                                scope.launch(ExceptionHandler()) {
                                    preferences =
                                        viewModel.preferenceDataStore.updateData { prefs ->
                                            pref.setter(prefs, effectiveValue)
                                        }
                                }
                            }
                        }
                    },
                    interactionSource = interactionSource,
                    modifier = focusModifier,
                )
            }
        }
        }
    }

    if (showPinFlow && currentUser != null) {
        currentUser?.let { user ->
            SetPinFlow(
                currentPin = user.pin,
                onAddPin = { viewModel.setPin(user, it); showPinFlow = false },
                onRemovePin = { viewModel.setPin(user, null); showPinFlow = false },
                onDismissRequest = { showPinFlow = false },
            )
        }
    }

    SeerrDialog(
        seerrDialogMode = seerrDialogMode,
        seerrConnection = seerrConnection,
        onDismissRequest = { seerrDialogMode = SeerrDialogMode.None },
        seerrVm = seerrVm,
    )

    if (showQuickConnectDialog) {
        val quickConnectStatus by viewModel.quickConnectStatus.collectAsState(LoadingState.Pending)
        val successMessage = stringResource(R.string.quick_connect_success)

        LaunchedEffect(quickConnectStatus) {
            when (val status = quickConnectStatus) {
                LoadingState.Success -> {
                    Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                    showQuickConnectDialog = false
                }

                is LoadingState.Error -> {
                    val errorMessage = status.message ?: "Authorization failed"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                }

                else -> {}
            }
        }

        QuickConnectDialog(
            onSubmit = { code ->
                viewModel.authorizeQuickConnect(code)
            },
            onDismissRequest = {
                viewModel.resetQuickConnectStatus()
                showQuickConnectDialog = false
            },
        )
    }
    if (showVersionDialog) {
        val dismissVersion = { showVersionDialog = false; restoreVersionFocus?.invoke(); Unit }
        BackHandler(onBack = dismissVersion)
        LaunchedEffect(Unit) {
            if (UpdateChecker.ACTIVE) viewModel.fetchReleaseNotes()
        }
        val release by viewModel.releaseNotes.collectAsState()
        ScrollableDialog(
            onDismissRequest = dismissVersion,
        ) {
            item {
                if (!UpdateChecker.ACTIVE) {
                    // Local branding only; this fork has no upstream release feed.
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.jellylu_banner),
                            contentDescription = null,
                            modifier = Modifier.width(160.dp).height(90.dp),
                        )
                        Text(
                            text = stringResource(R.string.brand_name),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(text = installedVersionLabel, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = stringResource(R.string.jellylu_attribution),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    when (val r = release) {
                        is DataLoadingState.Error -> {
                            ErrorMessage(message = "Error", exception = r.exception)
                        }

                        DataLoadingState.Pending,
                        DataLoadingState.Loading,
                        -> {
                            LoadingPage()
                        }

                        is DataLoadingState.Success<Release> -> {
                            ReleaseNotes(r.data)
                        }
                    }
                }
            }
        }
    }
    if (showLocaleChoiceDialog) {
        LocaleChoiceDialog(
            onDismissRequest = { showLocaleChoiceDialog = false },
        )
    }
}

@Composable
fun PreferencesPage(
    initialPreferences: AppPreferences,
    preferenceScreenOption: PreferenceScreenOption,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        when (preferenceScreenOption) {
            PreferenceScreenOption.BASIC,
            PreferenceScreenOption.ADVANCED,
            PreferenceScreenOption.EXO_PLAYER,
            PreferenceScreenOption.MPV,
            PreferenceScreenOption.SCREENSAVER,
            PreferenceScreenOption.SKIP_SEGMENTS,
            PreferenceScreenOption.EXPERIMENTAL,
            PreferenceScreenOption.HDR_FORMATS,
            -> {
                PreferencesContent(
                    initialPreferences,
                    preferenceScreenOption,
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .align(Alignment.TopStart),
                )
            }
        }
    }
}

data class CacheUsage(
    val imageMemoryUsed: Long,
    val imageMemoryMax: Long,
    val imageDiskUsed: Long,
)
