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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.config.Config
import com.vladutu.copilot.service.ConnState
import com.vladutu.copilot.ui.BackHomeButton
import com.vladutu.copilot.service.RecentEvent
import com.vladutu.copilot.service.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun StatusScreen(state: UiState, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BackHomeButton(onBack)
        // Connection state
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).background(state.conn.color(), CircleShape))
            Box(Modifier.size(12.dp))
            Text(state.conn.label(), style = MaterialTheme.typography.headlineMedium)
        }

        // Clock skew (only after we've observed one message)
        state.skewSec?.let { skew ->
            val sign = if (skew >= 0) "+" else ""
            val color = if (abs(skew) > Config.MAX_MESSAGE_AGE_SEC) Color(0xFFC62828) else Color.Gray
            Text(
                text = "Clock skew: $sign${skew}s (phone − box)",
                style = MaterialTheme.typography.titleMedium,
                color = color,
            )
        }

        // Recent events
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Recent events", style = MaterialTheme.typography.titleMedium)
            if (state.recent.isEmpty()) {
                Text("—", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
            } else {
                for (event in state.recent) {
                    EventRow(event)
                }
            }
        }

        Text(
            text = "topic: ${Config.NTFY_TOPIC.take(16)}…",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )
    }
}

@Composable
private fun EventRow(event: RecentEvent) {
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        .format(Date(event.timeSec * 1000))
    Text(
        text = "$time  ${event.text}",
        style = MaterialTheme.typography.bodyLarge,
        color = if (event.ok) Color(0xFF2E7D32) else Color(0xFFC62828),
    )
}

private fun ConnState.color(): Color = when (this) {
    is ConnState.Connected -> Color(0xFF2E7D32)
    is ConnState.Reconnecting -> Color(0xFFF9A825)
    is ConnState.Error -> Color(0xFFC62828)
}

private fun ConnState.label(): String = when (this) {
    is ConnState.Connected -> "Connected"
    is ConnState.Reconnecting -> "Reconnecting"
    is ConnState.Error -> "Error: ${this.message}"
}
