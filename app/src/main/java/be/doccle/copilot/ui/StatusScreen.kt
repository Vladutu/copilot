package be.doccle.copilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import be.doccle.copilot.config.Config
import be.doccle.copilot.service.ConnState
import be.doccle.copilot.service.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusScreen(state: UiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).background(state.conn.color(), CircleShape))
            Box(Modifier.size(12.dp))
            Text(state.conn.label(), style = MaterialTheme.typography.headlineMedium)
        }

        Column {
            Text("Last command", style = MaterialTheme.typography.titleMedium)
            Text(
                text = state.lastMessage?.let { msg ->
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        .format(Date(msg.ts * 1000))
                    "▶ ${msg.form} ${msg.id} · $time"
                } ?: "—",
                style = MaterialTheme.typography.headlineLarge,
            )
            state.lastLaunchError?.let {
                Text(
                    text = "Last error: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Red,
                )
            }
        }

        Text(
            text = "topic: ${Config.NTFY_TOPIC.take(16)}…",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )
    }
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
