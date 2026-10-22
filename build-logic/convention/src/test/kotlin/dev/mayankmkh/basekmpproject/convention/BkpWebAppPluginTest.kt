package dev.mayankmkh.basekmpproject.convention

import java.io.File
import kotlin.test.assertContains
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * `bkp.web.app` is the one primary that declares a target on the module's behalf, so what it
 * declares is worth pinning: exactly `wasmJs`, and an executable rather than a klib.
 */
class BkpWebAppPluginTest {

    @field:TempDir lateinit var projectDir: File

    @Test
    fun `web app declares the wasmJs target and nothing else`() {
        val result =
            TestProject(projectDir)
                .withBuildScript(
                    """
            plugins { id("bkp.web.app") }

            afterEvaluate {
                // `metadata` is KGP's own, created for every multiplatform project.
                val names = kotlin.targets.map { it.name }.filter { it != "metadata" }.sorted()
                println("TARGETS=" + names.joinToString(","))
            }
            """
                )
                .run()

        assertContains(result.output, "TARGETS=wasmJs")
    }

    /**
     * A library target produces a klib and no `*Run` tasks; the browser needs a bundle. The task is
     * the observable difference, and it is the one a developer actually types.
     */
    @Test
    fun `web app produces a browser executable`() {
        val result =
            TestProject(projectDir)
                .withBuildScript(
                    """
            plugins { id("bkp.web.app") }
            """
                )
                .run("tasks", "--all")

        assertContains(result.output, "wasmJsBrowserDevelopmentRun")
    }

    /**
     * Both executable links run in the one Kotlin daemon, and two whole-program Wasm links at once
     * can exceed its heap, so the production link waits for the development one.
     */
    @Test
    fun `production wasm link runs after the development link`() {
        val result =
            TestProject(projectDir)
                .withBuildScript(
                    """
            plugins { id("bkp.web.app") }

            gradle.taskGraph.whenReady {
                val production = tasks.getByName("compileProductionExecutableKotlinWasmJs")
                val after = production.mustRunAfter.getDependencies(production).map { it.name }
                println("PRODUCTION_LINK_AFTER=" + after.sorted().joinToString(","))
            }
            """
                )
                .run("compileProductionExecutableKotlinWasmJs", "--dry-run")

        assertContains(
            result.output,
            Regex("PRODUCTION_LINK_AFTER=.*compileDevelopmentExecutableKotlinWasmJs"),
        )
    }
}
