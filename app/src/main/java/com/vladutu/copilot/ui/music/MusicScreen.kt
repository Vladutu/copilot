package com.vladutu.copilot.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Radio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.vladutu.copilot.ui.ScreenHeader
import com.vladutu.copilot.ui.home.HomeTile

// Playlists + Songs + Top Weekly + Discover + Radio. Knob walks all five;
// the empty sixth grid slot is a placeholder, not a stop.
private const val TILE_COUNT = 5
private const val COLUMNS = 3

private data class MusicTile(
    val labelRes: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val busy: Boolean = false,
)

@Composable
fun MusicScreen(
    onOpenPlaylists: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenTopWeekly: () -> Unit,
    topWeeklyBusy: Boolean,
    onOpenDiscover: () -> Unit,
    onOpenRadio: () -> Unit,
    onBack: () -> Unit,
) {
    // Knob twist (DPAD_LEFT/RIGHT) walks the five tiles linearly in reading order:
    // Playlists → Songs → Top Weekly → Discover → Radio.
    val tileFocus = remember { List(TILE_COUNT) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusedIndex) {
        runCatching { tileFocus[focusedIndex].requestFocus() }
    }

    // Order must match the knob reading order above.
    val tiles = listOf(
        MusicTile(R.string.home_playlists, Icons.Filled.PlaylistPlay, onOpenPlaylists),
        MusicTile(R.string.home_songs, Icons.Filled.MusicNote, onOpenSongs),
        MusicTile(
            R.string.home_top_weekly,
            Icons.AutoMirrored.Filled.TrendingUp,
            onOpenTopWeekly,
            busy = topWeeklyBusy,
        ),
        MusicTile(R.string.home_discover, Icons.Filled.Explore, onOpenDiscover),
        MusicTile(R.string.home_radio, Icons.Filled.Radio, onOpenRadio),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
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
        // Standard sub-screen header: back button + centered title. Touch-only,
        // not a knob stop — knob BACK pops the route the same way.
        ScreenHeader(title = stringResource(R.string.home_music), onBack = onBack)

        // 3-column grid: Playlists/Songs/Top Weekly, then Discover/Radio/(empty).
        // Each row keeps weight 1f so tile size stays consistent; the trailing
        // slot in the partial row is an empty placeholder so tiles stay
        // grid-aligned.
        tiles.chunked(COLUMNS).forEachIndexed { rowIndex, rowTiles ->
            Row(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for (col in 0 until COLUMNS) {
                    val tile = rowTiles.getOrNull(col)
                    if (tile != null) {
                        val globalIndex = rowIndex * COLUMNS + col
                        HomeTile(
                            modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[globalIndex]),
                            label = stringResource(tile.labelRes),
                            onClick = tile.onClick,
                            fallbackIcon = tile.icon,
                            busy = tile.busy,
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}
