package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.ui.theme.colors.LunocoUiColors
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.flow.first

/** Non-observable focus bookkeeping; ordinary row focus does not rebuild the workspace. */
private class SettingsFocusMemory {
    var details = false
    val rows = mutableMapOf<String, String>()
    val ordinals = mutableMapOf<String, Int>()
    companion object {
        val Saver = mapSaver(
            save = { memory: SettingsFocusMemory ->
                buildMap<String, Any> {
                    put("details", memory.details)
                    memory.rows.forEach { (k, v) -> put("row:$k", v) }
                    memory.ordinals.forEach { (k, v) -> put("ordinal:$k", v) }
                }
            },
            restore = { saved ->
                SettingsFocusMemory().apply {
                    details = saved["details"] as? Boolean ?: false
                    saved.forEach { (k, v) ->
                        if (k.startsWith("row:")) rows[k.removePrefix("row:")] = v as String
                        if (k.startsWith("ordinal:")) ordinals[k.removePrefix("ordinal:")] = v as Int
                    }
                }
            },
        )
    }
}

internal fun restoredSettingId(entries: List<LunocoSettingEntry>, id: String?, ordinal: Int): String? =
    entries.firstOrNull { it.id == id }?.id ?: entries.getOrNull(ordinal.coerceIn(0, (entries.size - 1).coerceAtLeast(0)))?.id

/**
 * The route facade retains existing Navigation3 entry state. No action/data load is performed here.
 * Row content is the existing controller; placement gates prevent requesting an uncomposed lazy row.
 */
