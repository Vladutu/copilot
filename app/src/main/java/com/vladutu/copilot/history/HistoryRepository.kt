package com.vladutu.copilot.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HistoryRepository(
    private val store: HistoryStore,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
) {

    private val mutex = Mutex()

    fun itemsFor(form: Form): Flow<List<SavedItem>> =
        store.itemsFor(form).map { list -> list.sortedByDescending { it.savedAt } }

    /** Upsert: re-saving an item refreshes savedAt (and any meta) so re-shared
     *  items from Pilot promote back to the top of the list. */
    suspend fun save(item: SavedItem) = mutex.withLock {
        store.mutate(item.form) { current ->
            current.filterNot { it.id == item.id } + item
        }
    }

    /** Bump savedAt to "now" so a successful tap from inside Copilot promotes
     *  the item to the top of its list. */
    suspend fun touch(form: Form, id: String) = mutex.withLock {
        val now = clock()
        store.mutate(form) { current ->
            current.map { if (it.id == id) it.copy(savedAt = now) else it }
        }
    }

    suspend fun delete(form: Form, id: String) = mutex.withLock {
        store.mutate(form) { current -> current.filterNot { it.id == id } }
    }
}
