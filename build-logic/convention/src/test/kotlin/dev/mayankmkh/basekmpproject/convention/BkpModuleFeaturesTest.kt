package dev.mayankmkh.basekmpproject.convention

import java.io.File
import kotlin.test.assertContains
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * What `bkpModule { features { } }` actually does to a module, as opposed to what the validator
 * rejects -- see [BkpValidationGraphPluginTest] for that half.
 */
class BkpModuleFeaturesTest {

    @field:TempDir lateinit var projectDir: File

    @Test
    fun `android app opting into demoProdFlavors gets demo and prod`() {
        val result =
            TestProject(projectDir)
                .withBuildScript(
                    androidApp(
                        """
                bkpModule {
                    features { demoProdFlavors() }
                }
                """
                    )
                )
                .run()

        assertContains(result.output, "FLAVORS=demo,prod")
    }

    @Test
    fun `android app saying nothing gets no flavors`() {
        val result = TestProject(projectDir).withBuildScript(androidApp("")).run()

        assertContains(result.output, "FLAVORS=\n")
    }

    /**
     * The synchronous half of the [dev.mayankmkh.basekmpproject.convention.dsl.BkpTargets]
     * contract: a target declared inside `exception` has its source sets available to the rest of
     * the script, not at some later point in configuration.
     */
    @Test
    fun `documented target exception selects a smaller set synchronously`() {
        val result =
            TestProject(projectDir)
                .withBuildScript(
                    """
            plugins { id("bkp.kmp.lib") }

            kotlin {
                bkpTargets {
                    exception("This module wraps a native-only SDK") {
                        android()
                        ios()
                        jvm()
                    }
                }
                println("ANDROID_SOURCE_SET=" + sourceSets.getByName("androidMain").name)
            }
            """
                )
                .run()

        assertContains(result.output, "ANDROID_SOURCE_SET=androidMain")
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
