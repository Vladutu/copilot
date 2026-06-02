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

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val ds = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(storeName) }
        )
        val store = HistoryStore(ds)
        repo = HistoryRepository(store)
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

    @Test fun `duplicate save is no-op and keeps original savedAt`() = runTest {
        repo.save(item(Form.PLAYLIST, "a", 100L, "first"))
        repo.save(item(Form.PLAYLIST, "a", 200L, "second"))
        val list = repo.itemsFor(Form.PLAYLIST).first()
        assertEquals(1, list.size)
        assertEquals(100L, list[0].savedAt)
        assertEquals("first", list[0].title)
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
