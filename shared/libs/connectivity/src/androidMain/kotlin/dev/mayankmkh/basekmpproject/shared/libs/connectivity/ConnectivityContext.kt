package dev.mayankmkh.basekmpproject.shared.libs.connectivity

import android.content.Context

actual class ConnectivityContext(context: Context) {
    internal val appContext: Context = context.applicationContext
}
