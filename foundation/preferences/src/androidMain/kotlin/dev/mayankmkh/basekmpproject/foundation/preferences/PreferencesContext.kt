package dev.mayankmkh.basekmpproject.foundation.preferences

import android.content.Context

public actual class PreferencesContext(context: Context) {
    internal val appContext: Context = context.applicationContext
}
