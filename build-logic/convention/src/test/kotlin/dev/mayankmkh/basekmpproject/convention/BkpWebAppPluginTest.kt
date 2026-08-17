package dev.mayankmkh.basekmpproject.convention

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains

/**
 * `bkp.web.app` is the one primary that declares a target on the module's behalf, so what it
 * declares is worth pinning: exactly `wasmJs`, and an executable rather than a klib.
 */
class BkpWebAppPluginTest {

    @field:TempDir
    lateinit var projectDir: File

    @Test
    fun `web app declares the wasmJs target and nothing else`() {
        val result = TestProject(projectDir).withBuildScript(
            """
            plugins { id("bkp.web.app") }

            afterEvaluate {
                // `metadata` is KGP's own, created for every multiplatform project.
                val names = kotlin.targets.map { it.name }.filter { it != "metadata" }.sorted()
                println("TARGETS=" + names.joinToString(","))
            }
            """,
        ).run()

        assertContains(result.output, "TARGETS=wasmJs")
    }

    /**
     * A library target produces a klib and no `*Run` tasks; the browser needs a bundle. The task is
     * the observable difference, and it is the one a developer actually types.
     */
    @Test
    fun `web app produces a browser executable`() {
        val result = TestProject(projectDir).withBuildScript(
            """
            plugins { id("bkp.web.app") }
            """,
        ).run("tasks", "--all")

        assertContains(result.output, "wasmJsBrowserDevelopmentRun")
    }
}
