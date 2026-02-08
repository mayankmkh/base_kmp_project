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

        "commonMainImplementation"(libs.findLibrary("decompose.decompose").get())
        "commonMainImplementation"(libs.findLibrary("decompose.extensions.compose").get())
        "commonMainImplementation"(libs.findLibrary("essenty.lifecycle.coroutines").get())

        val koinBom = libs.findLibrary("koin.bom").get()
        "commonMainImplementation"(platform(koinBom))
        "commonMainImplementation"(libs.findLibrary("koin.core").get())
    }
}
