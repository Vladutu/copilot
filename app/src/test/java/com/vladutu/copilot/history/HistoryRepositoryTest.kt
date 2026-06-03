package com.vladutu.copilot.history

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
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
class HistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var repo: HistoryRepository
    private val storeName = "test_history_${System.nanoTime()}"
    private var fakeNow: Long = 0L

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val ds = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(storeName) }
        )
        val store = HistoryStore(ds)
        repo = HistoryRepository(store, clock = { fakeNow })
    }

    @After fun tearDown() {
        context.preferencesDataStoreFile(storeName).delete()
    }

    private fun item(form: Form, id: String, savedAt: Long, title: String? = id) =
        SavedItem(form, id, title, null, "url-$id", savedAt)

    @Test fun `save then read returns the item`() = runTest {
        repo.save(item(Form.PLAYLIST, "a", 100L))
        val list = repo.itemsFor(Form.PLAYLIST).first()
        assertEquals(1, list.size)
        assertEquals("a", list[0].id)
    }

    @Test fun `re-saving an item refreshes savedAt and meta so it promotes to the top`() = runTest {
        repo.save(item(Form.PLAYLIST, "a", 100L, "first"))
        repo.save(item(Form.PLAYLIST, "b", 150L, "b"))
        repo.save(item(Form.PLAYLIST, "a", 200L, "second"))
        val list = repo.itemsFor(Form.PLAYLIST).first()
        assertEquals(2, list.size)
        assertEquals(listOf("a", "b"), list.map { it.id })
        assertEquals(200L, list[0].savedAt)
        assertEquals("second", list[0].title)
    }

    @Test fun `touch bumps savedAt and re-sorts to the top`() = runTest {
        repo.save(item(Form.PLAYLIST, "old", 100L))
        repo.save(item(Form.PLAYLIST, "mid", 150L))
        repo.save(item(Form.PLAYLIST, "new", 200L))

        fakeNow = 500L
        repo.touch(Form.PLAYLIST, "old")

        val list = repo.itemsFor(Form.PLAYLIST).first()
        assertEquals(listOf("old", "new", "mid"), list.map { it.id })
        assertEquals(500L, list[0].savedAt)
    }

    @Test fun `touch on missing entry or wrong form is a no-op`() = runTest {
        repo.save(item(Form.PLAYLIST, "a", 100L))
        fakeNow = 500L
        repo.touch(Form.SONG, "a")
        repo.touch(Form.PLAYLIST, "missing")

        val e = repo.itemsFor(Form.PLAYLIST).first().single()
        assertEquals(100L, e.savedAt)
    }

    @Test fun `touch only affects the matching form`() = runTest {
        repo.save(item(Form.PLAYLIST, "x", 100L))
        repo.save(item(Form.SONG, "x", 200L))

        fakeNow = 500L
        repo.touch(Form.PLAYLIST, "x")

        assertEquals(500L, repo.itemsFor(Form.PLAYLIST).first().single().savedAt)
        assertEquals(200L, repo.itemsFor(Form.SONG).first().single().savedAt)
    }

    @Test fun `items are sorted newest first`() = runTest {
        repo.save(item(Form.PLAYLIST, "a", 100L))
        repo.save(item(Form.PLAYLIST, "b", 200L))
        repo.save(item(Form.PLAYLIST, "c", 150L))
        val list = repo.itemsFor(Form.PLAYLIST).first()
        assertEquals(listOf("b", "c", "a"), list.map { it.id })
    }

    @Test fun `delete removes the entry`() = runTest {
        repo.save(item(Form.PLAYLIST, "a", 100L))
        repo.save(item(Form.PLAYLIST, "b", 200L))
        repo.delete(Form.PLAYLIST, "a")
        val list = repo.itemsFor(Form.PLAYLIST).first()
        assertEquals(listOf("b"), list.map { it.id })
    }

    @Test fun `delete non-existent is no-op`() = runTest {
        repo.delete(Form.PLAYLIST, "nope")
        val list = repo.itemsFor(Form.PLAYLIST).first()
        assertTrue(list.isEmpty())
    }

    @Test fun `forms are isolated`() = runTest {
        repo.save(item(Form.PLAYLIST, "a", 100L))
        repo.save(item(Form.SONG, "b", 100L))
        repo.save(item(Form.DESTINATION, "c", 100L))
        assertEquals(listOf("a"), repo.itemsFor(Form.PLAYLIST).first().map { it.id })
        assertEquals(listOf("b"), repo.itemsFor(Form.SONG).first().map { it.id })
        assertEquals(listOf("c"), repo.itemsFor(Form.DESTINATION).first().map { it.id })
    }
}
