package dev.mayankmkh.basekmpproject.convention

import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BkpHelixGraphTest {
    @field:TempDir lateinit var projectDir: File

    @Test
    fun `wrongly placed role fires path mismatch`() {
        val result =
            helixProject()
                .withModule(":storage:oops", roleModule("bkp.kmp.feature"))
                .runAndFail("checkModuleGraph")

        assertContains(result.output, "[MOD-PATH-ROLE-MISMATCH] :storage:oops")
    }

    @Test
    fun `feature to capability impl is denied`() {
        val result =
            helixProject()
                .withModule(
                    ":feature:consumer",
                    roleModule(
                        "bkp.kmp.feature",
                        "implementation(project(\":capability:secret-impl\"))",
                    ),
                )
                .withModule(
                    ":capability:secret-impl",
                    roleModule("bkp.kmp.capability.impl"),
                )
                .runAndFail("checkModuleGraph")

        assertContains(
            result.output,
            "[DEP-ROLE-DENIED] :feature:consumer -> :capability:secret-impl",
        )
    }

    @Test
    fun `feature to capability api is allowed`() {
        val result =
            helixProject()
                .withModule(
                    ":feature:consumer",
                    roleModule(
                        "bkp.kmp.feature",
                        "implementation(project(\":capability:public-api\"))",
                    ),
                )
                .withModule(
                    ":capability:public-api",
                    roleModule("bkp.kmp.capability.api"),
                )
                .run("checkModuleGraph")

        assertFalse(result.output.contains("DEP-ROLE-DENIED"))
    }

    @Test
    fun `schema two report includes graph context metadata`() {
        helixProject()
            .withModule(
                ":feature:consumer",
                roleModule(
                    "bkp.kmp.feature",
                    "api(project(\":capability:public-api\"))",
                ),
                mapOf(
                    "src/commonMain/kotlin/example/api/Consumer.kt" to
                        "package example.api\n\npublic interface Consumer\n"
                ),
            )
            .withModule(
                ":capability:public-api",
                roleModule("bkp.kmp.capability.api"),
                mapOf(
                    "src/commonMain/kotlin/example/PublicApi.kt" to
                        "package example\n\npublic interface PublicApi\n"
                ),
            )
            .run("checkModuleGraph")

        val report = projectDir.resolve("build/reports/helix/module-graph.json").readText()
        assertContains(report, "\"schema\": 2")
        assertContains(report, "\"projectDir\": \"feature/consumer\"")
        assertContains(report, "\"targets\"")
        assertContains(report, "\"publicApiDirs\"")
        assertContains(report, "\"configuration\": \"api\"")
    }

    @Test
    fun `active exception downgrades denied edge to warning`() {
        val result =
            deniedFeatureProject(
                    exceptions(
                        expires = "2099-01-01",
                        rule = "DEP-ROLE-DENIED",
                        scope = ":feature:consumer -> :capability:secret-impl",
                    )
                )
                .run("checkModuleGraph")

        assertContains(result.output, "temporary exception")
        assertContains(result.output, "[DEP-ROLE-DENIED]")
    }

    @Test
    fun `expired exception fails with stable rule id`() {
        val result =
            deniedFeatureProject(
                    exceptions(
                        expires = "2000-01-01",
                        rule = "DEP-ROLE-DENIED",
                        scope = ":feature:consumer -> :capability:secret-impl",
                    )
                )
                .runAndFail("checkModuleGraph")

        assertContains(result.output, "[EXC-EXPIRED]")
    }

    @Test
    fun `api impl family collapse detects logical cycle`() {
        val result =
            helixProject()
                .withModule(
                    ":platform:a-api",
                    roleModule("bkp.kmp.platform.api"),
                )
                .withModule(
                    ":platform:a-impl",
                    roleModule(
                        "bkp.kmp.platform.impl",
                        "implementation(project(\":platform:b-api\"))",
                    ),
                )
                .withModule(
                    ":platform:b-api",
                    roleModule("bkp.kmp.platform.api"),
                )
                .withModule(
                    ":platform:b-impl",
                    roleModule(
                        "bkp.kmp.platform.impl",
                        "implementation(project(\":platform:a-api\"))",
                    ),
                )
                .runAndFail("checkModuleGraph")

        assertContains(result.output, "[GRAPH-CYCLE-LOGICAL]")
        assertFalse(result.output.contains("[GRAPH-CYCLE-PHYSICAL]"))
    }

    @Test
    fun `an app shell may depend on the shared app root`() {
        val result =
            helixProject()
                .withModule(":app:shared", roleModule("bkp.kmp.app"))
                .withModule(
                    ":app:host",
                    roleModule("bkp.kmp.app", "implementation(project(\":app:shared\"))"),
                )
                .run("checkModuleGraph")

        assertFalse(result.output.contains("DEP-ROLE-DENIED"))
        assertFalse(result.output.contains("MOD-PATH-ROLE-MISMATCH"))
    }

    @Test
    fun `the adoption-era shared path is no longer grandfathered`() {
        val result =
            helixProject()
                .withModule(":shared:app", roleModule("bkp.kmp.app"))
                .runAndFail("checkModuleGraph")

        assertContains(result.output, "[MOD-PATH-ROLE-MISMATCH] :shared:app")
    }

    @Test
    fun `a module with no role plugin is reported wherever it sits`() {
        val result =
            helixProject()
                .withModule(
                    ":shared:libs",
                    """
                    plugins { id("bkp.kmp.lib") }
                    kotlin { bkpTargets { exception("graph tests stay JVM-only") { jvm() } } }
                    """,
                )
                .runAndFail("checkModuleGraph")

        assertContains(result.output, "[MOD-ROLE-MISSING] :shared:libs")
    }

    @Test
    fun `a public declaration added after a cached run is still caught`() {
        val project =
            helixProject()
                .withModule(
                    ":feature:late",
                    roleModule("bkp.kmp.feature"),
                    mapOf(
                        "$FEATURE_SOURCE_ROOT/Existing.kt" to
                            "package dev.mayankmkh.basekmpproject.feature.late\n\n" +
                                "internal val existing: Int = 1\n"
                    ),
                )

        val first = project.run("checkModuleGraph", "--configuration-cache")
        assertFalse(first.output.contains("FEATURE-PUBLIC-SURFACE-OUTSIDE-API"))

        // No `--rerun-tasks`, and nothing that would reconfigure the build: the file is new, and
        // only a source *directory* declared as a task input can notice it on a cache hit.
        project.withFile(
            "feature/late/$FEATURE_SOURCE_ROOT/Leaked.kt",
            "package dev.mayankmkh.basekmpproject.feature.late\n\nfun leaked(): Int = 2\n",
        )

        val second = project.runExpectingFailure("checkModuleGraph", "--configuration-cache")
        assertContains(second.output, "Reusing configuration cache")
        assertContains(second.output, "[FEATURE-PUBLIC-SURFACE-OUTSIDE-API] :feature:late")
    }

    private fun deniedFeatureProject(exceptionJson: String): TestProject =
        helixProject(exceptionJson)
            .withModule(
                ":feature:consumer",
                roleModule(
                    "bkp.kmp.feature",
                    "implementation(project(\":capability:secret-impl\"))",
                ),
            )
            .withModule(
                ":capability:secret-impl",
                roleModule("bkp.kmp.capability.impl"),
            )

    private fun helixProject(exceptionJson: String = EMPTY_EXCEPTIONS): TestProject =
        TestProject(projectDir)
            .withBuildScript("plugins { id(\"bkp.validation.graph\") }")
            .withFile("config/helix/dependency-policy.json", POLICY)
            .withFile("config/helix/exceptions.json", exceptionJson)

    private fun roleModule(plugin: String, dependency: String = ""): String =
        """
        plugins { id("$plugin") }
        kotlin {
            bkpTargets { exception("TestKit keeps graph tests JVM-only") { jvm() } }
            sourceSets.commonMain.dependencies { $dependency }
        }
        """

    private fun exceptions(expires: String, rule: String, scope: String): String =
        """
        {
          "schema": 1,
          "exceptions": [
            {
              "rule": "$rule",
              "scope": "$scope",
              "owner": "architecture-test",
              "reason": "proves downgrade semantics",
              "created": "2026-01-01",
              "expires": "$expires",
              "removalCondition": "test completes"
            }
          ]
        }
        """
            .trimIndent()

    private fun TestProject.runAndFail(vararg tasks: String) = runExpectingFailure(*tasks)

    companion object {
        private const val FEATURE_SOURCE_ROOT =
            "src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/late"
        private const val EMPTY_EXCEPTIONS = """{"schema":1,"exceptions":[]}"""
        private val POLICY =
            """
            {
              "schema": 2,
              "defaultDecision": "deny",
              "roles": {
                "app": { "allow": ["feature", "ui", "capability_api", "capability_impl", "foundation_api", "foundation_runtime", "platform", "platform_api", "platform_impl", "storage"] },
                "feature": { "allow": ["ui", "capability_api", "foundation_api", "platform", "platform_api"] },
                "ui": { "allow": ["ui", "foundation_api"] },
                "capability_api": { "allow": ["capability_api", "foundation_api"] },
                "capability_impl": { "allow": ["capability_api", "foundation_api", "foundation_runtime", "platform", "platform_api", "storage"] },
                "foundation_api": { "allow": ["foundation_api"] },
                "foundation_runtime": { "allow": ["foundation_api", "foundation_runtime"] },
                "platform": { "allow": ["foundation_api", "foundation_runtime"] },
                "platform_api": { "allow": ["foundation_api"] },
                "platform_impl": { "allow": ["platform_api", "foundation_api", "foundation_runtime"] },
                "storage": { "allow": ["foundation_api", "foundation_runtime"] }
              },
              "conditionalAllows": []
            }
            """
                .trimIndent()
    }
}
