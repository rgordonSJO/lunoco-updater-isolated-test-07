package com.github.damontecres.wholphin.ui.preferences

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.*
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.colors.LunocoUiColors
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlin.math.roundToLong

/** Only provided around main-workspace rows, never around the specialized editor routes. */
data class LunocoSettingPresentation(
    val id: String,
    val description: String? = null,
    val restoreFocus: () -> Unit = {},
    val title: String? = null,
)
val LocalLunocoSettingPresentation = staticCompositionLocalOf<LunocoSettingPresentation?> { null }

@Composable
fun LunocoPreferenceRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    value: String? = null,
    checked: Boolean? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    extraDescription: String? = LocalLunocoSettingPresentation.current?.description,
    trailing: @Composable (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val presentation = LocalLunocoSettingPresentation.current
    val on = stringResource(R.string.lunoco_settings_on)
    val off = stringResource(R.string.lunoco_settings_off)
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp)
            .then(if (presentation != null) Modifier.testTag("settings-row-${presentation.id}") else Modifier)
            .onFocusChanged { focused = it.hasFocus }
            .border(2.dp, if (focused) LunocoUiColors.FocusOutline else Color.Transparent, shape)
            .background(if (focused) LunocoUiColors.CardFooter else Color.Transparent, shape)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                if (checked != null) {
                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                    stateDescription = if (checked) on else off
                }
            }
            .focusProperties { canFocus = true }
            .combinedClickable(
                interactionSource = interactionSource, indication = null,
                role = if (checked == null) Role.Button else Role.Switch,
                onClick = onClick, onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(presentation?.title ?: title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface)
            if (!summary.isNullOrBlank()) Text(summary, fontSize = 14.sp, lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!extraDescription.isNullOrBlank() && extraDescription != summary) Text(extraDescription,
                fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing()
        else if (checked != null) Text(if (checked) on else off, fontSize = 16.sp,
            color = if (checked) LunocoUiColors.Accent else MaterialTheme.colorScheme.onSurfaceVariant)
        else if (!value.isNullOrBlank()) Text(value, fontSize = 16.sp, lineHeight = 22.sp,
            modifier = Modifier.widthIn(max = 176.dp),
            color = MaterialTheme.colorScheme.onSurface)
        else if (selected) Text("✓", fontSize = 20.sp, color = LunocoUiColors.Accent)
    }
}

/** Choice focus is explicit; selection and dismissal both return to the exact keyed row. */
@Composable
internal fun LunocoChoiceSheet(
    title: String,
    labels: List<String>,
    selectedIndices: Set<Int>,
    initialIndex: Int,
    multiple: Boolean = false,
    descriptions: List<String> = emptyList(),
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(0, (labels.size - 1).coerceAtLeast(0)))
    val requester = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLunocoSettingPresentation provides null) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(.72f).heightIn(max = 440.dp)
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .3f), RoundedCornerShape(12.dp))
            .padding(20.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 12.dp))
            if (labels.isEmpty()) {
                LunocoPreferenceRow(stringResource(R.string.lunoco_settings_empty), onDismiss,
                    Modifier.focusRequester(requester))
            }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(labels, key = { index, _ -> index }) { index, label ->
                    LunocoPreferenceRow(
                        title = label, summary = descriptions.getOrNull(index)?.takeIf { it.isNotBlank() }, onClick = { onSelect(index) },
                        selected = index in selectedIndices,
                        checked = if (multiple) index in selectedIndices else null,
                        extraDescription = null,
                        modifier = Modifier.testTag("settings-choice-$index")
                            .then(if (index == initialIndex.coerceIn(0, (labels.size - 1).coerceAtLeast(0)))
                                Modifier.focusRequester(requester) else Modifier),
                    )
                }
            }
            LaunchedEffect(Unit) { withFrameNanos { }; requester.tryRequestFocus() }
        }
        }
    }
}

@Composable
internal fun LunocoChoicePreference(
    title: String, summary: String?, labels: List<String>, selectedIndex: Int,
    onValueChange: (Int) -> Unit, modifier: Modifier,
    interactionSource: MutableInteractionSource,
    descriptions: List<String> = emptyList(),
) {
    var open by remember { mutableStateOf(false) }
    val presentation = LocalLunocoSettingPresentation.current
    LunocoPreferenceRow(title, { open = true }, modifier, value = summary,
        interactionSource = interactionSource)
    if (open) LunocoChoiceSheet(title, labels, setOf(selectedIndex), selectedIndex,
        descriptions = descriptions,
        onSelect = { index ->
            onValueChange(index)
            open = false
            presentation?.restoreFocus?.invoke()
        },
        onDismiss = { open = false; presentation?.restoreFocus?.invoke() })
}

