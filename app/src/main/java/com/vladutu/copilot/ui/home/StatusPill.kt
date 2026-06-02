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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.service.ConnState
import com.vladutu.copilot.service.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusPill(state: UiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dotColor = when (state.conn) {
        is ConnState.Connected -> Color(0xFF2E7D32)
        is ConnState.Reconnecting -> Color(0xFFF9A825)
        is ConnState.Error -> Color(0xFFC62828)
    }
    val lastTime = state.recent.firstOrNull()?.timeSec?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it * 1000))
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(14.dp).background(dotColor, CircleShape))
        if (lastTime != null) {
            Spacer(Modifier.width(8.dp))
            Text(text = lastTime, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
