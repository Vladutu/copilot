package com.vladutu.copilot.ui.discover

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.discover.DiscoverRepository
import com.vladutu.copilot.discover.FoundPlaylist
import com.vladutu.copilot.discover.SearchException
import com.vladutu.copilot.discover.YtMusicUrls
import com.vladutu.copilot.launch.AppLauncher
import com.vladutu.copilot.ui.KnobPagedGrid
import com.vladutu.copilot.ui.MediaRowTile
import com.vladutu.copilot.ui.ScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

private sealed interface BrowseState {
    data object Loading : BrowseState
    data class Loaded(val playlists: List<FoundPlaylist>) : BrowseState
    data object Failed : BrowseState
}

/** Paged grid of playlists matching one category keyword; tap plays in YT Music. */
@Composable
fun BrowseResultsScreen(
    keyword: String,
    repository: DiscoverRepository,
    okHttp: OkHttpClient,
    launcher: AppLauncher,
    onLaunched: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<BrowseState>(BrowseState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(keyword, attempt) {
        state = BrowseState.Loading
        state = try {
            // attempt > 0 means the user pressed Retry — bypass the session cache.
            BrowseState.Loaded(repository.playlists(keyword, refresh = attempt > 0))
        } catch (e: SearchException) {
            DiagnosticLog.e(TAG, "browse '$keyword' failed", e)
            BrowseState.Failed
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = keyword, onBack = onBack)

        when (val s = state) {
            is BrowseState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is BrowseState.Failed -> RetryBox(onRetry = { attempt++ })

            is BrowseState.Loaded -> {
                if (s.playlists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.discover_no_results),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                } else {
                    KnobPagedGrid(
                        items = s.playlists,
                        resetKey = keyword,
                        modifier = Modifier.weight(1f),
                    ) { playlist, requesters ->
                        BrowsePlaylistRow(
                            playlist = playlist,
                            okHttp = okHttp,
                            focus = requesters?.get(0),
                            onTap = {
                                when (val r = launcher.launchYtMusic(YtMusicUrls.playlist(playlist.playlistId))) {
                                    is AppLauncher.Result.Ok -> onLaunched()
                                    is AppLauncher.Result.Failed ->
                                        Toast.makeText(context, r.reason, Toast.LENGTH_LONG).show()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Failure state with a knob-reachable Retry: focused on entry so a knob press retries. */
@Composable
private fun RetryBox(onRetry: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.discover_browse_failed),
                style = MaterialTheme.typography.titleLarge,
            )
            Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) {
                Text(stringResource(R.string.discover_retry))
            }
        }
    }
}

@Composable
private fun BrowsePlaylistRow(
    playlist: FoundPlaylist,
    okHttp: OkHttpClient,
    focus: FocusRequester?,
    onTap: () -> Unit,
) {
    var bitmap by remember(playlist.playlistId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(playlist.playlistId) {
        val url = playlist.thumbnailUrl ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                okHttp.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    resp.body.bytes().let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
            }.getOrNull()
        }?.asImageBitmap()
    }
    MediaRowTile(
        modifier = Modifier.fillMaxSize(),
        label = playlist.title,
        onClick = onTap,
        focusRequester = focus,
        thumbnail = bitmap,
        fallbackRes = R.drawable.ic_music_note,
    )
}

private const val TAG = "DiscoverBrowse"
