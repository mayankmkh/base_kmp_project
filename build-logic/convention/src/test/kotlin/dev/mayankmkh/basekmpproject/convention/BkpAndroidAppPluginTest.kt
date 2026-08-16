package dev.mayankmkh.basekmpproject.convention

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * What `bkp.android.app` hands a module that declares nothing but its own identity, and -- the half
 * that makes one convention safe to share across several app modules -- what happens when a module
 * disagrees with it.
 */
class BkpAndroidAppPluginTest {

    @field:TempDir
    lateinit var projectDir: File

    @Test
    fun `android app inherits the build type convention`() {
        val result = TestProject(projectDir).withBuildScript(androidApp()).run()

        assertContains(result.output, "DEBUG_SUFFIX=.debug")
        assertContains(result.output, "RELEASE_SUFFIX=null")
        assertContains(result.output, "RELEASE_MINIFY=true")
        assertContains(result.output, "BUILD_CONFIG=true")
        assertContains(result.output, "RUNNER=androidx.test.runner.AndroidJUnitRunner")
        // AGP extracts its default file to a version-stamped name -- `proguard-android-optimize
        // .txt-9.3.1` -- so match the prefix rather than pinning the AGP version in an assertion.
        assertContains(result.output, "PROGUARD=proguard-android-optimize.txt")
        // AGP fails the build on a ProGuard file that is listed but missing, so a module with no
        // rules of its own must not have one listed on its behalf.
        assertFalse(
            result.output.contains("proguard-rules.pro"),
            "a module with no proguard-rules.pro should not have one listed",
        )
    }

    @Test
    fun `android app with its own proguard rules gets them appended`() {
        val result = TestProject(projectDir)
            .withBuildScript(androidApp())
            .withFile("proguard-rules.pro", "# rules\n")
            .run()

        assertContains(result.output, ",proguard-rules.pro")
    }

    /**
     * Everything the convention sets is a plain property assigned while the plugin is applied, and a
     * module's own `android { }` block runs afterwards -- so an app that wants something else says so
     * and wins. That is what lets several app modules share one convention and still differ.
     *
     * `proguardFiles` is the exception, and the assertion below pins it: it appends rather than
     * assigns, so a module can add to the list but cannot take the convention's entries back out.
     */
    @Test
    fun `module settings override the convention`() {
        val result = TestProject(projectDir)
            .withBuildScript(
                androidApp(
                    """
                    buildTypes {
                        debug { applicationIdSuffix = ".dbg" }
                        release {
                            isMinifyEnabled = false
                            applicationIdSuffix = ".rel"
                            proguardFiles("extra-rules.pro")
                        }
                    }
                    buildFeatures { buildConfig = false }
                    defaultConfig { testInstrumentationRunner = "com.example.Runner" }
                    """,
                ),
            )
            .withFile("extra-rules.pro", "# rules\n")
            .run()

        assertContains(result.output, "DEBUG_SUFFIX=.dbg")
        assertContains(result.output, "RELEASE_SUFFIX=.rel")
        assertContains(result.output, "RELEASE_MINIFY=false")
        assertContains(result.output, "BUILD_CONFIG=false")
        assertContains(result.output, "RUNNER=com.example.Runner")
        assertContains(result.output, "PROGUARD=extra-rules.pro,proguard-android-optimize.txt")
    }

    /**
     * Reported from a `finalizeDsl` registered by the build script, so it runs after the one the
     * plugin registers while applying and sees the finished DSL.
     */
    private fun androidApp(androidBlock: String = "") =
        """
        plugins { id("bkp.android.app") }

        android {
            namespace = "dev.mayankmkh.basekmpproject.test"

            $androidBlock
        }

        androidComponents {
            finalizeDsl { app ->
                val debug = app.buildTypes.getByName("debug")
                val release = app.buildTypes.getByName("release")
                println("DEBUG_SUFFIX=" + debug.applicationIdSuffix)
                println("RELEASE_SUFFIX=" + release.applicationIdSuffix)
                println("RELEASE_MINIFY=" + release.isMinifyEnabled)
                println("PROGUARD=" + release.proguardFiles.map { it.name }.sorted().joinToString(","))
                println("BUILD_CONFIG=" + app.buildFeatures.buildConfig)
                println("RUNNER=" + app.defaultConfig.testInstrumentationRunner)
            }
        }
        """
}
