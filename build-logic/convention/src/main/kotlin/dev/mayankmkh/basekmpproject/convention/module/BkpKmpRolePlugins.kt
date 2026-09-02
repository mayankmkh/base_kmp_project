package dev.mayankmkh.basekmpproject.convention.module

import dev.mayankmkh.basekmpproject.configureKMPCompose
import dev.mayankmkh.basekmpproject.convention.core.addAppRoleDependencies
import dev.mayankmkh.basekmpproject.convention.core.addCapabilityImplRoleDependencies
import dev.mayankmkh.basekmpproject.convention.dsl.BkpModuleExtension
import dev.mayankmkh.basekmpproject.convention.helix.HelixRole
import dev.mayankmkh.basekmpproject.convention.helix.recordHelixRole
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

private fun Project.applyRoleBase(role: HelixRole) {
    apply(plugin = "bkp.kmp.lib")
    recordHelixRole(role)
}

private fun Project.applyCompose() {
    apply(plugin = "org.jetbrains.compose")
    apply(plugin = "org.jetbrains.kotlin.plugin.compose")
    configureKMPCompose()
}

private fun Project.enableExplicitApi() {
    extensions.getByType<KotlinMultiplatformExtension>().explicitApi()
}

class BkpKmpAppPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            applyRoleBase(HelixRole.APP)
            applyCompose()
            addAppRoleDependencies()
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
            addCapabilityImplRoleDependencies()
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
