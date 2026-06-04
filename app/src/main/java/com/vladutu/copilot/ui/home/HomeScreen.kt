package com.vladutu.copilot.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.launch.AppLauncher
import com.vladutu.copilot.service.UiState

private const val TILE_COUNT = 5

@Composable
fun HomeScreen(
    state: UiState,
    onOpenWaze: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenDestinations: () -> Unit,
    onOpenStatus: () -> Unit,
    onBackFromHome: () -> Unit,
) {
    BackHandler(onBack = onBackFromHome)

    // Knob twist (DPAD_LEFT/RIGHT) walks the five tiles linearly in reading
    // order: Waze → Maps → Playlists → Songs → Destinations. StatusPill is
    // touch-only — it's not in the knob rotation by design.
    val tileFocus = remember { List(TILE_COUNT) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusedIndex) {
        runCatching { tileFocus[focusedIndex].requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        if (focusedIndex < TILE_COUNT - 1) { focusedIndex++; true } else false
                    }
                    Key.DirectionLeft -> {
                        if (focusedIndex > 0) { focusedIndex--; true } else false
                    }
                    else -> false
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top row — outbound nav apps (2 tiles).
            Row(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HomeTile(
                    modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[0]),
                    label = stringResource(R.string.home_waze),
                    onClick = onOpenWaze,
                    packageName = AppLauncher.WAZE_PKG,
                    fallbackRes = R.drawable.ic_map_pin,
                )
                HomeTile(
                    modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[1]),
                    label = stringResource(R.string.home_maps),
                    onClick = onOpenMaps,
                    packageName = AppLauncher.MAPS_PKG,
                    fallbackRes = R.drawable.ic_map_pin,
                )
            }
            // Bottom row — saved-content lists (3 tiles).
            Row(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HomeTile(
                    modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[2]),
                    label = stringResource(R.string.home_playlists),
                    onClick = onOpenPlaylists,
                    fallbackIcon = Icons.Filled.PlaylistPlay,
                )
                HomeTile(
                    modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[3]),
                    label = stringResource(R.string.home_songs),
                    onClick = onOpenSongs,
                    fallbackIcon = Icons.Filled.MusicNote,
                )
                HomeTile(
                    modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[4]),
                    label = stringResource(R.string.home_destinations),
                    onClick = onOpenDestinations,
                    fallbackIcon = Icons.Filled.Place,
                )
            }
        }
        StatusPill(
            state = state,
            onClick = onOpenStatus,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
        )
    }
}
