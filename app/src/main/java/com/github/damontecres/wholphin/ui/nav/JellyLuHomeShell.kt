package com.github.damontecres.wholphin.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.ui.main.LocalJellyLuHomeCards
import com.github.damontecres.wholphin.ui.main.LocalJellyLuNavigationHasFocus
import com.github.damontecres.wholphin.ui.main.LocalJellyLuRailFocus
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.tryRequestFocus

/** Fixed browsing chrome, also used by Settings; other full-screen destinations bypass it. */
@Composable
fun JellyLuBrowseShell(
    destination: Destination,
    preferences: UserPreferences,
    user: JellyfinUser,
    server: JellyfinServer,
    viewModel: NavDrawerViewModel,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.serviceState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val entries = remember(state.items, state.moreItems) {
        (state.items + state.moreItems + NavDrawerItem.Favorites)
            .filter { it !is NavDrawerItem.More }.distinctBy { it.id }
    }
    val homeFocus = remember(user.id) { FocusRequester() }
    val contentFocus = remember(destination) { FocusRequester() }
    var navigationFocused by remember { mutableStateOf(false) }
    val navigationHasFocus = remember { { navigationFocused } }
    var profileMenu by remember { mutableStateOf(false) }

    val links = remember(entries) { jellyLuBrowseLinks(entries) }
    val overflow = remember(entries, links) {
        val primaryIds = links.map { it.library.id }.toSet()
        entries.filter { it.id !in primaryIds && it !is NavDrawerItem.Favorites }
    }
    val selectedLibraryId = viewModel.navigationManager.backStack.asReversed().firstNotNullOfOrNull { route ->
        when (route) {
            is Destination.MediaItem -> entries.filterIsInstance<ServerNavDrawerItem>()
                .firstOrNull { it.itemId == route.itemId }?.itemId
            is Destination.Recordings -> route.itemId
            else -> null
        }
    }
    JellyLuHomeBackFocus(destination is Destination.Home && !navigationFocused && !profileMenu, homeFocus)

    CompositionLocalProvider(
        LocalJellyLuHomeCards provides (destination is Destination.Home),
        // Retained local name for source compatibility; it now points to top navigation.
        LocalJellyLuRailFocus provides homeFocus,
        LocalJellyLuNavigationHasFocus provides navigationHasFocus,
    ) {
        Column(modifier.fillMaxSize()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 20.dp)
                    .onFocusChanged { navigationFocused = it.hasFocus }
                    .focusGroup(),
            ) {
                BrowseButton(onClick = { profileMenu = true }, modifier = Modifier.width(126.dp)) {
                    Image(
                        painterResource(R.drawable.lunoco_wordmark),
                        stringResource(R.string.brand_name),
                        Modifier.width(96.dp).height(26.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, stringResource(R.string.profile), Modifier.size(12.dp))
                }
                BrowseButton(
                    onClick = {
                        viewModel.navigationManager.selectHome()
                    },
                    selected = destination is Destination.Home,
                    modifier = Modifier.widthIn(min = JellyLuBrowseTab.HOME.widthDp.dp).focusRequester(homeFocus),
                ) { Icon(Icons.Default.Home, stringResource(R.string.home), Modifier.size(22.dp)) }
                val groups = remember(links) { lunocoBrowseGroups(links) }
                groups.forEach { group ->
                    key(group.tab) {
                        val openLink: (Int) -> Unit = { index ->
                            val link = group.children[index]
                            if (destination == link.destination) contentFocus.tryRequestFocus()
                            else viewModel.navigationManager.navigateToFromDrawer(link.destination)
                        }
                        if (group.hasMenu) {
                            BrowseDropdown(
                                label = stringResource(group.tab.label),
                                choices = group.children.map {
                                    stringResource(if (it.tab == JellyLuBrowseTab.LIVE_TV) R.string.lunoco_guide else it.tab.label)
                                },
                                selectedIndex = group.children.indexOfFirst { selectedLibraryId == it.library.itemId },
                                onSelect = openLink,
                            )
                        } else {
                            val link = group.children.single()
                            BrowseButton(
                                onClick = { openLink(0) },
                                selected = selectedLibraryId == link.library.itemId,
                                modifier = Modifier.widthIn(min = group.tab.widthDp.dp),
                            ) { Text(stringResource(group.tab.label), fontSize = 15.sp, maxLines = 1) }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                BrowseButton({
                    viewModel.navigationManager.navigateToFromDrawer(Destination.Search())
                }, selected = destination is Destination.Search, modifier = Modifier.width(34.dp)) {
                    Icon(Icons.Default.Search, stringResource(R.string.search), Modifier.size(20.dp))
                }
                BrowseButton({
                    if (destination !is Destination.Settings) {
                        viewModel.navigationManager.navigateTo(Destination.Settings(PreferenceScreenOption.BASIC))
                    }
                }, selected = destination is Destination.Settings, modifier = Modifier.width(34.dp)) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings), Modifier.size(20.dp))
                }
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (destination is Destination.Home) 0.dp else 24.dp)
                    .focusRestorer().focusRequester(contentFocus).focusGroup(),
            ) {
                DestinationContent(destination, preferences, onClearBackdrop, Modifier.fillMaxSize())
            }
        }
    }
    if (profileMenu) {
        val profileLabel = stringResource(R.string.profile)
        val favoritesLabel = stringResource(R.string.favorites)
        val nowPlayingLabel = stringResource(R.string.now_playing)
        val choices = buildList<Pair<String, () -> Unit>> {
            add(profileLabel to {
                profileMenu = false
                viewModel.navigateToSetup(SetupDestination.UserList(server))
            })
            add(favoritesLabel to {
                profileMenu = false
                viewModel.navigationManager.navigateToFromDrawer(Destination.Favorites)
            })
            addAll(overflow.map { entry ->
                entry.name(context) to {
                    profileMenu = false
                    val target = if (entry is ServerNavDrawerItem) {
                        JellyLuBrowseLink(JellyLuBrowseTab.HOME, entry).destination
                    } else null
                    if (target != null) viewModel.navigationManager.navigateToFromDrawer(target)
                    else viewModel.onClickDrawerItem(entries.indexOf(entry), entry)
                }
            })
            if (state.nowPlayingEnabled) add(nowPlayingLabel to {
                profileMenu = false
                viewModel.navigationManager.navigateTo(Destination.NowPlaying)
            })
        }
        BrowseChoiceDialog(stringResource(R.string.brand_name), choices, { profileMenu = false })
    }
}

/** Shared with the Home focus test; preserves the existing Back-to-navigation behavior. */
@Composable
internal fun JellyLuHomeBackFocus(enabled: Boolean, homeFocus: FocusRequester) {
    BackHandler(enabled = enabled) { homeFocus.tryRequestFocus() }
}

@Composable
private fun BrowseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        modifier = modifier.height(34.dp),
        content = content,
    )
}

@Composable
private fun BrowseChoiceDialog(
    title: String,
    choices: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.width(440.dp).heightIn(max = 400.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .focusGroup(),
        ) {
            item {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp))
            }
            if (choices.isEmpty()) item { Text(stringResource(R.string.no_results)) }
            itemsIndexed(choices, key = { index, _ -> index }) { index, choice ->
                ListItem(selected = false, onClick = choice.second,
                    headlineContent = { Text(choice.first) },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
            }
        }
        LaunchedEffect(Unit) { if (choices.isNotEmpty()) firstFocus.tryRequestFocus() }
    }
}
