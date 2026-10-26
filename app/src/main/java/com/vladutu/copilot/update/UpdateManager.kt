package com.vladutu.copilot.update

import com.vladutu.copilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * The self-update state machine behind the Home top-bar label. One check per process start
 * ([checkNow] from MainActivity); a tap on the label calls [install], which downloads the APK
 * and hands it to the system installer. Android's install sheet takes over from there, and on
 * success the process is replaced, so there is no "installed" state to model.
 *
 * Collaborators are plain functions so the state machine is testable without OkHttp or an
 * Android Context; [ServiceLocator] wires the real [UpdateChecker], [ApkDownloader] and
 * [ApkInstaller].
 */
class UpdateManager(
    private val scope: CoroutineScope,
    private val check: suspend () -> UpdateChecker.Update?,
    private val download: suspend (UpdateChecker.Update) -> File,
    private val install: (File) -> Unit,
) {

    sealed interface State {
        data object Idle : State
        data class Available(val update: UpdateChecker.Update, val error: String? = null) : State
        data class Downloading(val update: UpdateChecker.Update) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var installJob: Job? = null

    fun checkNow() {
        scope.launch {
            val update = check() ?: return@launch
            DiagnosticLog.i(TAG, "update available: ${update.version}")
            _state.value = State.Available(update)
        }
    }

    fun install() {
        val current = _state.value as? State.Available ?: return
        if (installJob?.isActive == true) return
        _state.value = State.Downloading(current.update)
        installJob = scope.launch {
            try {
                val apk = download(current.update)
                install(apk)
                _state.value = State.Available(current.update)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticLog.w(TAG, "update download failed", e)
                _state.value = State.Available(current.update, error = e.message ?: "download failed")
            }
        }
    }

    private companion object {
        const val TAG = "Update"
    }
}
