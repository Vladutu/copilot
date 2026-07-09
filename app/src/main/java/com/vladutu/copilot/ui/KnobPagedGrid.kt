package com.vladutu.copilot.ui

import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.vladutu.copilot.ui.lists.DotStrip
import com.vladutu.copilot.ui.lists.PageIndicator

/**
 * Shared paged tile rail driven by the BMW knob. Extracted from SavedListScreen so
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
 * The rail is a single horizontal row of [pageSize] big tiles (redesign-spec §2b).
 * [tile] receives the item and, when its page is the settled one, [stopsPerItem]
 * FocusRequesters in item-major knob order (all stops of item N before item N+1).
 * On non-settled pages it receives null and must not attach requesters.
 *
 * [onRangeChange] reports the visible 1-based item range (e.g. "1–5 / 24") whenever the
 * page or the list changes, so the caller's header can show a position count without
 * KnobPagedGrid owning the header.
 *
 * Bottom dot strip: a list that fits on a single page gets one dot per item with the
 * pill tracking the focused item (same feel as Home); anything longer gets one dot
 * per page, the pill moving on page flips. [perItemDots] forces the per-item strip
 * even across pages — for fixed menus (Music) that stay small enough to dot each entry,
 * as opposed to unbounded saved lists.
 */
@Composable
fun <T> KnobPagedGrid(
    items: List<T>,
    resetKey: Any?,
    modifier: Modifier = Modifier,
    stopsPerItem: Int = 1,
    pageSize: Int = 5,
    perItemDots: Boolean = false,
    onRangeChange: ((String) -> Unit)? = null,
    tile: @Composable (item: T, focusRequesters: List<FocusRequester>?) -> Unit,
) {
    val nav = remember(items.size, pageSize, stopsPerItem) {
        KnobGridNav(items.size, pageSize, stopsPerItem)
    }
    val pagerState = rememberPagerState(pageCount = { nav.pageCount })
    val tileFocus = remember(pageSize, stopsPerItem) { List(pageSize * stopsPerItem) { FocusRequester() } }

    // Single source of truth: the knob's page + stop. We drive the pager and the focus
    // FROM this state and never read pagerState.currentPage back into our logic. During a
    // page animation currentPage flips at the halfway point while the old focusedStop is
    // still in place, so a fast second twist used to compute a bogus same-page move and
    // then the settled-page refocus effect would yank focus back to the old page — the
    // "goes to the 2nd page then bounces back to the 1st on fast rotation" bug.
    var pos by remember { mutableStateOf(KnobPos(0, 0)) }

    // resetKey change (top item changed / new list) → land back on page 0, stop 0.
    LaunchedEffect(resetKey) { pos = KnobPos(0, 0) }

    // Items shrank (deletion) → repair a now-stale position onto a valid stop.
    LaunchedEffect(items) { pos = nav.clamp(pos) }

    // Drive the pager from our page. Relaunching on a page change cancels any in-flight
    // scroll, so a rapid burst of twists just animates to the latest target page once.
    LaunchedEffect(pos.page) {
        if (nav.pageCount > 0) pagerState.animateScrollToPage(pos.page)
    }

    // A finger swipe moves the pager without going through the knob, so mirror the
    // settled page back into our state — otherwise the dots, range and focus stay on
    // the old page. ONLY for real drags though: a knob-driven settle can also disagree
    // with pos.page — a fast right+left twist at a page edge lets the abandoned scroll
    // cross the finish line before its cancel lands — and mirroring that would overwrite
    // the second twist, dumping focus on the wrong page's last stop. So a drag arms the
    // mirror, the settle consumes it, and knob input disarms it (the knob is authoritative
    // the moment it speaks).
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    var dragPending by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) { if (dragged) dragPending = true }
    LaunchedEffect(pagerState.settledPage) {
        if (dragPending && pagerState.settledPage != pos.page) {
            pos = nav.clamp(pos.copy(page = pagerState.settledPage))
        }
        dragPending = false
    }

    // Focus follows our stop, but only once the pager has actually settled on our page:
    // requesters are attached only to the settled page's tiles (see below), so requesting
    // while a scroll is still animating would target the old page and drag focus backward.
    LaunchedEffect(pos, pagerState.settledPage) {
        if (items.isNotEmpty() && pagerState.settledPage == pos.page) {
            runCatching { tileFocus[pos.stop].requestFocus() }
        }
    }

    // Report the visible item range for the caller's header count.
    LaunchedEffect(pos.page, items.size, pageSize) {
        if (onRangeChange != null && items.isNotEmpty()) {
            val start = pos.page * pageSize + 1
            val end = minOf((pos.page + 1) * pageSize, items.size)
            onRangeChange("$start–$end / ${items.size}")
        }
    }

    val keyHandler = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionRight -> { dragPending = false; pos = nav.next(nav.clamp(pos)); true }
            Key.DirectionLeft -> { dragPending = false; pos = nav.prev(nav.clamp(pos)); true }
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
            // One horizontal rail of pageSize tiles, weighted so they share the row.
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                for (i in 0 until pageSize) {
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
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (items.size >= 2 && (perItemDots || items.size <= pageSize)) {
                // Focus moving between two stops of the same item (Discover's name + play
                // zones) keeps the pill on that item's dot.
                DotStrip(
                    count = items.size,
                    current = nav.itemIndexOf(pos.page, pos.stop).coerceAtMost(items.size - 1),
                )
            } else {
                PageIndicator(pageCount = nav.pageCount, currentPage = pos.page)
            }
        }
    }
}
