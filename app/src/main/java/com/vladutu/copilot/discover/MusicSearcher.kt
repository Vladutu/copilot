package com.vladutu.copilot.discover

/** A playlist found by keyword search. Raw ID only — URL building is the caller's job. */
data class FoundPlaylist(val playlistId: String, val title: String, val thumbnailUrl: String?)

/** A song found by keyword search; used as a radio-mix seed. */
data class FoundSong(val videoId: String, val title: String)

/** Wraps any backend failure so callers never see library-specific exceptions. */
class SearchException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Containment boundary for the discovery backend (spec 2026-06-11-discover): UI and
 * repository code may depend only on this interface and its plain data types, so the
 * backend (NewPipe Extractor today, official Data API later) swaps at one construction
 * site in ServiceLocator without touching anything else.
 */
interface MusicSearcher {
    suspend fun searchPlaylists(keyword: String): List<FoundPlaylist>
    suspend fun searchSongs(keyword: String): List<FoundSong>
}
