package dev.mayankmkh.basekmpproject.foundation.preferences

import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.applicationDataDirectory
import java.io.File

internal actual fun PlatformContext.dataStoreDirectory(): File =
    applicationDataDirectory(applicationId).resolve("datastore")
