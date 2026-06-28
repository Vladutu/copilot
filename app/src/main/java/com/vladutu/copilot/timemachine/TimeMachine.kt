package com.vladutu.copilot.timemachine

/**
 * One song from a Billboard Year-End Hot 100 chart, as parsed from Wikipedia.
 * Backend-independent: the source produces these, never MediaWiki/HTTP types.
 */
data class SongRef(val artist: String, val title: String) {
    /** Keyword query for [com.vladutu.copilot.discover.MusicSearcher]: "Artist Title". */
    val query: String get() = "$artist $title"
}

/** Wraps any backend failure so callers never see library- or HTTP-specific exceptions. */
class TimeMachineException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Containment boundary for the year-end chart backend (spec 2026-06-28-music-time-machine),
 * mirroring [com.vladutu.copilot.discover.MusicSearcher]: callers see only [SongRef]s, so the
 * source (Wikipedia today) swaps at one construction site in ServiceLocator.
 */
interface YearEndChartSource {
    /**
     * Top [n] songs of [year]'s Billboard Year-End Hot 100, rank #1 first. Returns an **empty
     * list** for a missing or stub year (the selector simply draws another). Throws
     * [TimeMachineException] only on a hard backend failure (network/HTTP/parse).
     */
    suspend fun topSongs(year: Int, n: Int): List<SongRef>
}

/**
 * Mints an anonymous YouTube temp playlist from a list of video IDs and returns its
 * "TLGG…" list ID (submitted order preserved). Throws [TimeMachineException] on backend
 * failure; an empty [videoIds] list is a programmer error (IllegalArgumentException).
 */
fun interface PlaylistMinter {
    suspend fun mint(videoIds: List<String>): String
}

/**
 * Persistent per-year cache of resolved video IDs. The data is immutable (a past year's
 * year-end list never changes), so there is no invalidation — and a cached year still plays
 * when Wikipedia or search is unreachable.
 */
interface YearVideoCache {
    suspend fun get(year: Int): List<String>?
    suspend fun put(year: Int, videoIds: List<String>)
}
