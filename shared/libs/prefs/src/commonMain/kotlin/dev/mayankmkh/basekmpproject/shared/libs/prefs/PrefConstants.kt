package dev.mayankmkh.basekmpproject.shared.libs.prefs

internal interface PrefKey {
    val key: String
}

internal enum class PrefFile(val fileName: String) {
    CREDENTIALS("credentials")
}

internal val PrefFile.dataStoreFileName
    get() = "$fileName.preferences_pb"
