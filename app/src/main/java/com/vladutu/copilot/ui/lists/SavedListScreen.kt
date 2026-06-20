package com.vladutu.copilot.ui.lists

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.history.ArtworkCache
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import com.vladutu.copilot.ui.KnobPagedGrid
import com.vladutu.copilot.ui.MediaRowTile
import com.vladutu.copilot.ui.ScreenHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SavedListScreen(
    items: List<SavedItem>,
    form: Form,
    artworkCache: ArtworkCache,
    onTap: (SavedItem) -> Unit,
    onDelete: (SavedItem) -> Unit,
    onBack: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<SavedItem?>(null) }
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
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = title, onBack = onBack)

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = emptyText, style = MaterialTheme.typography.titleLarge)
            }
        } else {
            // resetKey: when the top item changes (manual tap, or Pilot event arriving
            // while this screen is open) the user lands back on page 0 to see it.
            // Knob behavior (always-consume, page edges, clamp) lives in KnobPagedGrid.
            KnobPagedGrid(
                items = items,
                resetKey = items.firstOrNull()?.id,
                modifier = Modifier.weight(1f),
            ) { item, requesters ->
                SavedRow(
                    item = item,
                    artworkFile = artworkCache.fileFor(item.form, item.id),
                    focus = requesters?.get(0),
                    onTap = { onTap(item) },
                    onLongPress = { pendingDelete = item },
                )
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message, target.title ?: target.id)) },
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
        label = item.title ?: "Untitled · ${item.id.take(8)}",
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
    )
}
