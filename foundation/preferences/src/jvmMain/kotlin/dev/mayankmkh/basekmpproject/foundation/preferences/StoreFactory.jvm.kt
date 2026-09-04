package dev.mayankmkh.basekmpproject.foundation.preferences

import dev.mayankmkh.basekmpproject.foundation.runtime.applicationDataDirectory
import java.io.File

internal actual fun PreferencesContext.dataStoreDirectory(): File =
    applicationDataDirectory(applicationId).resolve("datastore")
