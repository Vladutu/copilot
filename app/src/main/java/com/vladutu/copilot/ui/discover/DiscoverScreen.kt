package com.vladutu.copilot.ui.discover

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.discover.DiscoverRepository
import com.vladutu.copilot.discover.SearchException
import com.vladutu.copilot.discover.YtMusicUrls
import com.vladutu.copilot.launch.AppLauncher
import com.vladutu.copilot.ui.KnobPagedGrid
import com.vladutu.copilot.ui.MediaRowTile
import com.vladutu.copilot.ui.ScreenHeader
import kotlinx.coroutines.launch

/**
 * Discover home: one tile per category. Categories are authored in Pilot; here they
 * can only be used or deleted. Nothing on this screen writes to history — discovery
 * is ephemeral by design (spec 2026-06-11-discover).
 */
@Composable
fun DiscoverScreen(
    categories: List<String>,
    repository: DiscoverRepository,
    launcher: AppLauncher,
    onBrowse: (String) -> Unit,
    onDelete: (String) -> Unit,
    onLaunched: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var mixBusyFor by remember { mutableStateOf<String?>(null) }
    // Resolved during composition (lint: LocalContextGetResourceValueCall) so the
    // text tracks configuration changes; the callback below only captures the value.
    val mixFailedText = stringResource(R.string.discover_mix_failed)

    fun playMix(keyword: String) {
        if (mixBusyFor != null) return
        mixBusyFor = keyword
        scope.launch {
            try {
                val seed = repository.mixSeed(keyword)
                if (seed == null) {
                    Toast.makeText(context, mixFailedText, Toast.LENGTH_LONG).show()
                } else {
                    when (val result = launcher.launchYtMusic(YtMusicUrls.radioMix(seed.videoId))) {
                        is AppLauncher.Result.Ok -> onLaunched()
                        is AppLauncher.Result.Failed ->
                            Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: SearchException) {
                DiagnosticLog.e(TAG, "mix for '$keyword' failed", e)
                Toast.makeText(context, mixFailedText, Toast.LENGTH_LONG).show()
            } finally {
                mixBusyFor = null
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.home_discover), onBack = onBack)

        if (categories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.empty_discover), style = MaterialTheme.typography.titleLarge)
            }
        } else {
            KnobPagedGrid(
                items = categories,
                resetKey = categories.firstOrNull(),
                stopsPerItem = 2,
                modifier = Modifier.weight(1f),
            ) { keyword, requesters ->
                MediaRowTile(
                    modifier = Modifier.fillMaxSize(),
                    label = keyword,
                    onClick = { onBrowse(keyword) },
                    onLongPress = { pendingDelete = keyword },
                    focusRequester = requesters?.get(0),
                    fallbackIcon = Icons.Filled.Explore,
                    trailing = {
                        PlayMixButton(
                            busy = mixBusyFor == keyword,
                            focus = requesters?.get(1),
                            onClick = { playMix(keyword) },
                        )
                    },
                )
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message, target)) },
            confirmButton = {
                TextButton(onClick = { onDelete(target); pendingDelete = null }) {
                    Text(stringResource(R.string.confirm_delete_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.confirm_delete_no))
                }
            },
        )
    }
}

@Composable
private fun PlayMixButton(
    busy: Boolean,
    focus: FocusRequester?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        modifier = Modifier
            .size(56.dp)
            .let { if (focus != null) it.focusRequester(focus) else it }
            // Always enabled: disabling would drop knob focus mid-launch. Reentry
            // while busy is guarded in playMix().
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (focused) BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play mix",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

private const val TAG = "Discover"
