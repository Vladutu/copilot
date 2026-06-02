package com.vladutu.copilot.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HistoryRepository(private val store: HistoryStore) {

    private val mutex = Mutex()

    fun itemsFor(form: Form): Flow<List<SavedItem>> =
        store.itemsFor(form).map { list -> list.sortedByDescending { it.savedAt } }

    suspend fun save(item: SavedItem) = mutex.withLock {
        store.mutate(item.form) { current ->
            if (current.any { it.id == item.id }) current
            else current + item
        }
    }

    suspend fun delete(form: Form, id: String) = mutex.withLock {
        store.mutate(form) { current -> current.filterNot { it.id == id } }
    }
}
