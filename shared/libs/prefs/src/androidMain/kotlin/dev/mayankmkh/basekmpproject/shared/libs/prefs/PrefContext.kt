package dev.mayankmkh.basekmpproject.shared.libs.prefs

import android.content.Context

actual class PrefContext(context: Context) {
    internal val appContext = context.applicationContext
}
