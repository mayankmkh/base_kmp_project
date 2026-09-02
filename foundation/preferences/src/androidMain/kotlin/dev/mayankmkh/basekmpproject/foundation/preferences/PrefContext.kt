package dev.mayankmkh.basekmpproject.foundation.preferences

import android.content.Context

public actual class PrefContext(context: Context) {
    internal val appContext = context.applicationContext
}
