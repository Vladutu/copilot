package com.vladutu.copilot.ui

/** Position of the knob focus inside a paged grid: page index + stop index on that page. */
data class KnobPos(val page: Int, val stop: Int)

/**
 * Pure stop/page arithmetic for [KnobPagedGrid]. Stops walk items in reading order;
 * an item can expose several stops (e.g. Discover's name + play zones), all of an
 * item's stops coming before the next item's.
 */
class KnobGridNav(
    private val itemCount: Int,
    private val pageSize: Int,
    private val stopsPerItem: Int,
) {
    val pageCount: Int = if (itemCount == 0) 0 else (itemCount + pageSize - 1) / pageSize

    fun itemsOnPage(page: Int): Int {
        if (page < 0 || page >= pageCount) return 0
        return minOf(pageSize, itemCount - page * pageSize)
    }

    fun stopsOnPage(page: Int): Int = itemsOnPage(page) * stopsPerItem

    /** Right twist: next stop; at the page's last stop move to the next page's first; clamp at the end. */
    fun next(pos: KnobPos): KnobPos = when {
        pos.stop < stopsOnPage(pos.page) - 1 -> pos.copy(stop = pos.stop + 1)
        pos.page < pageCount - 1 -> KnobPos(page = pos.page + 1, stop = 0)
        else -> pos
    }

    /** Left twist: previous stop; at stop 0 move to the previous page's last; clamp at the start. */
    fun prev(pos: KnobPos): KnobPos = when {
        pos.stop > 0 -> pos.copy(stop = pos.stop - 1)
        pos.page > 0 -> KnobPos(page = pos.page - 1, stop = stopsOnPage(pos.page - 1) - 1)
        else -> pos
    }

    /** Repair a possibly-stale position (item deleted, list shrunk) onto a valid stop. */
    fun clamp(pos: KnobPos): KnobPos {
        if (pageCount == 0) return KnobPos(0, 0)
        val page = pos.page.coerceIn(0, pageCount - 1)
        val stop = pos.stop.coerceIn(0, (stopsOnPage(page) - 1).coerceAtLeast(0))
        return KnobPos(page, stop)
    }

    fun itemIndexOf(page: Int, stop: Int): Int = page * pageSize + stop / stopsPerItem

    fun stopWithinItem(stop: Int): Int = stop % stopsPerItem
}