@Composable
private fun LunocoSliderPreference(
    preference: AppSliderPreference<*>, value: Long, onValueChange: (Long) -> Unit,
    modifier: Modifier, interactionSource: MutableInteractionSource,
) {
    val context = LocalContext.current
    var adjusting by remember { mutableStateOf(false) }
    var localValue by remember(value) { mutableLongStateOf(value.coerceIn(preference.min, preference.max)) }
    val change: (Long) -> Unit = {
        val next = it.coerceIn(preference.min, preference.max)
        if (next != localValue) { localValue = next; onValueChange(next) }
    }
    BackHandler(adjusting) { adjusting = false }
    val hint = stringResource(if (adjusting) R.string.lunoco_settings_adjusting else R.string.lunoco_settings_adjust)
    LunocoPreferenceRow(
        title = stringResource(preference.title), onClick = { adjusting = !adjusting },
        summary = hint, interactionSource = interactionSource,
        modifier = modifier
            .onFocusChanged { if (!it.hasFocus) adjusting = false }
            .onPreviewKeyEvent {
                if (adjusting && (it.key == Key.DirectionLeft || it.key == Key.DirectionRight)) {
                    if (it.type == KeyEventType.KeyDown) change(localValue +
                        if (it.key == Key.DirectionLeft) -preference.interval.toLong() else preference.interval.toLong())
                    true
                } else false
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(localValue.toFloat(),
                    preference.min.toFloat()..preference.max.toFloat(),
                    ((preference.max - preference.min) / preference.interval - 1).coerceAtLeast(0).toInt())
                setProgress { requested ->
                    val steps = ((requested - preference.min) / preference.interval).roundToLong()
                    change(preference.min + steps * preference.interval)
                    true
                }
            },
        trailing = {
            Column(Modifier.widthIn(max = 176.dp), horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(preference.summary(context, localValue) ?: localValue.toString(), fontSize = 16.sp,
                    color = if (adjusting) LunocoUiColors.Accent else MaterialTheme.colorScheme.onSurface)
                Canvas(Modifier.width(96.dp).height(4.dp)) {
                    val fraction = if (preference.max == preference.min) 0f
                        else (localValue - preference.min).toFloat() / (preference.max - preference.min)
                    drawRect(LunocoUiColors.ProgressTrack)
                    drawRect(LunocoUiColors.Accent, size = size.copy(width = size.width * fraction))
                }
            }
        },
    )
}

/** Uses the canonical getter/setter callbacks supplied by the existing Settings controller. */
@Suppress("UNCHECKED_CAST")
@Composable
fun LunocoSettingPreference(
    preference: AppPreference<*, Any>,
    value: Any?,
    onValueChange: (Any) -> Unit,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val context = LocalContext.current
    val title = stringResource(preference.title)
    when (preference) {
        is AppChoicePreference<*, *> -> {
            val pref = preference as AppChoicePreference<Any, Any>
            val labels = stringArrayResource(pref.displayValues).toList()
            val index = runCatching { pref.valueToIndex(value as Any) }.getOrDefault(-1)
            LunocoChoicePreference(title, labels.getOrNull(index) ?: stringResource(R.string.lunoco_settings_unavailable), labels, index,
                { onValueChange(pref.indexToValue(it)) }, modifier, interactionSource,
                pref.subtitles?.let { stringArrayResource(it).toList() }.orEmpty())
        }
        is AppMultiChoicePreference<*, *> -> {
            val pref = preference as AppMultiChoicePreference<Any, Any>
            val labels = stringArrayResource(pref.displayValues).toList()
            val selected = (value as List<Any>).toSet()
            var open by remember { mutableStateOf(false) }
            val presentation = LocalLunocoSettingPresentation.current
            val selection by rememberUpdatedState(selected)
            val onChange by rememberUpdatedState(onValueChange)
            LunocoPreferenceRow(title, { open = true }, modifier,
                summary = pref.summary(context, selected.toList()),
                value = selected.size.toString(), interactionSource = interactionSource)
            if (open) LunocoChoiceSheet(title, labels,
                pref.allValues.indices.filter { pref.allValues[it] in selected }.toSet(),
                pref.allValues.indexOfFirst { it in selected }.coerceAtLeast(0), multiple = true,
                onSelect = { index ->
                    val item = pref.allValues[index]
                    val next = selection.toMutableSet().apply { if (!add(item)) remove(item) }
                    onChange(next.toList())
                }, onDismiss = { open = false; presentation?.restoreFocus?.invoke() })
        }
        is AppSliderPreference<*> -> LunocoSliderPreference(preference, value as Long,
            { onValueChange(it) }, modifier, interactionSource)
        // Existing file/string/destination handling is retained, with Settings-only row styling.
        else -> ComposablePreference(preference, value, onValueChange, onNavigate,
            modifier, interactionSource)
    }
}
