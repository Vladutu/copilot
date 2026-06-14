package com.vladutu.copilot.liked

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.vladutu.copilot.nowplaying.NowPlaying
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LikedSongsRepositoryTest {

    private lateinit var context: Context
    private lateinit var repo: LikedSongsRepository
    private val storeName = "test_liked_${System.nanoTime()}"
    private var fakeNow: Long = 0L

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val ds = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(storeName) }
        )
        repo = LikedSongsRepository(LikedSongStore(ds), clock = { fakeNow })
    }

    @After fun tearDown() {
        context.preferencesDataStoreFile(storeName).delete()
    }

    @Test fun `like then read returns the song`() = runTest {
        fakeNow = 100L
        repo.like(NowPlaying("Bad Habits", "Ed Sheeran"))
        val list = repo.items().first()
        assertEquals(1, list.size)
        assertEquals("Bad Habits", list[0].title)
        assertEquals("Ed Sheeran", list[0].artist)
        assertEquals(100L, list[0].savedAt)
    }

    @Test fun `liking the same song again dedups and refreshes savedAt`() = runTest {
        fakeNow = 100L
        repo.like(NowPlaying("Bad Habits", "Ed Sheeran"))
        fakeNow = 150L
        repo.like(NowPlaying("Shivers", "Ed Sheeran"))
        fakeNow = 200L
        repo.like(NowPlaying(" bad habits ", "ED SHEERAN"))

        val list = repo.items().first()
        assertEquals(2, list.size)
        assertEquals("Bad Habits", list[0].title) // refreshed → top (newest first)
        assertEquals(200L, list[0].savedAt)
    }

    @Test fun `items are sorted newest first`() = runTest {
        fakeNow = 100L; repo.like(NowPlaying("A", "x"))
        fakeNow = 200L; repo.like(NowPlaying("B", "x"))
        fakeNow = 150L; repo.like(NowPlaying("C", "x"))
        assertEquals(listOf("B", "C", "A"), repo.items().first().map { it.title })
    }

    @Test fun `delete removes the entry`() = runTest {
        fakeNow = 100L; repo.like(NowPlaying("A", "x"))
        fakeNow = 200L; repo.like(NowPlaying("B", "x"))
        repo.delete(LikedSong("A", "x", savedAt = 100L))
        assertEquals(listOf("B"), repo.items().first().map { it.title })
    }

    @Test fun `clearAll empties the list`() = runTest {
        fakeNow = 100L; repo.like(NowPlaying("A", "x"))
        fakeNow = 200L; repo.like(NowPlaying("B", "x"))
        repo.clearAll()
        assertTrue(repo.items().first().isEmpty())
    }
}
