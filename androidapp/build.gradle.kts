import dev.mayankmkh.basekmpproject.BkpBuildType
import dev.mayankmkh.basekmpproject.convention.dsl.BkpModuleExtension
import org.gradle.kotlin.dsl.configure

plugins {
    alias(libs.plugins.bkp.android.app.compose)
//    alias(libs.plugins.bkp.android.app.firebase)
    alias(libs.plugins.kotlin.serialization)
}

extensions.configure<BkpModuleExtension> {
    features.flavorsDemoProd.set(true)
//    features.firebase.set(true)
}

android {
    namespace = "dev.mayankmkh.basekmpproject.androidapp"

    defaultConfig {
        applicationId = "dev.mayankmkh.basekmpproject.androidapp"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = BkpBuildType.DEBUG.applicationIdSuffix
        }
        release {
            isMinifyEnabled = true
            applicationIdSuffix = BkpBuildType.RELEASE.applicationIdSuffix
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // To publish on the Play store a private signing key is required, but to allow anyone
            // who clones the code to sign and run the release variant, use the debug signing key.
            // TODO: Abstract the signing configuration to a separate file to avoid hardcoding this.
            signingConfig = signingConfigs.named("debug").get()
        }
    }
    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(projects.shared.app)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
