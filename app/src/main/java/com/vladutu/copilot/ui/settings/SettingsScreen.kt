package com.vladutu.copilot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.ui.ScreenHeader
import com.vladutu.copilot.ui.permissions.PermissionHelpers

@Composable
fun SettingsScreen(
    autoStart: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    onOpenLogs: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_title), onBack = onBack)

        // Auto-start toggle row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_autostart_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            Switch(checked = autoStart, onCheckedChange = onAutoStartChange)
        }

        // Now-playing (notification access) grant — shown only while access is missing.
        if (!PermissionHelpers.isNotificationAccessGranted(ctx)) {
            OutlinedButton(onClick = { PermissionHelpers.openNotificationAccessSettings(ctx) }) {
                Text(stringResource(R.string.grant_now_playing_access))
            }
        }

        OutlinedButton(onClick = onOpenLogs) {
            Text(stringResource(R.string.settings_diagnostic_log))
        }
    }
}
