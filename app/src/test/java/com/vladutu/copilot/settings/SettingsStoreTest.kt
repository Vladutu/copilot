package com.vladutu.copilot.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsStoreTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SettingsStore
    private val scope = TestScope(UnconfinedTestDispatcher())

    @Before fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "settings.preferences_pb") },
        )
        store = SettingsStore(dataStore)
    }

    @Test fun `auto-start defaults to false`() = runTest {
        assertEquals(false, store.autoStartFlow.first())
    }

    @Test fun `setAutoStart round-trips`() = runTest {
        store.setAutoStart(true)
        assertEquals(true, store.autoStartFlow.first())
        store.setAutoStart(false)
        assertEquals(false, store.autoStartFlow.first())
    }

    @Test fun `topicFlow is null before any topic exists`() = runTest {
        assertNull(store.topicFlow.first())
    }

    @Test fun `ensureTopic generates a valid topic and persists it`() = runTest {
        val topic = store.ensureTopic()
        assertTrue("'$topic' should be valid", TopicGenerator.isValid(topic))
        assertEquals(topic, store.topicFlow.first())
    }

    @Test fun `ensureTopic is idempotent - second call returns the same topic`() = runTest {
        val first = store.ensureTopic()
        val second = store.ensureTopic()
        assertEquals(first, second)
        assertEquals(first, store.topicFlow.first())
    }

    @Test fun `regenerateTopic replaces the stored topic with a new valid one`() = runTest {
        val original = store.ensureTopic()
        val fresh = store.regenerateTopic()
        assertNotEquals(original, fresh)
        assertTrue(TopicGenerator.isValid(fresh))
        assertEquals(fresh, store.topicFlow.first())
    }
}
