package dev.mayankmkh.basekmpproject.foundation.runtime

import java.io.File

/**
 * Resolves the per-user directory that app-owned desktop stores share: `Library/Application
 * Support` on macOS, `APPDATA` on Windows, `XDG_CONFIG_HOME` or `.config` elsewhere. Nothing is
 * created; callers make the subdirectory they need.
 */
public fun applicationDataDirectory(applicationId: String): File {
    val userHome = File(System.getProperty("user.home"))
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    val base =
        when {
            osName.startsWith("mac") -> userHome.resolve("Library/Application Support")
            osName.startsWith("windows") ->
                environmentDirectory("APPDATA") ?: userHome.resolve("AppData\\Roaming")
            else -> environmentDirectory("XDG_CONFIG_HOME") ?: userHome.resolve(".config")
        }
    return base.resolve(applicationId)
}

private fun environmentDirectory(name: String): File? =
    System.getenv(name)?.takeIf { it.isNotBlank() }?.let(::File)
