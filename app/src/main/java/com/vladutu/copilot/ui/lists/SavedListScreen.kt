package com.vladutu.copilot.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.vladutu.copilot.R
import com.vladutu.copilot.history.ArtworkCache
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import com.vladutu.copilot.ui.ScreenHeader

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
    }
    val emptyText = when (form) {
        Form.PLAYLIST -> stringResource(R.string.empty_playlists)
        Form.SONG -> stringResource(R.string.empty_songs)
        Form.DESTINATION -> stringResource(R.string.empty_destinations)
    }

    val scope = rememberCoroutineScope()
    val pageSize = 6
    val tileFocus = remember { List(pageSize) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }

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
            val pageCount = (items.size + pageSize - 1) / pageSize
            val pagerState = rememberPagerState(pageCount = { pageCount })

            fun itemsOnPage(page: Int): Int {
                if (page < 0 || page >= pageCount) return 0
                return minOf(pageSize, items.size - page * pageSize)
            }

            // When the top item changes (manual tap, or Pilot event arriving while this screen
            // is open) the user should land back on page 0 to see the freshly promoted item.
            LaunchedEffect(items.firstOrNull()?.id) {
                pagerState.animateScrollToPage(0)
                focusedIndex = 0
            }

            // Refocus the tile at focusedIndex whenever the index, settled page, or items change.
            // Clamp to a valid slot first so a stale index from a deletion doesn't crash.
            LaunchedEffect(focusedIndex, pagerState.settledPage, items) {
                if (items.isNotEmpty()) {
                    val maxIdx = (itemsOnPage(pagerState.settledPage) - 1).coerceAtLeast(0)
                    val target = focusedIndex.coerceIn(0, maxIdx)
                    runCatching { tileFocus[target].requestFocus() }
                }
            }

            // Knob twist (DPAD_LEFT/RIGHT) walks the page's tiles linearly. At the page edge
            // it animates to the adjacent page and lands on the first tile (when going forward)
            // or the last tile (when going backward) so spatial continuity is preserved.
            // The header back-arrow is intentionally NOT in the rotation — physical BACK handles that.
            val pagerKeyHandler = Modifier.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        val count = itemsOnPage(pagerState.currentPage)
                        if (focusedIndex < count - 1) {
                            focusedIndex++
                            true
                        } else if (pagerState.currentPage < pageCount - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                focusedIndex = 0
                            }
                            true
                        } else false
                    }
                    Key.DirectionLeft -> {
                        if (focusedIndex > 0) {
                            focusedIndex--
                            true
                        } else if (pagerState.currentPage > 0) {
                            scope.launch {
                                val newPage = pagerState.currentPage - 1
                                pagerState.animateScrollToPage(newPage)
                                focusedIndex = (itemsOnPage(newPage) - 1).coerceAtLeast(0)
                            }
                            true
                        } else false
                    }
                    else -> false
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth().then(pagerKeyHandler),
            ) { page ->
                val start = page * pageSize
                val end = minOf(start + pageSize, items.size)
                val pageItems = items.subList(start, end)
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(0 to 3, 3 to 6).forEach { (rowStart, rowEnd) ->
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            for (i in rowStart until rowEnd) {
                                if (i < pageItems.size) {
                                    val it = pageItems[i]
                                    val tileModifier = Modifier.weight(1f).fillMaxSize().let { base ->
                                        if (page == pagerState.settledPage) base.focusRequester(tileFocus[i])
                                        else base
                                    }
                                    SavedTile(
                                        item = it,
                                        artworkFile = artworkCache.fileFor(it.form, it.id),
                                        onTap = { onTap(it) },
                                        onLongPress = { pendingDelete = it },
                                        modifier = tileModifier,
                                    )
                                } else {
                                    Box(modifier = Modifier.weight(1f).fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                PageIndicator(pageCount = pageCount, currentPage = pagerState.currentPage)
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
