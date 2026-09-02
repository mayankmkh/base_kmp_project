package dev.mayankmkh.basekmpproject.platform.connectivity

import android.content.Context

actual class ConnectivityContext(context: Context) {
    internal val appContext: Context = context.applicationContext
}
