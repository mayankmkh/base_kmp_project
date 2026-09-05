package dev.mayankmkh.basekmpproject.foundation.preferences

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import kotlinx.serialization.KSerializer

/**
 * Opens the files this app stores small state in. The app builds one factory from its logger and
 * Capability implementations take it to open the files they own (preferences.md section 12).
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

/** The production factory: DataStore files under the platform's application data location. */
public fun preferenceStores(context: PlatformContext, logger: Logger): PreferenceStores =
    DataStorePreferenceStores(context, logger)

/** For tests and previews: nothing touches disk and no file name is registered. */
public fun inMemoryPreferenceStores(): PreferenceStores = InMemoryPreferenceStores

internal class DataStorePreferenceStores(private val context: PlatformContext, logger: Logger) :
    PreferenceStores {
    private val logger: Logger = logger.withTag("preferences")

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
