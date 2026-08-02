package com.vladutu.copilot.ui.lists

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.discover.FoundSong
import com.vladutu.copilot.history.ArtworkCache
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import com.vladutu.copilot.nowplaying.NowPlaying
import com.vladutu.copilot.ui.ConfirmDialog
import com.vladutu.copilot.ui.KnobPagedGrid
import com.vladutu.copilot.ui.MediaRowTile
import com.vladutu.copilot.ui.NowPlayingStrip
import com.vladutu.copilot.ui.ScreenHeader
import com.vladutu.copilot.ui.voice.VoiceDialog
import com.vladutu.copilot.ui.voice.VoiceTarget
import com.vladutu.copilot.ui.voice.rememberMicPermissionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Everything the Songs voice tile needs: speak "title + artist", [find] the best
 * YT Music match, confirm, then [onPlay] launches it (the caller also saves it to
 * history, which lands it first in the list via savedAt ordering).
 */
data class VoiceSongConfig(
    val languageTag: String?,
    val find: suspend (String) -> FoundSong?,
    val onPlay: (FoundSong) -> Unit,
)

@Composable
fun SavedListScreen(
    items: List<SavedItem>,
    form: Form,
    artworkCache: ArtworkCache,
    nowPlaying: NowPlaying?,
    onTap: (SavedItem) -> Unit,
    onDelete: (SavedItem) -> Unit,
    onBack: () -> Unit,
    // Non-null adds a whole-list Clear button in the header (currently Songs only).
    onClearAll: (() -> Unit)? = null,
    // Non-null adds the voice tile first in the grid (currently Songs only).
    voiceSong: VoiceSongConfig? = null,
) {
    var pendingDelete by remember { mutableStateOf<SavedItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var showVoice by remember { mutableStateOf(false) }
    val startVoice = rememberMicPermissionRequest { showVoice = true }
    // Position count shown flush-right in the header (e.g. "1–5 / 24"); driven by the rail.
    var rangeText by remember { mutableStateOf<String?>(null) }
    val title = when (form) {
        Form.PLAYLIST -> stringResource(R.string.home_playlists)
        Form.SONG -> stringResource(R.string.home_songs)
        Form.DESTINATION -> stringResource(R.string.home_destinations)
        Form.RADIO -> stringResource(R.string.home_radio)
    }
    val emptyText = when (form) {
        Form.PLAYLIST -> stringResource(R.string.empty_playlists)
        Form.SONG -> stringResource(R.string.empty_songs)
        Form.DESTINATION -> stringResource(R.string.empty_destinations)
        Form.RADIO -> stringResource(R.string.empty_radio)
    }

    Column(
        // bottom = 0: the now-playing strip sits flush at the screen edge.
        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = title,
            onBack = onBack,
            trailing = if (items.isNotEmpty() && (rangeText != null || onClearAll != null)) {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        rangeText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (onClearAll != null) {
                            OutlinedButton(
                                onClick = { confirmClear = true },
                                // Touch-only chrome, same as BackHomeButton: the knob
                                // must never land here instead of on a tile.
                                modifier = Modifier.focusProperties { canFocus = false },
                            ) {
                                Text(stringResource(R.string.clear_list))
                            }
                        }
                    }
                }
            } else null,
        )

        if (items.isEmpty() && voiceSong == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = emptyText, style = MaterialTheme.typography.titleLarge)
            }
        } else {
            // resetKey: when the top item changes (manual tap, or Pilot event arriving
            // while this screen is open) the user lands back on page 0 to see it.
            // Knob behavior (always-consume, page edges, clamp) lives in KnobPagedGrid.
            val rows = remember(items, voiceSong != null) {
                (if (voiceSong != null) listOf<RowItem>(RowItem.Voice) else emptyList()) +
                    items.map { RowItem.Saved(it) }
            }
            KnobPagedGrid(
                items = rows,
                resetKey = items.firstOrNull()?.id,
                modifier = Modifier.weight(1f),
                onRangeChange = { rangeText = it },
            ) { row, requesters ->
                when (row) {
                    is RowItem.Voice -> MediaRowTile(
                        modifier = Modifier.fillMaxSize(),
                        label = stringResource(R.string.voice_song_tile),
                        onClick = { startVoice() },
                        focusRequester = requesters?.get(0),
                        fallbackIcon = Icons.Filled.Mic,
                    )
                    is RowItem.Saved -> SavedRow(
                        item = row.item,
                        artworkFile = artworkCache.fileFor(row.item.form, row.item.id),
                        focus = requesters?.get(0),
                        onTap = { onTap(row.item) },
                        onLongPress = { pendingDelete = row.item },
                    )
                }
            }
        }

        NowPlayingStrip(nowPlaying = nowPlaying)
    }

    if (showVoice && voiceSong != null) {
        VoiceDialog(
            languageTag = voiceSong.languageTag,
            titleRes = R.string.voice_song_title,
            questionRes = R.string.voice_play_confirm,
            confirmRes = R.string.voice_play_yes,
            notFoundRes = R.string.voice_song_not_found,
            resolve = { query -> voiceSong.find(query)?.let { VoiceTarget(it, it.title) } },
            onConfirm = voiceSong.onPlay,
            onDismiss = { showVoice = false },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.clear_songs_title),
            text = stringResource(R.string.clear_songs_message),
            onConfirm = { onClearAll?.invoke(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_title),
            text = stringResource(R.string.confirm_delete_message, target.title ?: target.id),
            onConfirm = { onDelete(target); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Grid entries: the optional voice tile first, then one entry per saved item. */
private sealed interface RowItem {
    object Voice : RowItem
    data class Saved(val item: SavedItem) : RowItem
}

@Composable
private fun SavedRow(
    item: SavedItem,
    artworkFile: File,
    focus: FocusRequester?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    var bitmap by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.id) {
        if (artworkFile.exists()) {
            val bmp = withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(artworkFile.absolutePath) }.getOrNull()
            }
            bitmap = bmp?.asImageBitmap()
        }
    }
    MediaRowTile(
        modifier = Modifier.fillMaxSize(),
        label = item.title ?: stringResource(R.string.untitled_item, item.id.take(8)),
        onClick = onTap,
        onLongPress = onLongPress,
        focusRequester = focus,
        thumbnail = bitmap,
        // Fallback when there is no artwork: form-specific glyph.
        fallbackIcon = when (item.form) {
            Form.RADIO -> Icons.Filled.Radio
            Form.DESTINATION -> Icons.Filled.Place
            else -> null
        },
        fallbackRes = when (item.form) {
            Form.PLAYLIST, Form.SONG -> R.drawable.ic_music_note
            else -> null
        },
        // Playlists/Songs show big cover art; Places/Radio use a small glyph.
        coverArt = item.form == Form.PLAYLIST || item.form == Form.SONG,
    )
}
