package com.vladutu.copilot.ui.liked

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.liked.LikedSong
import com.vladutu.copilot.ui.ScreenHeader

/**
 * Read-when-stopped memo of liked songs. A plain vertical list (no launch tiles —
 * nothing here is tappable to play). Manage with two whole-list actions only: Copy
 * (the list to the clipboard, for sharing off-device via email etc.) and Clear all.
 * No per-item delete by design.
 */
@Composable
fun LikedSongsScreen(
    items: List<LikedSong>,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val copiedMsg = stringResource(R.string.liked_copied)
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.home_liked), onBack = onBack)

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.empty_liked), style = MaterialTheme.typography.titleLarge)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = {
                    copyToClipboard(context, items)
                    Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.copy_list))
                }
                OutlinedButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.clear_all))
                }
            }
            // Big, readable rows; "Title - Artist" wraps to the next line when long
            // (no truncation). Generous spacing between songs.
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(items) { song ->
                    Text(
                        text = "•  ${songLine(song)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_all_title)) },
            text = { Text(stringResource(R.string.clear_all_message)) },
            confirmButton = {
                TextButton(onClick = { onClearAll(); confirmClear = false }) {
                    Text(stringResource(R.string.confirm_delete_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.confirm_delete_no))
                }
            },
        )
    }
}

/** One displayed/copied line: "Title - Artist", or just "Title" when no artist. */
private fun songLine(song: LikedSong): String =
    if (song.artist.isNullOrBlank()) song.title else "${song.title} - ${song.artist}"

private fun copyToClipboard(context: Context, items: List<LikedSong>) {
    val text = items.joinToString("\n") { songLine(it) }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Liked songs", text))
}
