package dev.mayankmkh.basekmpproject.foundation.preferences

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.WebLocalStorage
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext

internal actual fun <T> storageFor(
    context: PlatformContext,
    fileName: String,
    serializer: OkioSerializer<T>,
): Storage<T> =
    WebLocalStorage(serializer = serializer, name = "${context.applicationId}.$fileName")
