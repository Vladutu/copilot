package com.vladutu.copilot.ui.discover

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.isSyntheticKnobDuplicate
import com.vladutu.copilot.ui.theme.LocalTileAppearance

private sealed interface VoiceState {
    data class Listening(val partial: String?) : VoiceState
    data class Heard(val text: String) : VoiceState
    data class Error(val message: String) : VoiceState
}

/**
 * Speech-to-text capture for the Discover voice tile: starts listening the moment it
 * opens, shows the live partial transcript, then asks to confirm before anything is
 * saved — recognition mishears, and cleanup would otherwise be a long-press delete
 * per miss.
 *
 * Knob care: a dialog is its own window, so neither KnobPagedGrid's twist handler nor
 * MainActivity's synthetic-duplicate filter runs here. Twists fall back to Compose's
 * default focus search — fine for a single row of buttons — and the duplicate filter
 * is re-applied via onPreviewKeyEvent on the dialog surface (an ancestor of whichever
 * button holds focus). Each state's primary action takes focus on entry so a bare
 * knob press does the obvious thing: Cancel while listening, Add on a result, Retry
 * on an error.
 */
@Composable
fun VoiceAddDialog(
    languageTag: String?,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<VoiceState>(VoiceState.Listening(null)) }
    var attempt by remember { mutableStateOf(0) }

    val unavailableMsg = stringResource(R.string.voice_unavailable)
    val noMatchMsg = stringResource(R.string.voice_error_no_match)
    val failedMsg = stringResource(R.string.voice_error_generic)

    // One recognizer per attempt; Retry bumps [attempt] which disposes and restarts.
    DisposableEffect(attempt) {
        val recognizer = if (SpeechRecognizer.isRecognitionAvailable(context)) {
            state = VoiceState.Listening(null)
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            // No engine on the device (needs the Google app / Speech Services).
            state = VoiceState.Error(unavailableMsg)
            null
        }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                state = if (heard.isEmpty()) VoiceState.Error(noMatchMsg) else VoiceState.Heard(heard)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val heard = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!heard.isNullOrBlank() && state is VoiceState.Listening) {
                    state = VoiceState.Listening(heard)
                }
            }

            override fun onError(error: Int) {
                // Engines sometimes emit a stray CLIENT/BUSY error after delivering a
                // result — never let that wipe a transcript we already hold.
                if (state is VoiceState.Heard) return
                state = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceState.Error(noMatchMsg)
                    else -> VoiceState.Error(failedMsg)
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer?.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                if (languageTag != null) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                }
            },
        )
        onDispose { recognizer?.destroy() }
    }

    // Keyed on the state's class, not the state: partial-transcript updates keep the
    // Listening class and must not re-request focus on every spoken word.
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(state::class) { runCatching { primaryFocus.requestFocus() } }

    var added by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = Modifier.onPreviewKeyEvent { isSyntheticKnobDuplicate(it.nativeKeyEvent) },
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voice_dialog_title)) },
        text = {
            when (val s = state) {
                is VoiceState.Listening -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = stringResource(R.string.voice_listening),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (s.partial != null) {
                        Text(
                            text = s.partial,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                is VoiceState.Heard -> Text(
                    text = stringResource(R.string.voice_add_confirm, s.text),
                    style = MaterialTheme.typography.titleLarge,
                )
                is VoiceState.Error -> Text(
                    text = s.message,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        confirmButton = {
            when (val s = state) {
                is VoiceState.Listening -> Unit
                is VoiceState.Heard -> DialogKnobButton(
                    label = stringResource(R.string.voice_add_yes),
                    focus = primaryFocus,
                    onClick = {
                        if (!added) {
                            added = true
                            onAdd(s.text)
                            onDismiss()
                        }
                    },
                )
                is VoiceState.Error -> DialogKnobButton(
                    label = stringResource(R.string.discover_retry),
                    focus = primaryFocus,
                    onClick = { attempt++ },
                )
            }
        },
        dismissButton = {
            DialogKnobButton(
                label = stringResource(R.string.confirm_delete_no),
                focus = if (state is VoiceState.Listening) primaryFocus else null,
                onClick = onDismiss,
            )
        },
    )
}

/**
 * Dialog button with an unmistakable knob-focus border — M3's default focus tint on
 * a TextButton is too subtle to read at a glance on the car screen.
 */
@Composable
private fun DialogKnobButton(
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
