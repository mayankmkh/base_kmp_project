package dev.mayankmkh.basekmpproject.convention.core

import dev.mayankmkh.basekmpproject.libs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

internal fun Project.addFeatureRoleDependencies() {
    dependencies {
        "commonMainImplementation"(libs.findLibrary("androidx.lifecycle.viewmodel").get())
        "commonMainImplementation"(libs.findLibrary("androidx.lifecycle.viewmodel.compose").get())
    }
    addKoinDependencies(includeComposeViewModel = true)
}

internal fun Project.addCapabilityImplRoleDependencies() {
    addKoinDependencies(includeComposeViewModel = false)
}

private fun Project.addKoinDependencies(includeComposeViewModel: Boolean) {
    dependencies {
        "commonMainImplementation"(platform(libs.findLibrary("koin.bom").get()))
        "commonMainImplementation"(libs.findLibrary("koin.core").get())
        if (includeComposeViewModel) {
            "commonMainImplementation"(libs.findLibrary("koin.compose.viewmodel").get())
        }
    }
}
