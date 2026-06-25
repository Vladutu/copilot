package com.vladutu.copilot.ui.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import com.vladutu.copilot.R

@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasOverlay) {
        content()
    } else {
        OverlayPrompt(onGrant = { context.openOverlaySettings() })
    }
}

@Composable
private fun OverlayPrompt(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.overlay_permission_title),
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.overlay_permission_body),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onGrant,
            modifier = Modifier.height(72.dp),
            contentPadding = PaddingValues(horizontal = 32.dp)
        ) {
            Text(stringResource(R.string.grant), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun Context.openOverlaySettings() {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
