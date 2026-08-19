plugins {
    alias(libs.plugins.bkp.android.app.compose)
    //    alias(libs.plugins.bkp.android.app.firebase)
    alias(libs.plugins.kotlin.serialization)
}

bkpModule {
    features {
        demoProdFlavors()
    }
}

android {
    namespace = "dev.mayankmkh.basekmpproject.androidapp"

    defaultConfig {
        applicationId = "dev.mayankmkh.basekmpproject.androidapp"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // To publish on the Play store a private signing key is required, but to allow anyone
            // who clones the code to sign and run the release variant, use the debug signing key.
            // TODO: Abstract the signing configuration to a separate file to avoid hardcoding this.
            signingConfig = signingConfigs.named("debug").get()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(projects.shared.app)
}
