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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.launch.AppLauncher
import com.vladutu.copilot.service.UiState

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

    val firstTileFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstTileFocus.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                        .focusRequester(firstTileFocus),
                    packageName = AppLauncher.WAZE_PKG,
                    fallbackRes = R.drawable.ic_map_pin,
                    label = stringResource(R.string.home_waze),
                    onClick = onOpenWaze,
                )
                LabelTile(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    label = stringResource(R.string.home_playlists),
                    onClick = onOpenPlaylists,
                )
            }
            Row(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LabelTile(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    label = stringResource(R.string.home_songs),
                    onClick = onOpenSongs,
                )
                LabelTile(
                    modifier = Modifier.weight(1f).fillMaxSize(),
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
