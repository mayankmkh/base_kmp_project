package dev.mayankmkh.basekmpproject.convention.module

import dev.mayankmkh.basekmpproject.configureKMPCompose
import dev.mayankmkh.basekmpproject.convention.core.addCapabilityImplRoleDependencies
import dev.mayankmkh.basekmpproject.convention.core.addFeatureRoleDependencies
import dev.mayankmkh.basekmpproject.convention.dsl.BkpModuleExtension
import dev.mayankmkh.basekmpproject.convention.helix.HelixRole
import dev.mayankmkh.basekmpproject.convention.helix.recordHelixRole
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal fun Project.applyRoleBase(role: HelixRole) {
    apply(plugin = "bkp.kmp.lib")
    recordHelixRole(role)
}

internal fun Project.applyCompose() {
    apply(plugin = "org.jetbrains.compose")
    apply(plugin = "org.jetbrains.kotlin.plugin.compose")
    configureKMPCompose()
}

internal fun Project.enableExplicitApi() {
    extensions.getByType<KotlinMultiplatformExtension>().explicitApi()
}

internal fun Project.applyKoinCompiler() {
    apply(plugin = "io.insert-koin.compiler.plugin")
}

class BkpKmpAppPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            applyRoleBase(HelixRole.APP)
            applyKoinCompiler()
            applyCompose()
            addFeatureRoleDependencies()
        }
}

class BkpKmpUiPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            applyRoleBase(HelixRole.UI)
            applyCompose()
            enableExplicitApi()
        }
}

class BkpKmpCapabilityApiPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            applyRoleBase(HelixRole.CAPABILITY_API)
            enableExplicitApi()
        }
}

class BkpKmpCapabilityImplPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            applyRoleBase(HelixRole.CAPABILITY_IMPL)
            applyKoinCompiler()
            addCapabilityImplRoleDependencies()
            enableExplicitApi()
            pluginManager.withPlugin("app.cash.sqldelight") {
                // A contributor sees only its own files, so repo-wide migration numbers look
                // falsely gapped here. The composed sequence is verified in :storage:database.
                tasks
                    .matching { it.name.startsWith("verify") && it.name.endsWith("Migration") }
                    .configureEach { enabled = false }
            }
        }
}

class BkpKmpFoundationApiPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            applyRoleBase(HelixRole.FOUNDATION_API)
            enableExplicitApi()
            extensions.getByType<BkpModuleExtension>().features.whenComposeEnabled {
                applyCompose()
            }
        }
}

class BkpKmpFoundationRuntimePlugin : Plugin<Project> {
    override fun apply(target: Project) = target.applyRoleBase(HelixRole.FOUNDATION_RUNTIME)
}

class BkpKmpPlatformPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.applyRoleBase(HelixRole.PLATFORM)
}

class BkpKmpPlatformApiPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            applyRoleBase(HelixRole.PLATFORM_API)
            enableExplicitApi()
        }
}

class BkpKmpPlatformImplPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.applyRoleBase(HelixRole.PLATFORM_IMPL)
}

class BkpKmpStoragePlugin : Plugin<Project> {
    override fun apply(target: Project) = target.applyRoleBase(HelixRole.STORAGE)
}

class BkpKmpTestkitPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.applyRoleBase(HelixRole.TESTKIT)
}
