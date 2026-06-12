package com.vladutu.copilot.charts

/** Wraps any backend failure so callers never see library- or HTTP-specific exceptions. */
class ChartsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Containment boundary for the chart backend (spec 2026-06-12-top-weekly), mirroring
 * [com.vladutu.copilot.discover.MusicSearcher]: callers see only video IDs in chart
 * order, never NewPipe types, so the backend swaps at one construction site in
 * ServiceLocator.
 */
interface ChartFetcher {
    /** Ordered video IDs of a chart playlist, rank #1 first. Throws [ChartsException]. */
    suspend fun fetchVideoIds(playlistUrl: String): List<String>
}

/**
 * Mints a launch URL for an ad-hoc queue of videos. Throws [ChartsException] on
 * backend failure; an empty [videoIds] list is a programmer error (IllegalArgumentException).
 */
fun interface PlaylistMinter {
    suspend fun mint(videoIds: List<String>): String
}
