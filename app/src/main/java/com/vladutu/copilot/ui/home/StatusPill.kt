package com.vladutu.copilot.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.service.ConnState
import com.vladutu.copilot.service.UiState
import com.vladutu.copilot.ui.theme.PilotOk

/**
 * Home top-bar connection cluster (redesign-spec §3a): a status dot tinted by
 * [ConnState] + a short label. Tapping it opens the Status screen.
 */
@Composable
fun ConnectionStatus(state: UiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dotColor: Color = when (state.conn) {
        is ConnState.Connected -> PilotOk
        is ConnState.Reconnecting -> MaterialTheme.colorScheme.primary
        is ConnState.Error -> MaterialTheme.colorScheme.error
    }
    val label = when (state.conn) {
        is ConnState.Connected -> stringResource(R.string.status_connected)
        is ConnState.Reconnecting -> stringResource(R.string.status_connecting)
        is ConnState.Error -> stringResource(R.string.status_offline)
    }

    Row(
        modifier = modifier
            // Tap-only chrome: never a knob stop. Without this, the very first DPAD
            // event after launch (device still in touch mode, so nothing holds focus
            // yet) does its initial focus placement on the topmost-leftmost focusable —
            // this pill — instead of the Waze tile.
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 12dp ring (color at 18%) wrapping an 8dp solid inner dot — mirrors Pilot.
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(dotColor.copy(alpha = 0.18f), CircleShape)
                .padding(2.dp),
        ) {
            Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
