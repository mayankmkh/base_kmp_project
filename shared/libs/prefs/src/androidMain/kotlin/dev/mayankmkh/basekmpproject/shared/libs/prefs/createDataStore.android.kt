package dev.mayankmkh.basekmpproject.shared.libs.prefs

internal actual fun createDataStorePath(prefContext: PrefContext, prefFile: PrefFile): String {
    return prefContext.appContext.filesDir.resolve(prefFile.dataStoreFileName).absolutePath
}
