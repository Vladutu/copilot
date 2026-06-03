package com.vladutu.copilot.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

private const val TILE_COUNT = 4

@Composable
fun HomeScreen(
    state: UiState,
    onOpenWaze: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenDestinations: () -> Unit,
    onOpenStatus: () -> Unit,
    onBackFromHome: () -> Unit,
) {
    BackHandler(onBack = onBackFromHome)

    // Knob twist (DPAD_LEFT/RIGHT) walks the four tiles linearly: Waze → Playlists →
    // Songs → Destinations. StatusPill is not in the rotation by design — it's
    // touch-only, not used while driving.
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
                .padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BigAppButton(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .focusRequester(tileFocus[0]),
                    packageName = AppLauncher.WAZE_PKG,
                    fallbackRes = R.drawable.ic_map_pin,
                    label = stringResource(R.string.home_waze),
                    onClick = onOpenWaze,
                )
                LabelTile(
                    modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[1]),
                    label = stringResource(R.string.home_playlists),
                    onClick = onOpenPlaylists,
                )
            }
            Row(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LabelTile(
                    modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[2]),
                    label = stringResource(R.string.home_songs),
                    onClick = onOpenSongs,
                )
                LabelTile(
                    modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[3]),
                    label = stringResource(R.string.home_destinations),
                    onClick = onOpenDestinations,
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
