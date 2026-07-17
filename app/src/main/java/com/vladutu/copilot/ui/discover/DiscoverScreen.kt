package com.vladutu.copilot.ui.discover

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Mic
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
import androidx.core.content.ContextCompat
import com.vladutu.copilot.R
import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.discover.DiscoverRepository
import com.vladutu.copilot.discover.SearchException
import com.vladutu.copilot.discover.YtMusicUrls
import com.vladutu.copilot.launch.AppLauncher
import com.vladutu.copilot.nowplaying.NowPlaying
import com.vladutu.copilot.settings.VoiceLanguages
import com.vladutu.copilot.ui.KnobPagedGrid
import com.vladutu.copilot.ui.MediaRowTile
import com.vladutu.copilot.ui.NowPlayingStrip
import com.vladutu.copilot.ui.ScreenHeader
import com.vladutu.copilot.ui.theme.LocalTileAppearance
import kotlinx.coroutines.launch

/**
 * Discover home: a fixed voice tile (speak a keyword to add it as a category), then
 * one tile per category. Categories are otherwise authored in Pilot; here they can
 * be added by voice, used, or deleted. Nothing on this screen writes to history —
 * discovery is ephemeral by design (spec 2026-06-11-discover).
 */
@Composable
fun DiscoverScreen(
    categories: List<String>,
    repository: DiscoverRepository,
    launcher: AppLauncher,
    voiceLanguage: String,
    onBrowse: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onLaunched: () -> Unit,
    nowPlaying: NowPlaying?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var mixBusyFor by remember { mutableStateOf<String?>(null) }
    var showVoice by remember { mutableStateOf(false) }
    // Resolved during composition (lint: LocalContextGetResourceValueCall) so the
    // text tracks configuration changes; the callback below only captures the value.
    val mixFailedText = stringResource(R.string.discover_mix_failed)
    val micDeniedText = stringResource(R.string.voice_mic_denied)

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showVoice = true
        } else {
            Toast.makeText(context, micDeniedText, Toast.LENGTH_LONG).show()
        }
    }

    fun startVoice() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) showVoice = true else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

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
        // bottom = 0: the now-playing strip sits flush at the screen edge.
        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.home_discover), onBack = onBack)

        // The voice tile keeps the grid non-empty even before Pilot has sent any
        // categories, so the old empty-state branch is gone.
        val items = remember(categories) {
            listOf<DiscoverItem>(DiscoverItem.Voice) + categories.map { DiscoverItem.Category(it) }
        }
        KnobPagedGrid(
            items = items,
            resetKey = categories.firstOrNull(),
            stopsPerItem = 2,
            modifier = Modifier.weight(1f),
        ) { item, requesters ->
            when (item) {
                is DiscoverItem.Voice -> MediaRowTile(
                    // No trailing bar here, so this item's second knob stop re-targets
                    // the card itself: requester 1 sits on the tile root and delegates
                    // to the same focusable surface as requester 0. Crossing the tile
                    // stays two detents like every other Discover tile — no dead stop.
                    modifier = Modifier
                        .fillMaxSize()
                        .let { if (requesters != null) it.focusRequester(requesters[1]) else it },
                    label = stringResource(R.string.discover_voice_tile),
                    onClick = { startVoice() },
                    focusRequester = requesters?.get(0),
                    fallbackIcon = Icons.Filled.Mic,
                )
                is DiscoverItem.Category -> MediaRowTile(
                    modifier = Modifier.fillMaxSize(),
                    label = item.keyword,
                    onClick = { onBrowse(item.keyword) },
                    onLongPress = { pendingDelete = item.keyword },
                    focusRequester = requesters?.get(0),
                    fallbackIcon = Icons.Filled.Explore,
                    trailing = {
                        PlayMixButton(
                            busy = mixBusyFor == item.keyword,
                            focus = requesters?.get(1),
                            onClick = { playMix(item.keyword) },
                        )
                    },
                )
            }
        }

        NowPlayingStrip(nowPlaying = nowPlaying)
    }

    if (showVoice) {
        VoiceAddDialog(
            languageTag = VoiceLanguages.tagFor(voiceLanguage),
            onAdd = onAdd,
            onDismiss = { showVoice = false },
        )
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
    val appearance = LocalTileAppearance.current
    // Full-width bar pinned at the tile bottom (redesign-spec §3d), knob stop 1.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .let { if (focus != null) it.focusRequester(focus) else it }
            // Always enabled: disabling would drop knob focus mid-launch. Reentry
            // while busy is guarded in playMix().
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (focused) BorderStroke(appearance.focusBorderWidth, MaterialTheme.colorScheme.primary)
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = stringResource(R.string.discover_play_mix),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Grid entries: the fixed voice tile first, then one entry per synced category. */
private sealed interface DiscoverItem {
    object Voice : DiscoverItem
    data class Category(val keyword: String) : DiscoverItem
}

private const val TAG = "Discover"
