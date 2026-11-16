package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.ui.components.ConfirmDialogContent

/** The existing confirmation policy/content, with bounded main-Settings dialog geometry. */
@Composable
internal fun LunocoSettingsConfirmation(
    title: String,
    body: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.widthIn(max = 520.dp).fillMaxWidth(.76f).height(320.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
        ) {
            ConfirmDialogContent(title, body, onCancel, onConfirm, Modifier.fillMaxSize())
        }
    }
}
