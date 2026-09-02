package dev.mayankmkh.basekmpproject.convention

import java.io.File
import kotlin.test.assertContains
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * One test per `throw GradleException` in `BkpValidationGraphPlugin`, plus the positive cases those
 * rules are guarding. These ran as throwaway edits to a real module before this existed: append the
 * offending block, run a build, read the message, revert.
 *
 * Two of the validator's rules have no test because no build script can reach them:
 *
 * - "expected exactly one bkp primary plugin group" -- every pair of groups collides while the
 *   plugins are still being applied, so the validator never runs. Two Android plugins are rejected
 *   by AGP outright; KMP plus an Android plugin is rejected by AGP as of 9.0; and `bkp.desktop.app`
 *   against either of the others fails on `Cannot add extension with name 'kotlin'`, since
 *   `kotlin("jvm")`, KGP and AGP's built-in Kotlin all want that name.
 * - "bkpModule extension is present but no bkp primary plugin is applied" -- the extension is only
 *   ever created by a primary plugin, so its presence implies one.
 *
 * Both are guards against a future plugin that breaks those assumptions, not against anything a
 * module can write today.
 *
 * One more rule is untestable for reasons of its own:
 *
 * - `BkpTargets.exception`'s own argument guards -- a blank reason, an `exception` that trails the
 *   selectors, a `default()` nested inside one -- fail on the shape of the call rather than on
 *   anything about the module, so a test would only be restating the `if`.
 */
class BkpValidationGraphPluginTest {

    @field:TempDir lateinit var projectDir: File

    @Test
    fun `kmp module declaring no targets is rejected`() {
        val result =
            TestProject(projectDir)
                .withBuildScript(
                    """
            plugins { id("bkp.kmp.lib") }
            """
                )
                .configureAndFail()

        assertContains(result.output, "no targets declared")
    }

    @Test
    fun `target created outside bkpTargets is rejected`() {
        val result =
            TestProject(projectDir)
                .withBuildScript(
                    """
            plugins { id("bkp.kmp.lib") }

            kotlin {
                bkpTargets { default() }
                macosArm64()
            }
            """
                )
                .configureAndFail()

        assertContains(result.output, "macosArm64 created outside")
    }

    @Test
    fun `kmp module declaring targets configures`() {
        TestProject(projectDir)
            .withBuildScript(
                """
            plugins { id("bkp.kmp.lib") }

            kotlin {
                bkpTargets { default() }
            }
            """
            )
            .run()
    }

    @Test
    fun `demoProdFlavors outside an android app is rejected`() {
        val result =
            TestProject(projectDir)
                .withBuildScript(
                    """
            plugins { id("bkp.kmp.lib") }

            kotlin {
                bkpTargets { default() }
            }

            bkpModule {
                features { demoProdFlavors() }
            }
            """
                )
                .configureAndFail()

        assertContains(
            result.output,
            "demoProdFlavors() is only supported for bkp.android.app* plugins",
        )
    }

    /**
     * Raw AGP rather than `bkp.android.app`, because that is the only shape in which the rule is
     * reachable. Applying the firebase plugin entirely alone dies earlier and less helpfully -- it
     * adds `implementation` dependencies, so with no primary at all it fails at apply time with
     * "Configuration with name 'implementation' not found", long before the validator's
     * `afterEvaluate` runs. Moving the check to apply time would not fix that: plugin order inside
     * `plugins { }` is the module's to choose, so a firebase-first listing would false-positive.
     */
    @Test
    fun `firebase plugin without an android app primary is rejected`() {
        val result =
            TestProject(projectDir)
                .withBuildScript(
                    """
            plugins {
                id("com.android.application")
                id("bkp.android.app.firebase")
            }

            android {
                namespace = "dev.mayankmkh.basekmpproject.test"
                compileSdk = libs.versions.android.compileSdk.get().toInt()
            }
            """
                )
                .configureAndFail()

        assertContains(
            result.output,
            "bkp.android.app.firebase requires a bkp.android.app* primary plugin",
        )
    }

    @Test
    fun `partial target set requires a documented exception`() {
        val result =
            TestProject(projectDir)
                .withBuildScript(
                    """
            plugins { id("bkp.kmp.lib") }

            kotlin { bkpTargets { jvm() } }
            """
                )
                .configureAndFail()

        assertContains(result.output, "a non-default bkp target set requires")
    }

    @Test
    fun `exception covering every platform is rejected`() {
        val result =
            TestProject(projectDir)
                .withBuildScript(
                    """
            plugins { id("bkp.kmp.lib") }

            kotlin {
                bkpTargets {
                    exception("Not actually an exception") {
                        android()
                        ios()
                        jvm()
                        web()
                    }
                }
            }
            """
                )
                .configureAndFail()

        assertContains(result.output, "excludes nothing")
        assertContains(result.output, "Use bkpTargets.default()")
    }

    /**
     * `bkp.kmp.lib` with the raw Compose plugins bolted on. The combination compiles, so nothing
     * else in the build would object -- the module would simply be missing whatever a
     * Compose-owning Helix role plugin brings beyond the two plugins.
     */
    @Test
    fun `raw Compose plugins require the Compose convention`() {
        val result =
            TestProject(projectDir)
                .withBuildScript(
                    """
            plugins {
                id("bkp.kmp.lib")
                id("org.jetbrains.compose")
                id("org.jetbrains.kotlin.plugin.compose")
            }
            """
                )
                .configureAndFail()

        assertContains(result.output, "Use a Compose-owning Helix role plugin")
    }

    @Test
    fun `raw Compose plugins require the Compose Android app convention`() {
        val result =
            TestProject(projectDir)
                .withBuildScript(
                    """
            plugins {
                id("bkp.android.app")
                id("org.jetbrains.compose")
                id("org.jetbrains.kotlin.plugin.compose")
            }

            android { namespace = "dev.mayankmkh.basekmpproject.test" }
            """
                )
                .configureAndFail()

        assertContains(result.output, "Use bkp.android.app.compose")
    }
}
