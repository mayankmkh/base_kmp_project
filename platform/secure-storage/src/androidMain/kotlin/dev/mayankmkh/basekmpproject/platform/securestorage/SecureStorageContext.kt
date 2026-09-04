package dev.mayankmkh.basekmpproject.platform.securestorage

import android.content.Context

public actual class SecureStorageContext(context: Context, internal val applicationId: String) {
    internal val appContext: Context = context.applicationContext
}
