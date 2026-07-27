package com.vladutu.copilot.ui.status

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vladutu.copilot.R
import com.vladutu.copilot.config.Config
import com.vladutu.copilot.service.ConnState
import com.vladutu.copilot.service.RecentEvent
import com.vladutu.copilot.service.UiState
import com.vladutu.copilot.ui.ScreenHeader
import com.vladutu.copilot.ui.theme.LayoutMode
import com.vladutu.copilot.ui.theme.LocalLayoutMode
import com.vladutu.copilot.ui.theme.PilotOk
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs

/**
 * Health hero readable from the driver's seat (docs/design/img.png): a big tinted
 * connection card + clock-skew row on the left, recent events with aligned times
 * on the right. Connection state tracks the ntfy stream lifecycle, so it's honest
 * even when no message has arrived for days.
 */
@Composable
fun StatusScreen(state: UiState, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.status_title), onBack = onBack)
        if (LocalLayoutMode.current == LayoutMode.PORTRAIT) {
            // No width for side-by-side cards on an upright panel: hero (+skew) stacks
            // above the events list, keeping the same 38/62 share top-to-bottom.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ConnectionHero(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(38f),
                )
                state.skewSec?.let { SkewCard(it) }
                RecentEventsCard(
                    events = state.recent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(62f),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(38f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ConnectionHero(
                        state = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    state.skewSec?.let { SkewCard(it) }
                }
                RecentEventsCard(
                    events = state.recent,
                    modifier = Modifier
                        .weight(62f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/** The big tinted card: glow dot, state label, and uptime while connected. */
@Composable
private fun ConnectionHero(state: UiState, modifier: Modifier = Modifier) {
    val color = state.conn.color()

    // Tick so "listening for Xh Ym" advances while the screen is open.
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowSec = System.currentTimeMillis() / 1000L
        }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = color.copy(alpha = 0.08f).compositeOver(MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
            Text(stringResource(state.conn.labelRes()), style = MaterialTheme.typography.headlineMedium)
            state.subtitle(nowSec)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SkewCard(skewSec: Long) {
    val sign = if (skewSec >= 0) "+" else ""
    val valueColor = if (abs(skewSec) > Config.MAX_MESSAGE_AGE_SEC) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.status_clock_skew), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "$sign${skewSec}s",
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun RecentEventsCard(events: List<RecentEvent>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.status_recent_events).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (events.isEmpty()) {
                Text(
                    "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                for (event in events) {
                    EventRow(event)
                }
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Monospace so the times line up into a column across rows.
        Text(
            text = time,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (event.ok) PilotOk else MaterialTheme.colorScheme.error),
        )
        Text(
            text = event.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (event.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UiState.subtitle(nowSec: Long): String? = when (conn) {
    is ConnState.Connected -> connectedSinceSec?.let { since ->
        val mins = ((nowSec - since) / 60).coerceAtLeast(0)
        val h = mins / 60
        val m = mins % 60
        if (h > 0) stringResource(R.string.status_listening_hm, h, m)
        else stringResource(R.string.status_listening_m, m)
    }
    is ConnState.Reconnecting -> null
    is ConnState.Error -> conn.message
}

@Composable
private fun ConnState.color(): Color = when (this) {
    is ConnState.Connected -> PilotOk
    is ConnState.Reconnecting -> MaterialTheme.colorScheme.primary
    is ConnState.Error -> MaterialTheme.colorScheme.error
}

private fun ConnState.labelRes(): Int = when (this) {
    is ConnState.Connected -> R.string.status_connected
    is ConnState.Reconnecting -> R.string.status_connecting
    is ConnState.Error -> R.string.status_error
}
