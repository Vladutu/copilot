package com.vladutu.copilot.timemachine

import com.vladutu.copilot.discover.FoundPlaylist
import com.vladutu.copilot.discover.FoundSong
import com.vladutu.copilot.discover.MusicSearcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private class FakeSource(private val songsByYear: Map<Int, List<SongRef>>) : YearEndChartSource {
    override suspend fun topSongs(year: Int, n: Int): List<SongRef> =
        (songsByYear[year] ?: emptyList()).take(n)
}

/** Resolves "$artist $title" via [idByQuery]; null means "not found" (empty result). */
private class FakeSearcher(private val idByQuery: (String) -> String?) : MusicSearcher {
    override suspend fun searchPlaylists(keyword: String): List<FoundPlaylist> = emptyList()
    override suspend fun searchSongs(keyword: String): List<FoundSong> =
        idByQuery(keyword)?.let { listOf(FoundSong(videoId = it, title = keyword)) } ?: emptyList()
}

private class MapCache(seed: Map<Int, List<String>> = emptyMap()) : YearVideoCache {
    val map = seed.toMutableMap()
    var puts = 0
    override suspend fun get(year: Int): List<String>? = map[year]
    override suspend fun put(year: Int, videoIds: List<String>) {
        map[year] = videoIds; puts++
    }
}

private class FakeMinter : PlaylistMinter {
    var minted: List<String>? = null
    override suspend fun mint(videoIds: List<String>): String {
        minted = videoIds; return "TLGGtest"
    }
}

private object ThrowingSource : YearEndChartSource {
    override suspend fun topSongs(year: Int, n: Int): List<SongRef> = error("source must not be called")
}

private object ThrowingSearcher : MusicSearcher {
    override suspend fun searchPlaylists(keyword: String): List<FoundPlaylist> = error("searcher must not be called")
    override suspend fun searchSongs(keyword: String): List<FoundSong> = error("searcher must not be called")
}

class TimeMachineRepositoryTest {

    @Test fun `chronological tour mints sorted years' songs and returns an ordered url`() = runTest {
        val years = listOf(1985, 1990, 1995, 2000, 2005)        // only these resolve; rest are empty
        val source = FakeSource(years.associateWith { y -> (1..3).map { SongRef("A$y", "$it") } })
        val searcher = FakeSearcher { q -> q.replace(" ", "_") }
        val cache = MapCache()
        val minter = FakeMinter()
        val repo = TimeMachineRepository(
            source, searcher, cache, minter,
            YearSelector(random = Random(7), currentYear = { 2026 }),
        )

        val url = repo.launchUrl()

        assertEquals("https://music.youtube.com/watch?list=TLGGtest", url)
        // Years sorted ascending, each year's three songs kept in chart order.
        val expected = years.sorted().flatMap { y -> (1..3).map { "A${y}_$it" } }
        assertEquals(expected, minter.minted)
        // All five fully-resolved years were cached.
        assertEquals(years.toSet(), cache.map.keys)
    }

    @Test fun `cached years resolve without hitting the source or searcher`() = runTest {
        // Range is exactly five years (1980..1984), all pre-cached → backends never touched.
        val cache = MapCache((1980..1984).associateWith { listOf("c$it") })
        val minter = FakeMinter()
        val repo = TimeMachineRepository(
            ThrowingSource, ThrowingSearcher, cache, minter,
            YearSelector(currentYear = { 1985 }),
        )

        val url = repo.launchUrl()

        assertEquals("https://music.youtube.com/watch?list=TLGGtest", url)
        assertEquals(listOf("c1980", "c1981", "c1982", "c1983", "c1984"), minter.minted)
        assertEquals(0, cache.puts)
    }

    @Test fun `returns null and never mints when nothing resolves`() = runTest {
        val source = FakeSource(emptyMap())                     // every year is a stub
        val minter = FakeMinter()
        val repo = TimeMachineRepository(
            source, FakeSearcher { null }, MapCache(), minter,
            YearSelector(currentYear = { 2026 }),
        )

        assertNull(repo.launchUrl())
        assertNull(minter.minted)
    }

    @Test fun `a year with an unresolvable song is played partial but not cached`() = runTest {
        // Single eligible year (1980); its middle song's search returns nothing.
        val source = FakeSource(mapOf(1980 to listOf(SongRef("A", "1"), SongRef("A", "2"), SongRef("A", "3"))))
        val searcher = FakeSearcher { q -> if (q == "A 2") null else q.replace(" ", "_") }
        val cache = MapCache()
        val minter = FakeMinter()
        val repo = TimeMachineRepository(
            source, searcher, cache, minter,
            YearSelector(currentYear = { 1981 }),
        )

        val url = repo.launchUrl()

        assertEquals("https://music.youtube.com/watch?list=TLGGtest", url)
        assertEquals(listOf("A_1", "A_3"), minter.minted)       // order preserved, gap dropped
        assertTrue("partial year must not be cached", cache.map.isEmpty())
    }
}
