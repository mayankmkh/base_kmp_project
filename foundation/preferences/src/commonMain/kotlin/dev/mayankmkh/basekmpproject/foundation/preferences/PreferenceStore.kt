package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import kotlin.jvm.JvmInline
import kotlinx.coroutines.flow.Flow

@JvmInline public value class PrefFile(public val name: String)

@JvmInline
public value class PrefKey<T> internal constructor(internal val dataStoreKey: Preferences.Key<T>) {
    public val name: String
        get() = dataStoreKey.name
}

public fun booleanPrefKey(name: String): PrefKey<Boolean> = PrefKey(booleanPreferencesKey(name))

public fun intPrefKey(name: String): PrefKey<Int> = PrefKey(intPreferencesKey(name))

public fun longPrefKey(name: String): PrefKey<Long> = PrefKey(longPreferencesKey(name))

public fun floatPrefKey(name: String): PrefKey<Float> = PrefKey(floatPreferencesKey(name))

public fun doublePrefKey(name: String): PrefKey<Double> = PrefKey(doublePreferencesKey(name))

public fun stringPrefKey(name: String): PrefKey<String> = PrefKey(stringPreferencesKey(name))

public fun stringSetPrefKey(name: String): PrefKey<Set<String>> =
    PrefKey(stringSetPreferencesKey(name))

public interface PreferenceStore {
    public suspend fun <T> get(key: PrefKey<T>): T?

    public fun <T> observe(key: PrefKey<T>): Flow<T?>

    public suspend fun <T> set(key: PrefKey<T>, value: T)

    public suspend fun remove(key: PrefKey<*>)

    public suspend fun contains(key: PrefKey<*>): Boolean

    public suspend fun edit(block: PreferenceEditor.() -> Unit)

    public suspend fun clear()
}

public interface PreferenceEditor {
    public fun <T> set(key: PrefKey<T>, value: T)

    public fun remove(key: PrefKey<*>)

    public fun clear()
}

/**
 * Opens one preferences file.
 *
 * [logger] is the app's one logger; this module tags it with its own name, as every module that
 * takes a logger does. It is only ever written to when a file has to be replaced (section 5).
 */
public fun openPreferenceStore(
    context: PlatformContext,
    file: PrefFile,
    logger: Logger,
): PreferenceStore {
    registerOpenFile(file)
    return DataStorePreferenceStore(
        createPreferenceDataStore(context, file, logger.withTag(LogTag))
    )
}

/** A no-filesystem store for common tests and for tests of consumers. */
public fun inMemoryPreferenceStore(): PreferenceStore =
    DataStorePreferenceStore(InMemoryDataStore(emptyPreferences()))
