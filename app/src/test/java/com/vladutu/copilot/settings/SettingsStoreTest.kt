package com.vladutu.copilot.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