@Composable
fun LunocoSettingsWorkspace(
    preferences: AppPreferences,
    screen: PreferenceScreenOption,
    modifier: Modifier = Modifier,
    message: String? = null,
    entriesFor: (LunocoSettingsCategory, AppPreferences, PreferenceScreenOption) -> List<LunocoSettingEntry> =
        { category, prefs, page -> LunocoSettingsCatalog.workspaceEntries(category, prefs, screen, page) },
    rowContent: @Composable (LunocoSettingEntry, Modifier, MutableInteractionSource) -> Unit,
) {
    val primary = screen == PreferenceScreenOption.BASIC
    val categories = remember(screen) { LunocoSettingsCatalog.categories(screen) }
    var categoryName by rememberSaveable(screen) {
        mutableStateOf((if (screen == PreferenceScreenOption.ADVANCED) LunocoSettingsCategory.PLAYBACK
            else LunocoSettingsCatalog.categoryFor(screen)).name)
    }
    val category = categories.firstOrNull { it.name == categoryName } ?: categories.first()
    var nested by rememberSaveable(screen) { mutableStateOf(LunocoSettingsCatalog.isNested(screen)) }
    val page = if (nested) screen else PreferenceScreenOption.BASIC
    val sectionKey = screen.name + ":" + category.name + ":" + page.name
    val memory = rememberSaveable(saver = SettingsFocusMemory.Saver) { SettingsFocusMemory() }
    val entries = entriesFor(category, preferences, page)
    val categoriesState = rememberLazyListState()
    val categoryRequesters = remember { LunocoSettingsCategory.entries.associateWith { FocusRequester() } }
    val categoryPlaced = remember { mutableStateSetOf<LunocoSettingsCategory>() }
    var categoryRequest by remember { mutableStateOf<LunocoSettingsCategory?>(null) }
    var detailRequest by remember { mutableStateOf<String?>(null) }
    val paneStates = rememberSaveableStateHolder()
    val enterDetails = {
        memory.details = true
        detailRequest = restoredSettingId(entries, memory.rows[sectionKey], memory.ordinals[sectionKey] ?: 0)
        if (detailRequest == null) categoryRequest = category
    }
    val returnToIndex = {
        memory.details = false
        categoryRequest = category
    }
    LaunchedEffect(Unit) {
        if (memory.details || nested) enterDetails() else categoryRequest = category
    }
    LaunchedEffect(categoryRequest) {
        categoryRequest?.let { target ->
            val index = categories.indexOf(target).coerceAtLeast(0)
            if (index !in categoriesState.layoutInfo.visibleItemsInfo.map { it.index }) categoriesState.scrollToItem(index)
            snapshotFlow { target in categoryPlaced }.first { it }
            categoryRequesters.getValue(target).tryRequestFocus()
            categoryRequest = null
        }
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 4.dp, vertical = 16.dp)) {
        Text(stringResource(if (primary) R.string.settings else R.string.advanced_settings), fontSize = 28.sp, lineHeight = 36.sp,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            LazyColumn(
                state = categoriesState, verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier.width(216.dp).fillMaxHeight().testTag("settings-categories"),
            ) {
                items(categories, key = { it.name }) { item ->
                    var focused by remember { mutableStateOf(false) }
                    DisposableEffect(item) { onDispose { categoryPlaced.remove(item) } }
                    val shape = RoundedCornerShape(8.dp)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .testTag("settings-category-${item.name}")
                            .focusRequester(categoryRequesters.getValue(item))
                            .onFocusChanged { focused = it.isFocused }
                            .onGloballyPositioned { categoryPlaced.add(item) }
                            .onPreviewKeyEvent {
                                if (it.key == Key.DirectionRight && it.type == KeyEventType.KeyDown) { enterDetails(); true } else false
                            }
                            .border(2.dp, if (focused) LunocoUiColors.FocusOutline else Color.Transparent, shape)
                            .background(if (focused) LunocoUiColors.CardFooter else Color.Transparent, shape)
                            .semantics { selected = item == category }
                            .focusProperties { canFocus = true }
                            .clickable(role = Role.Tab, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                categoryName = item.name
                                nested = false
                                memory.details = true
                                // Resolve after the new category's descriptors are composed.
                                detailRequest = "@enter"
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.width(3.dp).height(20.dp).background(
                            if (item == category) LunocoUiColors.Accent else Color.Transparent, shape))
                        Text(stringResource(LunocoSettingsCatalog.categoryTitle(item, screen)), fontSize = 16.sp, lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    }
                }
            }
            paneStates.SaveableStateProvider(sectionKey) {
                val listState = rememberLazyListState()
                val requesters = remember { mutableMapOf<String, FocusRequester>() }
                val placed = remember { mutableStateSetOf<String>() }
                val ids = entries.map { it.id }
                LaunchedEffect(ids) {
                    if (memory.details && memory.rows[sectionKey] !in ids && detailRequest == null) detailRequest = "@removed"
                }
                LaunchedEffect(detailRequest, ids) {
                    val pending = detailRequest ?: return@LaunchedEffect
                    val target = if (pending.startsWith("@")) restoredSettingId(entries, memory.rows[sectionKey],
                        memory.ordinals[sectionKey] ?: 0) else restoredSettingId(entries, pending, memory.ordinals[sectionKey] ?: 0)
                    if (target == null) { detailRequest = null; returnToIndex(); return@LaunchedEffect }
                    val index = entries.indexOfFirst { it.id == target }
                    if (index !in listState.layoutInfo.visibleItemsInfo.map { it.index }) listState.scrollToItem(index)
                    snapshotFlow { target in placed }.first { it }
                    // Let detaching the removed focus target finish invalidating its owner.
                    if (pending == "@removed") withFrameNanos { }
                    requesters[target]?.tryRequestFocus()
                    detailRequest = null
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    val heading = if (nested) when (screen) {
                        PreferenceScreenOption.EXO_PLAYER -> R.string.exoplayer_options
                        PreferenceScreenOption.MPV -> R.string.mpv_options
                        PreferenceScreenOption.HDR_FORMATS -> R.string.hdr_formats
                        PreferenceScreenOption.SKIP_SEGMENTS -> R.string.skip_behavior
                        PreferenceScreenOption.EXPERIMENTAL -> R.string.experimental_settings
                        else -> category.title
                    } else LunocoSettingsCatalog.categoryTitle(category, screen)
                    Text(stringResource(heading), fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                    val scopeLabel = if (primary) when (category) {
                        LunocoSettingsCategory.PLAYBACK -> R.string.lunoco_settings_essential_playback_hint
                        LunocoSettingsCategory.PROFILES -> R.string.lunoco_settings_connections_hint
                        else -> R.string.lunoco_settings_about_hint
                    } else when (category) {
                        LunocoSettingsCategory.AUDIO -> R.string.lunoco_settings_audio_scope
                        LunocoSettingsCategory.PROFILES -> R.string.lunoco_settings_connection_scope
                        LunocoSettingsCategory.SUPPORT -> R.string.lunoco_settings_support_scope
                        LunocoSettingsCategory.HOME -> R.string.lunoco_settings_home_scope
                        LunocoSettingsCategory.PLAYBACK -> R.string.lunoco_settings_player_scope
                        else -> R.string.lunoco_settings_device_scope
                    }
                    Text(stringResource(scopeLabel), fontSize = 14.sp, lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
                    if (entries.isEmpty()) Text(stringResource(R.string.lunoco_settings_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(
                        state = listState, contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize().testTag("settings-detail-pane").focusRestorer().focusGroup(),
                    ) {
                        itemsIndexed(entries, key = { _, entry -> entry.id }, contentType = { _, _ -> "preference" }) { index, entry ->
                            val requester = remember(entry.id) { requesters.getOrPut(entry.id) { FocusRequester() } }
                            val interactions = remember { MutableInteractionSource() }
                            DisposableEffect(entry.id) { onDispose { placed.remove(entry.id); requesters.remove(entry.id) } }
                            Column {
                                if (index == 0 || entries[index - 1].section != entry.section) {
                                    Text(stringResource(entry.section), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                                }
                                CompositionLocalProvider(LocalLunocoSettingPresentation provides LunocoSettingPresentation(
                                    entry.id, entry.description?.let { stringResource(it) },
                                    restoreFocus = { detailRequest = entry.id },
                                    title = stringResource(entry.title),
                                )) {
                                    rowContent(entry, Modifier.focusRequester(requester)
                                        .onFocusChanged {
                                            if (it.hasFocus) {
                                                memory.details = true
                                                memory.rows[sectionKey] = entry.id
                                                memory.ordinals[sectionKey] = index
                                            }
                                        }
                                        .onGloballyPositioned { placed.add(entry.id) }
                                        .onKeyEvent {
                                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) {
                                                returnToIndex(); true
                                            } else false
                                        }, interactions)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
