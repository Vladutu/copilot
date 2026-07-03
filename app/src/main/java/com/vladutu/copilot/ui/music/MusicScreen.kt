package com.vladutu.copilot.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Radio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.nowplaying.NowPlaying
import com.vladutu.copilot.ui.KnobPagedGrid
import com.vladutu.copilot.ui.MediaRowTile
import com.vladutu.copilot.ui.NowPlayingStrip
import com.vladutu.copilot.ui.ScreenHeader

// Playlists + Songs + Time Machine + Discover + Radio + Liked. Six tiles were too
// cramped on one 1440px row, so the fixed menu now pages 4-at-a-time through the
// shared knob rail (redesign-spec §3c) — same knob/dot behavior as every list screen.
private const val MUSIC_PAGE_SIZE = 4

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
    onOpenTimeMachine: () -> Unit,
    timeMachineBusy: Boolean,
    onOpenDiscover: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenLiked: () -> Unit,
    nowPlaying: NowPlaying?,
    onBack: () -> Unit,
) {
    // Knob reading order: Playlists → Songs → Time Machine → Discover → Radio → Liked.
    val tiles = listOf(
        MusicTile(R.string.home_playlists, Icons.AutoMirrored.Filled.PlaylistPlay, onOpenPlaylists),
        MusicTile(R.string.home_songs, Icons.Filled.MusicNote, onOpenSongs),
        MusicTile(
            R.string.home_time_machine,
            Icons.Filled.History,
            onOpenTimeMachine,
            busy = timeMachineBusy,
        ),
        MusicTile(R.string.home_discover, Icons.Filled.Explore, onOpenDiscover),
        MusicTile(R.string.home_radio, Icons.Filled.Radio, onOpenRadio),
        MusicTile(R.string.home_liked, Icons.Filled.Favorite, onOpenLiked),
    )

    Column(
        // bottom = 0: the now-playing strip sits flush at the screen edge.
        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Standard sub-screen header: back button + centered title. Touch-only,
        // not a knob stop — knob BACK pops the route the same way.
        ScreenHeader(title = stringResource(R.string.home_music), onBack = onBack)

        // resetKey is a constant: the menu never changes, so the knob just stays put.
        KnobPagedGrid(
            items = tiles,
            resetKey = "music",
            pageSize = MUSIC_PAGE_SIZE,
            modifier = Modifier.weight(1f),
        ) { tile, requesters ->
            MediaRowTile(
                modifier = Modifier.fillMaxSize(),
                focusRequester = requesters?.get(0),
                label = stringResource(tile.labelRes),
                onClick = tile.onClick,
                fallbackIcon = tile.icon,
                busy = tile.busy,
                maxLines = 2,
            )
        }

        NowPlayingStrip(nowPlaying = nowPlaying)
    }
}
