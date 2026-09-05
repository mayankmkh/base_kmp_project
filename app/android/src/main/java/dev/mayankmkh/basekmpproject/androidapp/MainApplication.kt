package dev.mayankmkh.basekmpproject.androidapp

import android.app.Application
import dev.mayankmkh.basekmpproject.app.shared.di.initKoin
import org.koin.android.ext.koin.androidContext

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        initKoin(isDebug = BuildConfig.DEBUG) { androidContext(this@MainApplication) }
    }
}
