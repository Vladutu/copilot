package com.vladutu.copilot.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnobGridNavTest {

    @Test fun `walks stops within a page and clamps at the very end`() {
        val nav = KnobGridNav(itemCount = 4, pageSize = 6, stopsPerItem = 1)
        var pos = KnobPos(0, 0)
        repeat(3) { pos = nav.next(pos) }
        assertEquals(KnobPos(0, 3), pos)
        assertEquals(KnobPos(0, 3), nav.next(pos)) // clamp, never leak focus
    }

    @Test fun `clamps at the very start`() {
        val nav = KnobGridNav(itemCount = 4, pageSize = 6, stopsPerItem = 1)
        assertEquals(KnobPos(0, 0), nav.prev(KnobPos(0, 0)))
    }

    @Test fun `page edge forward lands on first stop of next page`() {
        val nav = KnobGridNav(itemCount = 8, pageSize = 6, stopsPerItem = 1)
        assertEquals(KnobPos(1, 0), nav.next(KnobPos(0, 5)))
    }

    @Test fun `page edge backward lands on last stop of previous page`() {
        val nav = KnobGridNav(itemCount = 8, pageSize = 6, stopsPerItem = 1)
        assertEquals(KnobPos(0, 5), nav.prev(KnobPos(1, 0)))
    }

    @Test fun `stopsPerItem 2 doubles the stops and keeps item-major order`() {
        val nav = KnobGridNav(itemCount = 8, pageSize = 6, stopsPerItem = 2)
        assertEquals(12, nav.stopsOnPage(0))
        assertEquals(4, nav.stopsOnPage(1)) // 2 items × 2 stops
        // item-major: stop 3 on page 0 = item 1, zone 1
        assertEquals(1, nav.itemIndexOf(page = 0, stop = 3))
        assertEquals(1, nav.stopWithinItem(3))
        // crossing the page edge with two-stop items
        assertEquals(KnobPos(1, 0), nav.next(KnobPos(0, 11)))
        assertEquals(KnobPos(0, 11), nav.prev(KnobPos(1, 0)))
    }

    @Test fun `clamp repairs a stale position after deletion`() {
        val nav = KnobGridNav(itemCount = 7, pageSize = 6, stopsPerItem = 1)
        assertEquals(KnobPos(1, 0), nav.clamp(KnobPos(1, 4))) // page 1 has 1 item
        assertEquals(KnobPos(1, 0), nav.clamp(KnobPos(5, 9))) // page out of range
    }

    // Trailing stop: Home's Like heart — one caller-owned extra stop after the last
    // item, on the last page only.

    @Test fun `trailing stop follows the last item across pages and clamps at the end`() {
        val nav = KnobGridNav(itemCount = 4, pageSize = 3, stopsPerItem = 1, hasTrailingStop = true)
        assertEquals(2, nav.pageCount)
        assertEquals(3, nav.stopsOnPage(0)) // trailing stop is NOT on earlier pages
        assertEquals(2, nav.stopsOnPage(1)) // last tile + heart
        assertEquals(KnobPos(1, 1), nav.next(KnobPos(1, 0)))
        assertEquals(KnobPos(1, 1), nav.next(KnobPos(1, 1))) // clamp on the heart
        assertEquals(KnobPos(1, 0), nav.prev(KnobPos(1, 1)))
        assertTrue(nav.isTrailingStop(KnobPos(1, 1)))
        assertFalse(nav.isTrailingStop(KnobPos(1, 0)))
        assertFalse(nav.isTrailingStop(KnobPos(0, 2)))
    }

    @Test fun `trailing stop on a single-page grid`() {
        val nav = KnobGridNav(itemCount = 4, pageSize = 4, stopsPerItem = 1, hasTrailingStop = true)
        assertEquals(1, nav.pageCount)
        assertEquals(5, nav.stopsOnPage(0))
        assertEquals(KnobPos(0, 4), nav.next(KnobPos(0, 3)))
        assertTrue(nav.isTrailingStop(KnobPos(0, 4)))
        assertEquals(KnobPos(0, 3), nav.prev(KnobPos(0, 4)))
    }

    @Test fun `trailing stop stays reachable when the last page is full`() {
        val nav = KnobGridNav(itemCount = 6, pageSize = 3, stopsPerItem = 1, hasTrailingStop = true)
        assertEquals(4, nav.stopsOnPage(1)) // 3 tiles + heart
        assertEquals(KnobPos(1, 3), nav.next(KnobPos(1, 2)))
        assertTrue(nav.isTrailingStop(KnobPos(1, 3)))
    }

    @Test fun `clamp repairs a trailing-stop position once the trailing stop disappears`() {
        // Song stops while the heart was focused → land back on the last tile.
        val nav = KnobGridNav(itemCount = 4, pageSize = 4, stopsPerItem = 1, hasTrailingStop = false)
        assertEquals(KnobPos(0, 3), nav.clamp(KnobPos(0, 4)))
    }

    @Test fun `without a trailing stop nothing reports as one`() {
        val nav = KnobGridNav(itemCount = 4, pageSize = 4, stopsPerItem = 1)
        assertFalse(nav.isTrailingStop(KnobPos(0, 3)))
        assertFalse(nav.isTrailingStop(KnobPos(0, 4)))
    }

    @Test fun `empty list yields zero pages and a safe clamp`() {
        val nav = KnobGridNav(itemCount = 0, pageSize = 6, stopsPerItem = 1)
        assertEquals(0, nav.pageCount)
        assertEquals(KnobPos(0, 0), nav.clamp(KnobPos(3, 3)))
    }
}
