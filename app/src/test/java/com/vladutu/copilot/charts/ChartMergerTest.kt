package com.vladutu.copilot.charts

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMergerTest {

    @Test fun `interleaves by rank with primary first`() {
        val merged = ChartMerger.merge(listOf("us1", "us2"), listOf("gb1", "gb2"))
        assertEquals(listOf("us1", "gb1", "us2", "gb2"), merged)
    }

    @Test fun `dedupes across charts keeping the first occurrence`() {
        // "hit" is US #2 and GB #1 — it appears once, at its earliest interleave slot.
        val merged = ChartMerger.merge(listOf("us1", "hit"), listOf("hit", "gb2"))
        assertEquals(listOf("us1", "hit", "gb2"), merged)
    }

    @Test fun `dedupes repeats within a single chart`() {
        val merged = ChartMerger.merge(listOf("a", "a", "b"), emptyList())
        assertEquals(listOf("a", "b"), merged)
    }

    @Test fun `one empty chart degrades to the other in order`() {
        assertEquals(listOf("gb1", "gb2"), ChartMerger.merge(emptyList(), listOf("gb1", "gb2")))
        assertEquals(listOf("us1", "us2"), ChartMerger.merge(listOf("us1", "us2"), emptyList()))
    }

    @Test fun `caps at 50 (watch_videos hard limit)`() {
        val us = (1..100).map { "us$it" }
        val gb = (1..100).map { "gb$it" }
        val merged = ChartMerger.merge(us, gb)
        assertEquals(50, merged.size)
        // First 50 of the interleave: us1, gb1, us2, gb2, … us25, gb25.
        assertEquals(listOf("us1", "gb1", "us2", "gb2"), merged.take(4))
        assertEquals(listOf("us25", "gb25"), merged.takeLast(2))
    }

    @Test fun `shorter inputs than the cap are returned whole`() {
        val merged = ChartMerger.merge(listOf("us1", "us2", "us3"), listOf("gb1"))
        assertEquals(listOf("us1", "gb1", "us2", "us3"), merged)
    }
}
