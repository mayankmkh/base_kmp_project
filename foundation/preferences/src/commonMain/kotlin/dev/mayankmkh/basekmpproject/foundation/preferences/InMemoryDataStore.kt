package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mirrors DataStore's whole-snapshot emission so public adapters are tested without a filesystem.
 */
internal class InMemoryDataStore<T>(initialValue: T) : DataStore<T> {
    private val mutex = Mutex()
    private var current = initialValue
    private val snapshots = MutableSharedFlow<T>(replay = 1).apply { tryEmit(initialValue) }

    override val data: Flow<T> = snapshots.asSharedFlow()

    override suspend fun updateData(transform: suspend (T) -> T): T = mutex.withLock {
        transform(current).also { updated ->
            current = updated
            snapshots.emit(updated)
        }
    }
}
