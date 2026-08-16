package dev.mayankmkh.basekmpproject.convention.dsl

import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType

internal fun Project.bkpModuleExtension(): BkpModuleExtension =
    extensions.findByType<BkpModuleExtension>() ?: extensions.create<BkpModuleExtension>("bkpModule")
