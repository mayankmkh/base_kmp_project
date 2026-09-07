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

/**
 * These filter with `named(Spec<String>)` rather than `matching { it.name ... }`.
 *
 * Both return a live collection, so both keep catching the tasks AGP, KMP, detekt and Spotless
 * register long after this runs -- which is the whole reason the tier is selected by name. The
 * difference is that `matching` takes a `Spec<Task>` and so has to instantiate every task to ask it
 * its name, while `named` filters on the name the container already knows. For a predicate that
 * only ever looks at the name, the eager one buys nothing.
 */
internal fun Project.registerVerifyFastModule() {
    tasks.register("verifyFastModule") {
        group = "verification"
        description = "Runs this module's Helix fast verification tasks."
        dependsOn(tasks.named { it in FAST_MODULE_TASK_NAMES })
    }
}

internal fun Project.registerVerifyFullModule(predicate: (String) -> Boolean) {
    tasks.register("verifyFullModule") {
        group = "verification"
        description = "Runs this module's Helix full verification tasks."
        dependsOn(tasks.named { predicate(it) })
    }
}
