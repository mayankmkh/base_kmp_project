package dev.mayankmkh.basekmpproject.foundation.preferences

import java.io.File

internal actual fun PreferencesContext.dataStoreDirectory(): File =
    appContext.filesDir.resolve("datastore")
