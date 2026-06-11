package com.vladutu.copilot.ui

import org.junit.Assert.assertEquals
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

    @Test fun `empty list yields zero pages and a safe clamp`() {
        val nav = KnobGridNav(itemCount = 0, pageSize = 6, stopsPerItem = 1)
        assertEquals(0, nav.pageCount)
        assertEquals(KnobPos(0, 0), nav.clamp(KnobPos(3, 3)))
    }
}
