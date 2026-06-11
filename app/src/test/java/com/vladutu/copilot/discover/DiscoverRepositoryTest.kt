package com.vladutu.copilot.discover

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

private class FakeSearcher : MusicSearcher {
    var playlistCalls = 0
    var songCalls = 0
    var playlists: List<FoundPlaylist> = emptyList()
    var songs: List<FoundSong> = emptyList()
    var throwOnSearch = false

    override suspend fun searchPlaylists(keyword: String): List<FoundPlaylist> {
        playlistCalls++
        if (throwOnSearch) throw SearchException("boom")
        return playlists
    }

    override suspend fun searchSongs(keyword: String): List<FoundSong> {
        songCalls++
        if (throwOnSearch) throw SearchException("boom")
        return songs
    }
}

class DiscoverRepositoryTest {

    private val searcher = FakeSearcher()

    @Test fun `playlists caches per keyword case-insensitively`() = runTest {
        searcher.playlists = listOf(FoundPlaylist("PL1", "Gym", null))
        val repo = DiscoverRepository(searcher)
        repo.playlists("Workout")
        repo.playlists("workout")
        assertEquals(1, searcher.playlistCalls)
    }

    @Test fun `refresh bypasses the cache`() = runTest {
        searcher.playlists = listOf(FoundPlaylist("PL1", "Gym", null))
        val repo = DiscoverRepository(searcher)
        repo.playlists("Workout")
        repo.playlists("Workout", refresh = true)
        assertEquals(2, searcher.playlistCalls)
    }

    @Test fun `mixSeed picks only from the top of the results`() = runTest {
        searcher.songs = (1..50).map { FoundSong("v$it", "song $it") }
        val repo = DiscoverRepository(searcher, random = Random(42))
        repeat(20) {
            val seed = repo.mixSeed("Chill")!!
            val rank = seed.videoId.removePrefix("v").toInt()
            assertTrue("seed $rank outside top 10", rank <= 10)
        }
        assertEquals(1, searcher.songCalls) // cached after the first call
    }

    @Test fun `mixSeed is null when nothing found`() = runTest {
        val repo = DiscoverRepository(searcher)
        assertNull(repo.mixSeed("nothing"))
    }

    @Test fun `search failures propagate as SearchException`() = runTest {
        searcher.throwOnSearch = true
        val repo = DiscoverRepository(searcher)
        try {
            repo.playlists("Workout")
            fail("expected SearchException")
        } catch (expected: SearchException) {
        }
    }
}
