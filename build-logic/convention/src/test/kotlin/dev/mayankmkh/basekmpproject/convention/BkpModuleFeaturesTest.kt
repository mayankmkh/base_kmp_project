package dev.mayankmkh.basekmpproject.convention

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains

/**
 * What `bkpModule { features { } }` actually does to a module, as opposed to what the validator
 * rejects -- see [BkpValidationGraphPluginTest] for that half.
 */
class BkpModuleFeaturesTest {

    @field:TempDir
    lateinit var projectDir: File

    @Test
    fun `android app opting into demoProdFlavors gets demo and prod`() {
        val result = TestProject(projectDir).withBuildScript(
            androidApp(
                """
                bkpModule {
                    features { demoProdFlavors() }
                }
                """,
            ),
        ).run()

        assertContains(result.output, "FLAVORS=demo,prod")
    }

    @Test
    fun `android app saying nothing gets no flavors`() {
        val result = TestProject(projectDir).withBuildScript(androidApp("")).run()

        assertContains(result.output, "FLAVORS=\n")
    }

    /**
     * The reporting `finalizeDsl` is registered from the build script, so it runs after the one
     * `BkpAndroidAppPlugin` registers during apply -- which is where the flavors come from. Reading
     * `productFlavors` from a task's `doLast` would work too, but this keeps the whole assertion at
     * configuration time, where the feature is decided.
     */
    private fun androidApp(features: String) =
        """
        plugins { id("bkp.android.app") }

        android {
            namespace = "dev.mayankmkh.basekmpproject.test"
        }

        $features

        androidComponents {
            finalizeDsl { app ->
                println("FLAVORS=" + app.productFlavors.map { it.name }.sorted().joinToString(","))
            }
        }
        """
}
