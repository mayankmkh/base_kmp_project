package dev.mayankmkh.basekmpproject.foundation.runtime

import android.content.Context

public actual class PlatformContext(
    context: Context,
    public actual val applicationId: String,
) {
    public val appContext: Context = context.applicationContext
}
