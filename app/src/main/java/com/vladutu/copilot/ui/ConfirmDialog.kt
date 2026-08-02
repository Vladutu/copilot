package com.vladutu.copilot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.isSyntheticKnobDuplicate
import com.vladutu.copilot.ui.theme.LocalTileAppearance

/**
 * Yes/no gate for destructive actions (item delete, Clear all), knob-operable the
 * same way as VoiceDialog's play/cancel confirm: the confirm button takes focus on
 * entry, a twist moves to Cancel via Compose's default focus search (fine for one
 * row of buttons), a press activates. Action-first button order for the same
 * glance-reads-left-to-right reason as there.
 *
 * A dialog is its own window and bypasses MainActivity.dispatchKeyEvent, so the
 * synthetic-duplicate filter is re-applied here (see KnobInput.kt) — without it
 * every knob press would land twice.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.confirm_delete_yes),
    dismissLabel: String = stringResource(R.string.confirm_delete_no),
) {
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { confirmFocus.requestFocus() } }

    AlertDialog(
        modifier = Modifier.onPreviewKeyEvent { isSyntheticKnobDuplicate(it.nativeKeyEvent) },
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogKnobButton(label = confirmLabel, focus = confirmFocus, onClick = onConfirm)
                DialogKnobButton(label = dismissLabel, focus = null, onClick = onDismiss)
            }
        },
    )
}

/**
 * Dialog button with an unmistakable knob-focus border — M3's default focus tint on
 * a TextButton is too subtle to read at a glance on the car screen. Shared by every
 * knob-operable dialog (this one and VoiceDialog).
 */
@Composable
internal fun DialogKnobButton(
    label: String,
    focus: FocusRequester?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    OutlinedButton(
        onClick = onClick,
        interactionSource = interaction,
        border = if (focused) {
            BorderStroke(LocalTileAppearance.current.focusBorderWidth, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        },
        modifier = if (focus != null) Modifier.focusRequester(focus) else Modifier,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
