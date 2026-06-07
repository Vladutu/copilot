package com.vladutu.copilot.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Radio
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
import androidx.compose.ui.graphics.vector.ImageVector
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

// Waze + Maps + Playlists + Songs + Places + Radio. Knob walks all six.
private const val TILE_COUNT = 6
private const val MEDIA_COLUMNS = 2

private data class MediaTile(val labelRes: Int, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun HomeScreen(
    state: UiState,
    onOpenWaze: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenDestinations: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenStatus: () -> Unit,
    onBackFromHome: () -> Unit,
) {
    BackHandler(onBack = onBackFromHome)

    // Knob twist (DPAD_LEFT/RIGHT) walks the six tiles linearly in reading order:
    // Waze → Maps → Playlists → Songs → Places → Radio. StatusPill is touch-only.
    val tileFocus = remember { List(TILE_COUNT) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusedIndex) {
        runCatching { tileFocus[focusedIndex].requestFocus() }
    }

    // Media tiles (indices 2..5). Order must match the knob reading order above.
    val mediaTiles = listOf(
        MediaTile(R.string.home_playlists, Icons.Filled.PlaylistPlay, onOpenPlaylists),
        MediaTile(R.string.home_songs, Icons.Filled.MusicNote, onOpenSongs),
        MediaTile(R.string.home_destinations, Icons.Filled.Place, onOpenDestinations),
        MediaTile(R.string.home_radio, Icons.Filled.Radio, onOpenRadio),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Always consume Left/Right so focusedIndex stays the single source
                // of truth. Returning false at the ends would hand the event to
                // Compose's default directional focus search, which moves the
                // on-screen focus independently of focusedIndex — the two desync and
                // the knob appears to bounce back into earlier tiles. Clamp instead.
                when (event.key) {
                    Key.DirectionRight -> {
                        if (focusedIndex < TILE_COUNT - 1) focusedIndex++
                        true
                    }
                    Key.DirectionLeft -> {
                        if (focusedIndex > 0) focusedIndex--
                        true
                    }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header strip — pill flush right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(state = state, onClick = onOpenStatus)
        }
        // Top row — outbound nav apps (2 tiles, indices 0..1).
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
        // Media tiles — 2-column grid (Playlists/Songs, then Places/Radio), so
        // the whole home is a uniform 2-wide grid with the Waze/Maps row above.
        // Each row keeps weight 1f so tile size stays consistent; trailing slots
        // in a partial row are empty placeholders so tiles stay grid-aligned.
        mediaTiles.chunked(MEDIA_COLUMNS).forEachIndexed { rowIndex, rowTiles ->
            Row(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for (col in 0 until MEDIA_COLUMNS) {
                    val tile = rowTiles.getOrNull(col)
                    if (tile != null) {
                        // Global tile index: 2 (after Waze/Maps) + position in media list.
                        val globalIndex = 2 + rowIndex * MEDIA_COLUMNS + col
                        HomeTile(
                            modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[globalIndex]),
                            label = stringResource(tile.labelRes),
                            onClick = tile.onClick,
                            fallbackIcon = tile.icon,
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}
