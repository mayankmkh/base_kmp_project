package dev.mayankmkh.basekmpproject.androidapp

import android.app.Application
import dev.mayankmkh.basekmpproject.shared.app.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        initKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO)
            androidContext(this@MainApplication)
        }
    }
}
