package com.vladutu.copilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.ui.lists.PageIndicator
import kotlinx.coroutines.launch

/**
 * Shared paged tile grid driven by the BMW knob. Extracted from SavedListScreen so
 * every screen inherits the same already-debugged focus behavior:
 *
 *  - `focusedStop` is the single source of truth; a LaunchedEffect pushes focus to
 *    the matching FocusRequester.
 *  - DPAD Left/Right are ALWAYS consumed (clamped at the ends). Returning false would
 *    hand the event to Compose's default directional focus search, which moves focus
 *    independently of our state — the two desync and the knob appears to jump around.
 *  - Page edges: last stop + right → next page first stop; first stop + left →
 *    previous page last stop. Stale positions after deletions are clamped.
 *
 * [tile] receives the item and, when its page is the settled one, [stopsPerItem]
 * FocusRequesters in item-major knob order (all stops of item N before item N+1).
 * On non-settled pages it receives null and must not attach requesters.
 */
@Composable
fun <T> KnobPagedGrid(
    items: List<T>,
    resetKey: Any?,
    modifier: Modifier = Modifier,
    stopsPerItem: Int = 1,
    pageSize: Int = 6,
    columns: Int = 3,
    tile: @Composable (item: T, focusRequesters: List<FocusRequester>?) -> Unit,
) {
    val nav = remember(items.size, pageSize, stopsPerItem) {
        KnobGridNav(items.size, pageSize, stopsPerItem)
    }
    val pagerState = rememberPagerState(pageCount = { nav.pageCount })
    val tileFocus = remember(pageSize, stopsPerItem) { List(pageSize * stopsPerItem) { FocusRequester() } }
    var focusedStop by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // resetKey change (top item changed / new list) → land back on page 0, stop 0.
    LaunchedEffect(resetKey) {
        if (nav.pageCount > 0) pagerState.animateScrollToPage(0)
        focusedStop = 0
    }

    // Refocus whenever the stop, settled page, or items change; clamp stale stops first.
    LaunchedEffect(focusedStop, pagerState.settledPage, items) {
        if (items.isNotEmpty()) {
            val pos = nav.clamp(KnobPos(pagerState.settledPage, focusedStop))
            runCatching { tileFocus[pos.stop].requestFocus() }
        }
    }

    fun moveFocus(step: (KnobPos) -> KnobPos) {
        val pos = step(nav.clamp(KnobPos(pagerState.currentPage, focusedStop)))
        if (pos.page != pagerState.currentPage) {
            scope.launch {
                pagerState.animateScrollToPage(pos.page)
                focusedStop = pos.stop
            }
        } else {
            focusedStop = pos.stop
        }
    }

    val keyHandler = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionRight -> {
                moveFocus(nav::next)
                true
            }
            Key.DirectionLeft -> {
                moveFocus(nav::prev)
                true
            }
            else -> false
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth().then(keyHandler),
        ) { page ->
            val start = page * pageSize
            val pageItems = items.subList(start, minOf(start + pageSize, items.size))
            val rowCount = (pageSize + columns - 1) / columns
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(rowCount) { row ->
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        for (col in 0 until columns) {
                            val i = row * columns + col
                            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                if (i < pageItems.size) {
                                    val requesters = if (page == pagerState.settledPage) {
                                        List(stopsPerItem) { s -> tileFocus[i * stopsPerItem + s] }
                                    } else null
                                    tile(pageItems[i], requesters)
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            PageIndicator(pageCount = nav.pageCount, currentPage = pagerState.currentPage)
        }
    }
}
