package com.vladutu.copilot.timemachine

import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.discover.MusicSearcher
import com.vladutu.copilot.discover.YtMusicUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * One-tap "Music Time Machine" orchestration (spec 2026-06-28): pick random years, resolve each
 * year's top songs to YouTube videos (cache first, else Wikipedia + search), mint one queue, and
 * return a launch URL that plays the tour in **chronological order** (oldest year first).
 *
 * Never throws: any failure degrades — a year that won't resolve is skipped, and only a tour that
 * produces zero songs returns null (the caller shows a brief error). The per-year cache is the
 * resilience mechanism: already-toured years keep working when Wikipedia or search is down.
 */
class TimeMachineRepository(
    private val source: YearEndChartSource,
    private val searcher: MusicSearcher,
    private val cache: YearVideoCache,
    private val minter: PlaylistMinter,
    private val selector: YearSelector = YearSelector(),
) {
    /** The launch URL for one tour, or null if nothing resolved. */
    suspend fun launchUrl(): String? = try {
        val resolved = resolveTour()
        if (resolved.isEmpty()) {
            DiagnosticLog.e(TAG, "no years resolved — time machine produced nothing to play")
            null
        } else {
            // Sort years ascending, keep each year's top-N in chart order: a forward tour through time.
            val orderedIds = resolved.toSortedMap().values.flatten()
            val listId = minter.mint(orderedIds)
            val url = YtMusicUrls.orderedPlaylist(listId)
            DiagnosticLog.i(TAG, "tour years ${resolved.keys.sorted()} → ${orderedIds.size} tracks → $url")
            url
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DiagnosticLog.e(TAG, "time machine failed", e)
        null
    }

    /** Resolve random years until [YearSelector] yields [YEARS_PER_TOUR] non-empty ones (or runs out). */
    private suspend fun resolveTour(): Map<Int, List<String>> = coroutineScope {
        val candidates = selector.candidates()
        val resolved = LinkedHashMap<Int, List<String>>()
        var idx = 0
        while (resolved.size < YEARS_PER_TOUR && idx < candidates.size) {
            val need = YEARS_PER_TOUR - resolved.size
            val batch = candidates.subList(idx, minOf(idx + need, candidates.size))
            idx += batch.size
            val results = batch.map { year -> async { year to resolveYear(year) } }.awaitAll()
            for ((year, ids) in results) if (ids.isNotEmpty()) resolved[year] = ids
        }
        resolved
    }

    /** Cached IDs, else Wikipedia top-N → per-song search. Empty = missing/stub/failed (year skipped). */
    private suspend fun resolveYear(year: Int): List<String> = try {
        cache.get(year) ?: run {
            val songs = source.topSongs(year, SONGS_PER_YEAR)
            if (songs.isEmpty()) return@run emptyList()  // missing/stub/parse — already logged by source
            val ids = songs.mapNotNull { song -> resolveSong(year, song) }
            // Cache only a fully-resolved year so a transient search miss isn't frozen in forever.
            if (ids.size == SONGS_PER_YEAR) cache.put(year, ids)
            else DiagnosticLog.w(TAG, "year $year: only ${ids.size}/$SONGS_PER_YEAR songs resolved — not caching")
            ids
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DiagnosticLog.w(TAG, "year $year resolve failed; skipping", e)
        emptyList()
    }

    /** One song → its first search-hit video ID, logging both "no hit" and search errors. */
    private suspend fun resolveSong(year: Int, song: SongRef): String? = try {
        val id = searcher.searchSongs(song.query).firstOrNull()?.videoId
        if (id == null) DiagnosticLog.w(TAG, "year $year: no search hit for \"${song.query}\"")
        id
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DiagnosticLog.w(TAG, "year $year: search failed for \"${song.query}\"", e)
        null
    }

    companion object {
        private const val TAG = "TimeMachine"
        const val YEARS_PER_TOUR = 5
        const val SONGS_PER_YEAR = 3
    }
}
