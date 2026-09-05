package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext

internal actual fun createPreferenceDataStore(
    context: PlatformContext,
    file: PrefFile,
    logger: Logger,
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        storage =
            WebLocalStorage(
                serializer = PreferencesSerializer,
                name = "${context.applicationId}.${file.preferencesFileName}",
            ),
        corruptionHandler = replaceCorruptFile(logger, file, "preferences") { emptyPreferences() },
    )

internal actual fun <T> createDocumentDataStore(
    context: PlatformContext,
    file: PrefFile,
    serializer: OkioSerializer<T>,
    logger: Logger,
): DataStore<T> =
    DataStoreFactory.create(
        storage =
            WebLocalStorage(
                serializer = serializer,
                name = "${context.applicationId}.${file.documentFileName}",
            ),
        corruptionHandler =
            replaceCorruptFile(logger, file, "document") { serializer.defaultValue },
    )
