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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.vladutu.copilot.R
import com.vladutu.copilot.history.ArtworkCache
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import com.vladutu.copilot.ui.BackHomeButton

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

    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val firstTileFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackHomeButton(onBack)
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = emptyText, style = MaterialTheme.typography.titleMedium)
            }
        } else {
            val pageSize = 6
            val pageCount = (items.size + pageSize - 1) / pageSize
            val pagerState = rememberPagerState(pageCount = { pageCount })

            // When the top item changes (manual tap, or Pilot event arriving while this screen
            // is open) the user should land back on page 0 to see the freshly promoted item.
            LaunchedEffect(items.firstOrNull()?.id) {
                pagerState.animateScrollToPage(0)
            }

            // Refocus the first tile of whichever page just settled, so the iDrive knob can
            // continue navigating immediately after a page change (or after items load).
            LaunchedEffect(pagerState.settledPage, items.isNotEmpty()) {
                if (items.isNotEmpty()) runCatching { firstTileFocus.requestFocus() }
            }

            // The iDrive knob arrives as DPAD_LEFT/RIGHT. We try moving focus within the page
            // first; if focus search fails (we're at the page edge) we page the HorizontalPager
            // instead, so the knob keeps producing visible motion all the way across the list.
            val pagerKeyHandler = Modifier.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        if (focusManager.moveFocus(FocusDirection.Right)) true
                        else if (pagerState.currentPage < pageCount - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                            true
                        } else false
                    }
                    Key.DirectionLeft -> {
                        if (focusManager.moveFocus(FocusDirection.Left)) true
                        else if (pagerState.currentPage > 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
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
                                        if (i == 0 && page == pagerState.settledPage) base.focusRequester(firstTileFocus)
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
