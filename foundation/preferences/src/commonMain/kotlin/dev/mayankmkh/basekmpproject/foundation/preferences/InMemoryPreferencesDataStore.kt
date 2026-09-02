package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [DataStore] with no file behind it, so the preference code can be exercised on every target
 * without a filesystem or a `PrefContext`.
 *
 * Deliberately a shared flow rather than a state flow: the real store re-emits the whole snapshot
 * on every write, even one that changes nothing, and a state flow would silently swallow that.
 */
internal class InMemoryPreferencesDataStore : DataStore<Preferences> {

    private val mutex = Mutex()
    private var current: Preferences = emptyPreferences()
    private val snapshots =
        MutableSharedFlow<Preferences>(replay = 1).apply { tryEmit(emptyPreferences()) }

    override val data: Flow<Preferences> = snapshots.asSharedFlow()

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(current)
            current = updated
            snapshots.emit(updated)
            updated
        }
}
