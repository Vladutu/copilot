package com.vladutu.copilot.charts

import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.discover.YtMusicUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * One-tap "Top Weekly" orchestration: fetch the US and GB weekly chart playlists in
 * parallel, rank-interleave with US priority (spec 2026-06-12-top-weekly), mint an
 * anonymous queue, and return the launch URL.
 *
 * Never throws: any failure — one chart, both charts, or the minting call — degrades
 * stepwise so the tile always plays something, in the worst case the US chart playlist
 * launched directly (still shuffled, to match the minted-queue experience).
 */
class ChartsRepository(
    private val fetcher: ChartFetcher,
    private val minter: PlaylistMinter,
) {
    suspend fun topWeeklyLaunchUrl(): String = coroutineScope {
        val us = async { fetchSafe(US_CHART_URL, "US") }
        val gb = async { fetchSafe(GB_CHART_URL, "GB") }
        val ids = ChartMerger.merge(us.await(), gb.await())
        if (ids.isEmpty()) {
            DiagnosticLog.e(TAG, "both chart fetches failed — falling back to US chart playlist")
            return@coroutineScope FALLBACK_URL
        }
        try {
            minter.mint(ids)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLog.e(TAG, "minting failed — falling back to US chart playlist", e)
            FALLBACK_URL
        }
    }

    private suspend fun fetchSafe(url: String, label: String): List<String> =
        try {
            fetcher.fetchVideoIds(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLog.e(TAG, "$label chart fetch failed", e)
            emptyList()
        }

    companion object {
        private const val TAG = "Charts"

        // YouTube's own auto-updating weekly chart playlists: stable IDs, contents
        // refreshed weekly (verified 2026-06-12, see spec).
        const val US_PLAYLIST_ID = "PL4fGSI1pDJn6O1LS0XSdF3RyO0Rq_LDeI"
        const val GB_PLAYLIST_ID = "PL4fGSI1pDJn6_f5P3MnzXg9l3GDfnSlXa"
        const val US_CHART_URL = "https://www.youtube.com/playlist?list=$US_PLAYLIST_ID"
        const val GB_CHART_URL = "https://www.youtube.com/playlist?list=$GB_PLAYLIST_ID"
        val FALLBACK_URL = YtMusicUrls.playlist(US_PLAYLIST_ID)
    }
}
