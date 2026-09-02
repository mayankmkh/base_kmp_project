package dev.mayankmkh.basekmpproject.convention

import java.io.File
import kotlin.test.assertContains
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BkpHelixRolePluginTest {
    @field:TempDir lateinit var projectDir: File

    @Test
    fun `every KMP role plugin applies and records its role`() {
        val roles =
            linkedMapOf(
                ":app:shared" to ("bkp.kmp.app" to "app"),
                ":feature:posts" to ("bkp.kmp.feature" to "feature"),
                ":ui:design-system" to ("bkp.kmp.ui" to "ui"),
                ":capability:posts-api" to ("bkp.kmp.capability.api" to "capability_api"),
                ":capability:posts-impl" to ("bkp.kmp.capability.impl" to "capability_impl"),
                ":foundation:resource" to ("bkp.kmp.foundation.api" to "foundation_api"),
                ":foundation:network" to ("bkp.kmp.foundation.runtime" to "foundation_runtime"),
                ":platform:connectivity" to ("bkp.kmp.platform" to "platform"),
                ":platform:location-api" to ("bkp.kmp.platform.api" to "platform_api"),
                ":platform:location-impl" to ("bkp.kmp.platform.impl" to "platform_impl"),
                ":storage:database" to ("bkp.kmp.storage" to "storage"),
                ":testkit:common" to ("bkp.kmp.testkit" to "testkit"),
            )
        val project = TestProject(projectDir).withBuildScript("")
        roles.forEach { (path, pluginAndRole) ->
            val (plugin, role) = pluginAndRole
            val composeOptIn =
                if (plugin == "bkp.kmp.foundation.api") {
                    "bkpModule { features { compose() } }"
                } else {
                    ""
                }
            project.withModule(
                path,
                """
                import dev.mayankmkh.basekmpproject.convention.helix.HelixModuleExtension
                import org.gradle.kotlin.dsl.getByType

                plugins { id("$plugin") }

                kotlin { bkpTargets { exception("TestKit keeps role tests JVM-only") { jvm() } } }
                $composeOptIn

                afterEvaluate {
                    val recorded = extensions.getByType<HelixModuleExtension>().roles.get().single()
                    println("ROLE_$role=" + recorded.policyName)
                }
                """,
            )
        }

        val result = project.run()
        roles.values.forEach { (_, role) -> assertContains(result.output, "ROLE_$role=$role") }
        assertContains(result.output, "ROLE_feature=feature")
    }
}
