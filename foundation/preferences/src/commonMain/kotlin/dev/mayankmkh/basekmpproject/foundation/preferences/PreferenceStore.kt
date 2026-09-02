package dev.mayankmkh.basekmpproject.foundation.preferences

import kotlin.jvm.JvmInline
import kotlinx.coroutines.flow.Flow

public interface PrefKey {
    public val key: String
}

@JvmInline public value class PrefFile(public val name: String)

internal val PrefFile.dataStoreFileName
    get() = "$name.preferences_pb"

public interface PreferenceStore {
    public suspend fun getString(key: PrefKey): String?

    public fun observeString(key: PrefKey): Flow<String?>

    public suspend fun putString(key: PrefKey, value: String)

    public suspend fun remove(key: PrefKey)

    public suspend fun contains(key: PrefKey): Boolean
}

public fun openPreferenceStore(prefContext: PrefContext, file: PrefFile): PreferenceStore =
    DataStorePreferenceStore(createDataStore(prefContext, file))

/**
 * A no-filesystem store for tests of consumers; this lives in commonMain because a KMP test source
 * set is not visible outside its module.
 */
public fun inMemoryPreferenceStore(): PreferenceStore =
    DataStorePreferenceStore(InMemoryPreferencesDataStore())
