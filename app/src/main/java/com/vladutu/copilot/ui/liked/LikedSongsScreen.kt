package com.vladutu.copilot.ui.liked

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vladutu.copilot.R
import com.vladutu.copilot.liked.LikedSong
import com.vladutu.copilot.ui.KnobPagedGrid
import com.vladutu.copilot.ui.ScreenHeader

@Composable
fun LikedSongsScreen(
    items: List<LikedSong>,
    onDelete: (LikedSong) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<LikedSong?>(null) }
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.clear_all))
                }
            }
            KnobPagedGrid(
                items = items,
                resetKey = items.firstOrNull()?.let { it.title + "|" + it.artist },
                modifier = Modifier.weight(1f),
            ) { item, requesters ->
                LikedTile(
                    song = item,
                    onLongPress = { pendingDelete = item },
                    modifier = Modifier.fillMaxSize().let { base ->
                        if (requesters != null) base.focusRequester(requesters[0]) else base
                    },
                )
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message, target.title)) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LikedTile(
    song: LikedSong,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val border = if (isFocused) {
        BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }
    Card(
        // No tap-to-play for liked songs: onClick is a deliberate no-op; only the
        // long-press (delete) is wired. Same combinedClickable mechanism as SavedTile.
        modifier = modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = {},
            onLongClick = onLongPress,
        ),
        shape = RoundedCornerShape(16.dp),
        border = border,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, lineHeight = 32.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            song.artist?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
