package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.setup.SwitchServerContent
import com.github.damontecres.wholphin.ui.setup.SwitchUserContent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Reads the existing local server context; opening this route does not select an account. */
@HiltViewModel
class LunocoConnectionsViewModel @Inject constructor(
    repository: ServerRepository,
    val navigation: NavigationManager,
) : ViewModel() {
    val server = repository.currentServerFlow.stateIn(viewModelScope, SharingStarted.Eagerly, repository.currentServer)
}

/** Existing editors hosted in the browsing stack so non-mutating Back returns to Settings. */
@Composable
fun LunocoConnectionsPage(
    servers: Boolean,
    modifier: Modifier = Modifier,
    viewModel: LunocoConnectionsViewModel = hiltViewModel(),
) {
    val server by viewModel.server.collectAsState()
    if (servers) {
        SwitchServerContent(modifier, focusWhenResumed = true)
    } else {
        val currentServer = server
        if (currentServer != null) {
            var restoreServers by rememberSaveable { mutableStateOf(false) }
            val serversFocus = remember { FocusRequester() }
            var serversPlaced by remember { mutableStateOf(false) }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            val foreground = viewModel.navigation.backStack.lastOrNull() == Destination.ManageConnections()
            LaunchedEffect(restoreServers, foreground) {
                if (!restoreServers || !foreground) return@LaunchedEffect
                snapshotFlow { serversPlaced }.first { it }
                val state = lifecycle.currentStateFlow.first {
                    it == Lifecycle.State.RESUMED || !it.isAtLeast(Lifecycle.State.STARTED)
                }
                if (state != Lifecycle.State.RESUMED) return@LaunchedEffect
                withFrameNanos { }
                if (viewModel.navigation.backStack.lastOrNull() == Destination.ManageConnections() &&
                    viewModel.server.value?.id == currentServer.id &&
                    lifecycle.currentState == Lifecycle.State.RESUMED
                ) {
                    serversFocus.requestFocus()
                    restoreServers = false
                }
            }
            SwitchUserContent(
                currentServer,
                modifier,
                onSwitchServer = {
                    restoreServers = true
                    viewModel.navigation.navigateTo(Destination.ManageConnections(servers = true))
                },
                switchServerModifier = Modifier.focusRequester(serversFocus)
                    .onGloballyPositioned { serversPlaced = true },
            )
        } else {
            val first = remember { FocusRequester() }
            Column(modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.lunoco_settings_no_server))
                Button(onClick = { viewModel.navigation.navigateTo(Destination.ManageConnections(servers = true)) },
                    modifier = Modifier.focusRequester(first)) {
                    Text(stringResource(R.string.lunoco_settings_manage_servers))
                }
                Button(onClick = { viewModel.navigation.goBack() }) { Text(stringResource(R.string.lunoco_settings_return)) }
            }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                if (viewModel.server.value == null) first.requestFocus()
            }
        }
    }
}
