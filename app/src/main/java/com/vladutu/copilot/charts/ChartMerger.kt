package com.vladutu.copilot.charts

/**
 * Rank-interleaves two charts with the primary chart winning ties (P1, S1, P2, S2, …),
 * dedupes by ID keeping the earliest occurrence, and caps the result — YouTube's
 * watch_videos endpoint silently truncates temp playlists at 50 entries (verified
 * 2026-06-12, see spec), so sending more is pointless.
 */
object ChartMerger {
    const val MAX_QUEUE = 50

    fun merge(primary: List<String>, secondary: List<String>, cap: Int = MAX_QUEUE): List<String> {
        val out = LinkedHashSet<String>()
        for (i in 0 until maxOf(primary.size, secondary.size)) {
            primary.getOrNull(i)?.let { out.add(it) }
            secondary.getOrNull(i)?.let { out.add(it) }
            if (out.size >= cap) break
        }
        return out.take(cap)
    }
}
