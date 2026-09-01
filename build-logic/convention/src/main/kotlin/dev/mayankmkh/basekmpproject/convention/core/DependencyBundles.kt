package dev.mayankmkh.basekmpproject.convention.core

import dev.mayankmkh.basekmpproject.libs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

internal fun Project.addKmpFeatureBundle() {
    dependencies {
        "commonMainImplementation"(project(":shared:libs:arch:core"))
        "commonMainImplementation"(project(":shared:libs:coroutines-x"))
        "commonMainImplementation"(project(":shared:libs:networking"))
        "commonMainImplementation"(project(":shared:libs:designsystem"))

        "commonMainImplementation"(libs.findLibrary("androidx.lifecycle.viewmodel").get())
        "commonMainImplementation"(libs.findLibrary("androidx.lifecycle.viewmodel.compose").get())

        val koinBom = libs.findLibrary("koin.bom").get()
        "commonMainImplementation"(platform(koinBom))
        "commonMainImplementation"(libs.findLibrary("koin.core").get())
        "commonMainImplementation"(libs.findLibrary("koin.compose.viewmodel").get())
    }
}
