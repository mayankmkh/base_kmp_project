plugins {
    alias(libs.plugins.basekmpproject.shared.library)
    alias(libs.plugins.basekmpproject.android.library)
    alias(libs.plugins.basekmpproject.shared.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget()
}
