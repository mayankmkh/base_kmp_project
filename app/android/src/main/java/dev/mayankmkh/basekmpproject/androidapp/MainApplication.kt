package dev.mayankmkh.basekmpproject.androidapp

import android.app.Application
import dev.mayankmkh.basekmpproject.app.shared.di.initKoin
import org.koin.android.ext.koin.androidContext

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // `BuildConfig.DEBUG` is the one debug signal that exists before the graph does, and the
        // shared module has no `BuildConfig` of its own. Koin's own diagnostics travel through the
        // app's Kermit logger on every target, so there is no `androidLogger` here.
        initKoin(isDebug = BuildConfig.DEBUG) { androidContext(this@MainApplication) }
    }
}
