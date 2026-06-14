package com.vladutu.copilot.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.BuildConfig
import com.vladutu.copilot.R
import com.vladutu.copilot.config.Config
import com.vladutu.copilot.service.ConnState
import com.vladutu.copilot.ui.ScreenHeader
import com.vladutu.copilot.ui.permissions.PermissionHelpers
import com.vladutu.copilot.service.RecentEvent
import com.vladutu.copilot.service.UiState
import com.vladutu.copilot.ui.theme.PilotOk
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs

@Composable
fun StatusScreen(state: UiState, onBack: () -> Unit, onOpenLogs: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader(title = "Status", onBack = onBack)
        // Connection state — ring-dot matches StatusPill.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = state.conn.color()
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f))
                    .padding(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
            Box(Modifier.size(12.dp))
            Text(state.conn.label(), style = MaterialTheme.typography.titleLarge)
        }

        // Clock skew (only after we've observed one message)
        state.skewSec?.let { skew ->
            val sign = if (skew >= 0) "+" else ""
            val skewColor = if (abs(skew) > Config.MAX_MESSAGE_AGE_SEC) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = "Clock skew: $sign${skew}s (phone − box)",
                style = MaterialTheme.typography.titleMedium,
                color = skewColor,
            )
        }

        // Recent events
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Recent events", style = MaterialTheme.typography.titleMedium)
            if (state.recent.isEmpty()) {
                Text(
                    "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                for (event in state.recent) {
                    EventRow(event)
                }
            }
        }

        Text(
            text = "topic: ${Config.NTFY_TOPIC.take(16)}…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(onClick = onOpenLogs) { Text("Diagnostic log") }

        // Now-playing (notification access) grant — shown only while access is missing.
        val ctx = LocalContext.current
        if (!PermissionHelpers.isNotificationAccessGranted(ctx)) {
            OutlinedButton(onClick = { PermissionHelpers.openNotificationAccessSettings(ctx) }) {
                Text(stringResource(R.string.grant_now_playing_access))
            }
        }
    }
}

@Composable
private fun EventRow(event: RecentEvent) {
    // LocalConfiguration is an observable locale source, unlike Locale.getDefault()
    // (NonObservableLocale lint error since Compose UI 1.9).
    val locale = LocalConfiguration.current.locales[0]
    val time = SimpleDateFormat("HH:mm:ss", locale)
        .format(Date(event.timeSec * 1000))
    Text(
        text = "$time  ${event.text}",
        style = MaterialTheme.typography.bodyLarge,
        color = if (event.ok) PilotOk else MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun ConnState.color(): Color = when (this) {
    is ConnState.Connected -> PilotOk
    is ConnState.Reconnecting -> MaterialTheme.colorScheme.primary
    is ConnState.Error -> MaterialTheme.colorScheme.error
}

private fun ConnState.label(): String = when (this) {
    is ConnState.Connected -> "Connected"
    is ConnState.Reconnecting -> "Reconnecting"
    is ConnState.Error -> "Error: ${this.message}"
}
