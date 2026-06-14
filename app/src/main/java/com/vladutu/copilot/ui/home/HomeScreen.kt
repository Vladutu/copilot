package com.vladutu.copilot.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.launch.AppLauncher
import com.vladutu.copilot.nowplaying.NowPlaying
import com.vladutu.copilot.service.UiState

@Composable
fun HomeScreen(
    state: UiState,
    nowPlaying: NowPlaying?,
    onLike: () -> Unit,
    onOpenWaze: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenDestinations: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenStatus: () -> Unit,
    onBackFromHome: () -> Unit,
) {
    BackHandler(onBack = onBackFromHome)

    val songPlaying = nowPlaying != null
    val tileCount = HomeKnob.tileCount(songPlaying)
    // Four fixed tiles + (optional) heart as the last stop.
    val tileFocus = remember { List(HomeKnob.BASE_TILES) { FocusRequester() } }
    val heartFocus = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }

    // If the song stops while the heart was focused, clamp back onto the last tile.
    LaunchedEffect(tileCount) {
        focusedIndex = HomeKnob.clampFocus(focusedIndex, tileCount)
    }
    LaunchedEffect(focusedIndex, tileCount) {
        val target = if (focusedIndex < HomeKnob.BASE_TILES) tileFocus[focusedIndex] else heartFocus
        runCatching { target.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        if (focusedIndex < tileCount - 1) focusedIndex++
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
        // Header strip: now-playing + heart on the left (only when a song is playing),
        // status pill flush right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (nowPlaying != null) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = nowPlayingLabel(nowPlaying),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = stringResource(R.string.like_song),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(40.dp)
                            .focusRequester(heartFocus)
                            .clickable(onClick = onLike)
                            .padding(8.dp),
                    )
                }
            } else {
                // keep the pill flush-right when nothing is playing
                Box(modifier = Modifier.weight(1f))
            }
            StatusPill(state = state, onClick = onOpenStatus)
        }

        // Top row — outbound nav apps (indices 0..1).
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
        // Bottom row — Places + Music (indices 2..3).
        Row(
            modifier = Modifier.weight(1f).fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HomeTile(
                modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[2]),
                label = stringResource(R.string.home_destinations),
                onClick = onOpenDestinations,
                fallbackIcon = Icons.Filled.Place,
            )
            HomeTile(
                modifier = Modifier.weight(1f).fillMaxSize().focusRequester(tileFocus[3]),
                label = stringResource(R.string.home_music),
                onClick = onOpenMusic,
                fallbackIcon = Icons.Filled.LibraryMusic,
            )
        }
    }
}

private fun nowPlayingLabel(np: NowPlaying): String =
    if (np.artist.isNullOrBlank()) "♪ ${np.title}" else "♪ ${np.title} — ${np.artist}"
