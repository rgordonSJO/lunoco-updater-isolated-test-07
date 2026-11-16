package com.github.damontecres.wholphin.ui.setup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.Release
import com.github.damontecres.wholphin.services.UpdateChecker
import com.github.damontecres.wholphin.ui.components.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val LocalLunocoUpdater = staticCompositionLocalOf<UpdateChecker?> { null }
internal fun Modifier.observeUpdateInteraction(updater: UpdateChecker?) =
    if (updater == null) this else onPreviewKeyEvent { updater.userInteraction(); false }

internal fun lunocoUpdateSurfaceAllowed(manual: Boolean, destination: Destination?): Boolean =
    if (manual) destination is Destination.Settings || destination == Destination.UpdateApp
    else destination is Destination.Home

/** Automatic offers occur only on a ready, idle Home; never in setup, playback or another window. */
@Composable
internal fun LunocoUpdateHost(destination: Destination?) {
    val updater = LocalLunocoUpdater.current ?: return
    val activity = LocalActivity.current ?: return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentDestination = rememberUpdatedState(destination)
    val state by updater.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Explicit retry avoids a surprise download/install when returning from system Settings.
    }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updater.cleanup()
        updater.dismiss()
        open = false
        // ACTION_VIEW results are not reliable installation-success evidence. No success claim.
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            if (destination is Destination.Settings || destination == Destination.UpdateApp)
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            updater.consumeNotice()
        }
    }
    LaunchedEffect(state.release, state.manual, state.checking, destination) {
        val checking = state.checking && state.manual
        if (state.release == null && !checking) { open = false; return@LaunchedEffect }
        if (open && state.release != null) updater.presented()
        val allowed = lunocoUpdateSurfaceAllowed(state.manual, destination)
        if (!allowed) {
            download?.cancel()
            open = false
            if (state.manual) { updater.cancelCheck(); updater.dismiss() }
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (!open) {
                if (!activity.window.decorView.hasWindowFocus()) updater.userInteraction()
                if (updater.session.mayPrompt(state.manual, updater.homeReady, updater.idleMs(),
                        true, activity.window.decorView.hasWindowFocus())) {
                    if (state.release != null) updater.presented()
                    open = true
                } else delay(1000)
            }
        }
    }
    val dismiss = {
        updater.cancelCheck()
        download?.cancel()
        open = false
        updater.dismiss()
        Unit
    }
    if (open && (state.release != null || (state.checking && state.manual))) {
        val release = state.release
        LunocoUpdateDialog(release, state.downloading, state.percent, dismiss,
            onUpdate = {
            if (release != null && download?.isActive != true) {
                if (!updater.canInstall()) {
                    try {
                        Toast.makeText(context, R.string.lunoco_updates_permission, Toast.LENGTH_LONG).show()
                        permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + context.packageName)))
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.lunoco_updates_install_failed, Toast.LENGTH_LONG).show()
                    }
                } else {
                    val manualOrigin = state.manual
                    download = scope.launch {
                    try {
                        val file = updater.download(release)
                        check(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                        check(lunocoUpdateSurfaceAllowed(manualOrigin, currentDestination.value))
                        installer.launch(updater.installationIntent(file))
                    } catch (ex: CancellationException) { throw ex }
                    catch (_: Exception) {
                        updater.cleanup()
                        dismiss()
                        Toast.makeText(context, R.string.lunoco_updates_install_failed, Toast.LENGTH_LONG).show()
                    }
                    }
                }
            }
        }, checking = state.checking && state.manual)
    }
}

@Composable
internal fun LunocoUpdateDialog(
    release: Release?,
    downloading: Boolean,
    percent: Int,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    checking: Boolean = false,
) {
    val later = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(.82f)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (checking) R.string.lunoco_updates_checking else R.string.lunoco_updates_title), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            if (release != null) {
                Text(stringResource(R.string.lunoco_updates_body))
                Text(stringResource(R.string.lunoco_updates_version, release.update?.versionName ?: release.version.toString()))
            }
            if (checking || downloading) {
                if (downloading) Text(stringResource(R.string.lunoco_updates_downloading, percent))
                if (downloading) LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                LunocoUpdateAction(R.string.lunoco_updates_cancel, onDismiss, Modifier.focusRequester(later))
            } else {
                if (!release?.body.isNullOrBlank()) {
                    Text(stringResource(R.string.lunoco_updates_notes), fontWeight = FontWeight.SemiBold)
                    // Plain text only: never execute links/HTML/markdown from release descriptions.
                    Text(release?.body.orEmpty().take(1200), maxLines = 8, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LunocoUpdateAction(R.string.lunoco_updates_now, onUpdate)
                    LunocoUpdateAction(R.string.lunoco_updates_later, onDismiss, Modifier.focusRequester(later))
                }
            }
        }
        }
        LaunchedEffect(checking, downloading) { later.requestFocus() }
    }
}

@Composable
private fun LunocoUpdateAction(@StringRes label: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick, modifier = modifier,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = Color(0xFF294A68),
            focusedContentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    ) { Text(stringResource(label)) }
}
