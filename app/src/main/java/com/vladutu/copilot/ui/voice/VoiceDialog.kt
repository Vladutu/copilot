package com.vladutu.copilot.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vladutu.copilot.R
import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.isSyntheticKnobDuplicate
import com.vladutu.copilot.ui.DialogKnobButton
import kotlinx.coroutines.CancellationException

/** What a transcript resolved to: [label] fills the confirm question's %1$s slot. */
data class VoiceTarget<T>(val value: T, val label: String)

private sealed interface VoicePhase {
    data class Listening(val partial: String?) : VoicePhase
    data class Resolving(val transcript: String) : VoicePhase
    data class Ready(val label: String) : VoicePhase
    data class Error(val message: String) : VoicePhase
}

/**
 * Returns a launcher for a voice-tile tap: runs [onReady] once RECORD_AUDIO is
 * granted, asking the system for it on first use and toasting on denial.
 */
@Composable
fun rememberMicPermissionRequest(onReady: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val deniedText = stringResource(R.string.voice_mic_denied)
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onReady()
        } else {
            Toast.makeText(context, deniedText, Toast.LENGTH_LONG).show()
        }
    }
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onReady() else request.launch(Manifest.permission.RECORD_AUDIO)
    }
}

/**
 * Speech capture with a confirm gate: listens the moment it opens, shows the live
 * partial transcript, maps the final transcript through [resolve] (identity for
 * Discover keywords; a YT Music search for Songs), and asks before acting —
 * recognition mishears, and cleanup would otherwise be a delete per miss. [resolve]
 * returning null shows [notFoundRes]; a throw shows a generic search failure.
 *
 * Knob care: a dialog is its own window, so neither KnobPagedGrid's twist handler nor
 * MainActivity's synthetic-duplicate filter runs here. Twists fall back to Compose's
 * default focus search — fine for a single row of buttons — and the duplicate filter
 * is re-applied via onPreviewKeyEvent on the dialog surface (an ancestor of whichever
 * button holds focus). Each phase's primary action takes focus on entry so a bare
 * knob press does the obvious thing: Cancel while listening/resolving, confirm on a
 * result, Retry on an error.
 */
@Composable
fun <T> VoiceDialog(
    languageTag: String?,
    @StringRes titleRes: Int,
    @StringRes questionRes: Int,
    @StringRes confirmRes: Int,
    @StringRes notFoundRes: Int,
    resolve: suspend (String) -> VoiceTarget<T>?,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf<VoicePhase>(VoicePhase.Listening(null)) }
    // The resolved value lives outside VoicePhase so the phases stay unparameterized.
    var target by remember { mutableStateOf<VoiceTarget<T>?>(null) }
    var attempt by remember { mutableStateOf(0) }

    val unavailableMsg = stringResource(R.string.voice_unavailable)
    val noMatchMsg = stringResource(R.string.voice_error_no_match)
    val failedMsg = stringResource(R.string.voice_error_generic)
    val notFoundMsg = stringResource(notFoundRes)
    val resolveFailedMsg = stringResource(R.string.voice_resolve_failed)

    // One recognizer per attempt; Retry bumps [attempt] which disposes and restarts.
    DisposableEffect(attempt) {
        val recognizer = if (SpeechRecognizer.isRecognitionAvailable(context)) {
            phase = VoicePhase.Listening(null)
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            // No engine on the device (needs the Google app / Speech Services).
            phase = VoicePhase.Error(unavailableMsg)
            null
        }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                phase = if (heard.isEmpty()) {
                    VoicePhase.Error(noMatchMsg)
                } else {
                    VoicePhase.Resolving(heard)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val heard = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!heard.isNullOrBlank() && phase is VoicePhase.Listening) {
                    phase = VoicePhase.Listening(heard)
                }
            }

            override fun onError(error: Int) {
                // Engines sometimes emit a stray CLIENT/BUSY error after delivering a
                // result — never let that wipe a transcript we already hold.
                if (phase !is VoicePhase.Listening) return
                phase = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoicePhase.Error(noMatchMsg)
                    else -> VoicePhase.Error(failedMsg)
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

    // Resolve off the transcript; keyed on the phase object so a Retry that hears
    // the same words resolves again.
    LaunchedEffect(phase) {
        val resolving = phase as? VoicePhase.Resolving ?: return@LaunchedEffect
        phase = try {
            val found = resolve(resolving.transcript)
            if (found == null) {
                VoicePhase.Error(notFoundMsg)
            } else {
                target = found
                VoicePhase.Ready(found.label)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLog.e(TAG, "resolve '${resolving.transcript}' failed", e)
            VoicePhase.Error(resolveFailedMsg)
        }
    }

    // Keyed on the phase's class, not the phase: partial-transcript updates keep the
    // Listening class and must not re-request focus on every spoken word.
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(phase::class) { runCatching { primaryFocus.requestFocus() } }

    var acted by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = Modifier.onPreviewKeyEvent { isSyntheticKnobDuplicate(it.nativeKeyEvent) },
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            when (val p = phase) {
                is VoicePhase.Listening -> Column(
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
                    if (p.partial != null) {
                        // Read-at-a-glance transcript: the dialog slot's default is the
                        // muted onSurfaceVariant — too gray on the car screen.
                        Text(
                            text = p.partial,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                is VoicePhase.Resolving -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Text(
                        text = stringResource(R.string.voice_searching, p.transcript),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
                is VoicePhase.Ready -> Text(
                    text = stringResource(questionRes, p.label),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                is VoicePhase.Error -> Text(
                    text = p.message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        confirmButton = {
            // Both buttons live in this one slot so the action reads first and Cancel
            // second — M3's dismiss-then-confirm order is the wrong way around for a
            // glance on the car screen.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (phase) {
                    is VoicePhase.Listening, is VoicePhase.Resolving -> Unit
                    is VoicePhase.Ready -> DialogKnobButton(
                        label = stringResource(confirmRes),
                        focus = primaryFocus,
                        onClick = {
                            val ready = target
                            if (!acted && ready != null) {
                                acted = true
                                onConfirm(ready.value)
                                onDismiss()
                            }
                        },
                    )
                    is VoicePhase.Error -> DialogKnobButton(
                        label = stringResource(R.string.discover_retry),
                        focus = primaryFocus,
                        onClick = { attempt++ },
                    )
                }
                DialogKnobButton(
                    label = stringResource(R.string.confirm_delete_no),
                    focus = if (phase is VoicePhase.Ready || phase is VoicePhase.Error) null else primaryFocus,
                    onClick = onDismiss,
                )
            }
        },
    )
}

private const val TAG = "VoiceDialog"
