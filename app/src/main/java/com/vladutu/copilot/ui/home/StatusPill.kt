package com.vladutu.copilot.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.service.ConnState
import com.vladutu.copilot.service.UiState
import com.vladutu.copilot.ui.theme.PilotOk
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun StatusPill(state: UiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dotColor: Color = when (state.conn) {
        is ConnState.Connected -> PilotOk
        is ConnState.Reconnecting -> MaterialTheme.colorScheme.primary
        is ConnState.Error -> MaterialTheme.colorScheme.error
    }
    // LocalConfiguration is an observable locale source, unlike Locale.getDefault()
    // (NonObservableLocale lint error since Compose UI 1.9).
    val locale = LocalConfiguration.current.locales[0]
    val lastTime = state.recent.firstOrNull()?.timeSec?.let {
        SimpleDateFormat("HH:mm", locale).format(Date(it * 1000))
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 12dp ring (color at 18%) wrapping an 8dp solid inner dot — mirrors Pilot.
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = 0.18f))
                .padding(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        if (lastTime != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = lastTime,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
