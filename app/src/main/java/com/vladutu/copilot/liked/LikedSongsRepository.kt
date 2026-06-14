package com.vladutu.copilot.liked

import com.vladutu.copilot.nowplaying.NowPlaying
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LikedSongsRepository(
    private val store: LikedSongStore,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    fun items(): Flow<List<LikedSong>> =
        store.items().map { list -> list.sortedByDescending { it.savedAt } }

    /** Upsert: liking a song already present just refreshes its savedAt (promotes it to
     *  the top) and keeps the original display title/artist — a re-read with odd casing or
     *  spacing shouldn't rewrite a cleanly-saved entry. A genuinely new song is appended. */
    suspend fun like(now: NowPlaying) {
        val incoming = LikedSong(now.title, now.artist, clock())
        store.mutate { current ->
            if (current.any { it.sameSongAs(incoming) }) {
                current.map { if (it.sameSongAs(incoming)) it.copy(savedAt = incoming.savedAt) else it }
            } else {
                current + incoming
            }
        }
    }

    suspend fun delete(song: LikedSong) =
        store.mutate { current -> current.filterNot { it.sameSongAs(song) } }

    suspend fun clearAll() = store.mutate { emptyList() }
}
