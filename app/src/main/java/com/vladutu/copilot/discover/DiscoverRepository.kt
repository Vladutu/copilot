package com.vladutu.copilot.discover

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Session-scoped results cache over [MusicSearcher]. No persistence — there is no
 * quota to protect; the cache only makes back-navigation snappy and keeps the
 * mix-seed pool stable while randomizing the pick.
 *
 * Searches for the same keyword are serialized by the mutex, so a double-tap can't
 * fire duplicate network calls.
 */
class DiscoverRepository(
    private val searcher: MusicSearcher,
    private val random: Random = Random.Default,
) {
    private val mutex = Mutex()
    private val playlistCache = mutableMapOf<String, List<FoundPlaylist>>()
    private val songCache = mutableMapOf<String, List<FoundSong>>()

    suspend fun playlists(keyword: String, refresh: Boolean = false): List<FoundPlaylist> = mutex.withLock {
        val key = cacheKey(keyword)
        if (refresh) playlistCache.remove(key)
        playlistCache.getOrPut(key) { searcher.searchPlaylists(keyword) }
    }

    /** Random seed from the top results so the same chip doesn't always start the same mix. */
    suspend fun mixSeed(keyword: String): FoundSong? = mutex.withLock {
        val songs = songCache.getOrPut(cacheKey(keyword)) { searcher.searchSongs(keyword) }
        songs.take(MIX_SEED_POOL).randomOrNull(random)
    }

    private fun cacheKey(keyword: String) = keyword.trim().lowercase()

    private companion object {
        const val MIX_SEED_POOL = 10
    }
}
