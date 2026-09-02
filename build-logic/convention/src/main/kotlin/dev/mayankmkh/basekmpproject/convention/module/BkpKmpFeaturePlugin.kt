package dev.mayankmkh.basekmpproject.convention.module

import dev.mayankmkh.basekmpproject.convention.core.addFeatureRoleDependencies
import dev.mayankmkh.basekmpproject.convention.helix.HelixRole
import org.gradle.api.Plugin
import org.gradle.api.Project

class BkpKmpFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            applyRoleBase(HelixRole.FEATURE)
            applyCompose()
            addFeatureRoleDependencies()
        }
    }
}
