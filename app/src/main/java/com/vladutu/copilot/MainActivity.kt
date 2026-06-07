package com.vladutu.copilot

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vladutu.copilot.bubble.BubbleController
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.launch.AppLauncher
import com.vladutu.copilot.service.ListenerService
import com.vladutu.copilot.ui.diagnostics.LogsScreen
import com.vladutu.copilot.ui.home.HomeScreen
import com.vladutu.copilot.ui.lists.SavedListScreen
import com.vladutu.copilot.ui.permissions.PermissionGate
import com.vladutu.copilot.ui.status.StatusScreen
import com.vladutu.copilot.ui.theme.CopilotDriveTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startListenerService()
        setContent {
            CopilotDriveTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PermissionGate {
                        CopilotNav(::leaveToOtherApp)
                    }
                }
            }
        }
    }

    private fun startListenerService() {
        startForegroundService(Intent(this, ListenerService::class.java))
    }

    override fun onResume() {
        super.onResume()
        BubbleController.onActivityResumed(this)
    }

    override fun onPause() {
        super.onPause()
        BubbleController.onActivityPaused(this)
    }

    // The BMW iDrive knob arrives via the carbox's CarPlay bridge as one set of
    // events from device "gaei" (src=0x301). The system then re-injects a
    // second, synthetic copy from a nameless device (src=0x101) for
    // DPAD_CENTER / BACK / BUTTON_1 — without filtering, every knob press and
    // every back press would fire twice. Drop the synthetic copy.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isSyntheticDuplicate(event)) return true
        return super.dispatchKeyEvent(event)
    }

    private fun isSyntheticDuplicate(event: KeyEvent): Boolean {
        val hasNamedDevice = !event.device?.name.isNullOrEmpty()
        if (hasNamedDevice) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_BUTTON_1 -> true
            else -> false
        }
    }

    private fun leaveToOtherApp() {
        BubbleController.requestShow(this)
        moveTaskToBack(true)
    }
}

@Composable
private fun CopilotNav(onLeftToOtherApp: () -> Unit) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as CopilotApp
    val launcher = remember { AppLauncher(context) }

    // A successful launch sends Copilot to the back, so a failure leaves this UI
    // in front — surface the reason as a toast instead of swallowing it silently.
    fun launchOrReport(result: AppLauncher.Result, onOk: () -> Unit) {
        when (result) {
            is AppLauncher.Result.Ok -> onOk()
            is AppLauncher.Result.Failed ->
                Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
        }
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            val uiState by ListenerService.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = uiState,
                onOpenWaze = { launchOrReport(launcher.openWazeApp()) { onLeftToOtherApp() } },
                onOpenMaps = { launchOrReport(launcher.openMapsApp()) { onLeftToOtherApp() } },
                onOpenPlaylists = { nav.navigate("list/playlist") },
                onOpenSongs = { nav.navigate("list/song") },
                onOpenDestinations = { nav.navigate("list/destination") },
                onOpenRadio = { nav.navigate("list/radio") },
                onOpenStatus = { nav.navigate("status") },
                onBackFromHome = onLeftToOtherApp,
            )
        }

        composable("list/{form}") { entry ->
            val formArg = entry.arguments?.getString("form") ?: return@composable
            val form = Form.fromWire(formArg) ?: return@composable
            val items by app.locator.historyRepository.itemsFor(form)
                .collectAsStateWithLifecycle(emptyList())
            SavedListScreen(
                items = items,
                form = form,
                artworkCache = app.locator.artworkCache,
                onTap = { item ->
                    launchOrReport(launcher.replay(item)) {
                        app.applicationScope.launch {
                            app.locator.historyRepository.touch(item.form, item.id)
                        }
                        onLeftToOtherApp()
                    }
                },
                onDelete = { item ->
                    app.applicationScope.launch {
                        app.locator.historyRepository.delete(item.form, item.id)
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable("status") {
            val uiState by ListenerService.state.collectAsStateWithLifecycle()
            StatusScreen(
                state = uiState,
                onBack = { nav.popBackStack() },
                onOpenLogs = { nav.navigate("logs") },
            )
        }

        composable("logs") {
            LogsScreen(onBack = { nav.popBackStack() })
        }
    }
}
