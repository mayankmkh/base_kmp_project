package dev.mayankmkh.basekmpproject.convention.core

import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

private val FAST_MODULE_TASK_NAMES =
    setOf(
        "jvmTest",
        "test",
        "compileCommonMainKotlinMetadata",
        "compileKotlinMetadata",
        "detektAll",
        "spotlessCheck",
    )

internal fun Project.registerVerifyFastModule() {
    tasks.register("verifyFastModule") {
        group = "verification"
        description = "Runs this module's Helix fast verification tasks."
        dependsOn(tasks.matching { it.name in FAST_MODULE_TASK_NAMES })
    }
}

internal fun Project.registerVerifyFullModule(predicate: (String) -> Boolean) {
    tasks.register("verifyFullModule") {
        group = "verification"
        description = "Runs this module's Helix full verification tasks."
        dependsOn(tasks.matching { predicate(it.name) })
    }
}
