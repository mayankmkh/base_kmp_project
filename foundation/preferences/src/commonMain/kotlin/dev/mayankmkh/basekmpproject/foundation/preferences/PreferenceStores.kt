package dev.mayankmkh.basekmpproject.foundation.preferences

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import kotlinx.serialization.KSerializer

/**
 * Opens the files this app stores small state in.
 *
 * A logger is a property of the process, not of a store, so the app builds one factory and every
 * store it opens writes through the same tagged logger. Capability implementations resolve this
 * factory from Koin and name their own files.
 */
public interface PreferenceStores {
    /** Opens one preferences file. */
    public fun open(file: PrefFile): PreferenceStore

    /** Opens one document file, encoded with the module's own lenient JSON. */
    public fun <T> openDocument(
        file: PrefFile,
        serializer: KSerializer<T>,
        defaultValue: T,
    ): DocumentStore<T>
}

/**
 * The production factory: DataStore files under the platform's application data location.
 *
 * [logger] is the app's logger; the module tags it with its own name and hands the tagged logger to
 * every store it opens.
 */
public fun preferenceStores(context: PlatformContext, logger: Logger): PreferenceStores =
    DataStorePreferenceStores(context, logger)

/** For tests and previews: nothing touches disk and no file name is registered. */
public fun inMemoryPreferenceStores(): PreferenceStores = InMemoryPreferenceStores

internal class DataStorePreferenceStores(private val context: PlatformContext, logger: Logger) :
    PreferenceStores {
    internal val logger: Logger = logger.withTag(LogTag)

    override fun open(file: PrefFile): PreferenceStore {
        registerOpenFile(file)
        return DataStorePreferenceStore(createPreferenceDataStore(context, file, logger))
    }

    override fun <T> openDocument(
        file: PrefFile,
        serializer: KSerializer<T>,
        defaultValue: T,
    ): DocumentStore<T> {
        registerOpenFile(file)
        return DataStoreDocumentStore(
            createDocumentDataStore(
                context,
                file,
                JsonDocumentSerializer(serializer, defaultValue),
                logger,
            )
        )
    }
}

// No registration: a test that opens the same file twice is asking for two independent stores, and
// nothing here is process-lifetime state.
private object InMemoryPreferenceStores : PreferenceStores {
    override fun open(file: PrefFile): PreferenceStore = inMemoryPreferenceStore()

    override fun <T> openDocument(
        file: PrefFile,
        serializer: KSerializer<T>,
        defaultValue: T,
    ): DocumentStore<T> = inMemoryDocumentStore(defaultValue)
}
