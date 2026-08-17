package dev.mayankmkh.basekmpproject.shared.libs.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File

/** Rename this alongside the desktop distribution's `packageName` when forking the template. */
private const val APP_DIRECTORY_NAME = "dev.mayankmkh.base_kmp_project"

/**
 * The per-user application data directory, by OS convention. Not the `java.io.tmpdir` the DataStore
 * guide reaches for and then warns may be cleared on reboot.
 */
private val appDataDirectory: File
    get() {
        val home = File(System.getProperty("user.home"))
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val base =
            when {
                osName.startsWith("mac") -> home.resolve("Library/Application Support")
                osName.startsWith("windows") ->
                    environmentDirectory("APPDATA") ?: home.resolve("AppData\\Roaming")
                else -> environmentDirectory("XDG_DATA_HOME") ?: home.resolve(".local/share")
            }
        return base.resolve(APP_DIRECTORY_NAME)
    }

private fun environmentDirectory(name: String): File? =
    System.getenv(name)?.takeIf { it.isNotBlank() }?.let(::File)

internal actual fun createDataStore(
    prefContext: PrefContext,
    prefFile: PrefFile,
): DataStore<Preferences> = createDataStore {
    appDataDirectory.resolve(prefFile.dataStoreFileName).absolutePath
}
