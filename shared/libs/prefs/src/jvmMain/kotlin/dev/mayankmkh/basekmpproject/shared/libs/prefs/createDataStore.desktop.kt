package dev.mayankmkh.basekmpproject.shared.libs.prefs

import java.io.File

internal actual fun createDataStorePath(prefContext: PrefContext, prefFile: PrefFile): String {
    val file = File(System.getProperty("java.io.tmpdir"), prefFile.dataStoreFileName)
    return file.absolutePath
}
