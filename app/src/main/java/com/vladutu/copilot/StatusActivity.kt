package com.vladutu.copilot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.vladutu.copilot.service.ListenerService
import com.vladutu.copilot.service.UiState
import com.vladutu.copilot.ui.StatusScreen
import kotlinx.coroutines.flow.MutableStateFlow

class StatusActivity : ComponentActivity() {

    private val requestNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored; system enforces the foreground notification anyway */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ensureNotificationPermission()
        startListenerService()

        setContent {
            MaterialTheme {
                Surface {
                    val flow = remember { ListenerService.state() ?: MutableStateFlow(UiState()) }
                    val state by flow.collectAsState()
                    StatusScreen(state = state)
                }
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startListenerService() {
        startForegroundService(Intent(this, ListenerService::class.java))
    }
}
