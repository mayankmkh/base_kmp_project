package dev.mayankmkh.basekmpproject.androidapp

import android.app.Application
import dev.mayankmkh.basekmpproject.app.shared.config.BuildEnvironment
import dev.mayankmkh.basekmpproject.app.shared.di.initKoin
import org.koin.android.ext.koin.androidContext

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Both build signals come from the variant: `DEBUG` from the build type, `APP_ENVIRONMENT`
        // from the product flavor the convention plugin registered, whose name is the environment
        // id. The two axes are independent, so all four variants are meaningful.
        initKoin(
            isDebug = BuildConfig.DEBUG,
            environment = BuildEnvironment.fromId(BuildConfig.APP_ENVIRONMENT),
        ) {
            androidContext(this@MainApplication)
        }
    }
}
