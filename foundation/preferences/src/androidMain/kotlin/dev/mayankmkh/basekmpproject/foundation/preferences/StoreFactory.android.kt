package dev.mayankmkh.basekmpproject.foundation.preferences

import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import java.io.File

internal actual fun PlatformContext.dataStoreDirectory(): File =
    appContext.filesDir.resolve("datastore")
