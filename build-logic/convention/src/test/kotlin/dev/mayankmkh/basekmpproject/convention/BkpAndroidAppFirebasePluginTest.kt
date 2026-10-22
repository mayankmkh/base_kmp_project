package dev.mayankmkh.basekmpproject.convention

import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The resolved Android runtime classpath that `bkp.android.app.firebase` produces next to the
 * repo's other protobuf-carrying Android dependencies.
 *
 * The plugin once excluded `protobuf-javalite` and `protolite-well-known-types` from firebase-perf,
 * a rule copied from Now in Android, where datastore-proto brought in a second protobuf runtime.
 * This repo never had that conflict -- Tink and DataStore ship relocated protobuf packages -- and
 * firebase-perf's generated messages extend `com.google.protobuf.GeneratedMessageLite`, so the
 * exclusion would have crashed the first metric with `NoClassDefFoundError`. These tests pin the
 * graph Firebase declares: the javalite runtime present via firebase-perf, nothing unshaded beside
 * it, and no duplicate classes.
 *
 * Unlike the configuration-only tests beside it, this fixture resolves real artifacts from
 * `google()` and `mavenCentral()`, so it needs network access or a warm `~/.gradle` cache.
 */
class BkpAndroidAppFirebasePluginTest {

    @field:TempDir lateinit var projectDir: File

    @Test
    fun `firebase perf brings the protobuf javalite runtime it declares`() {
        // The same `group:name:` form as the absence test, so this also proves that form matches.
        val result = project().run(*insight("com.google.protobuf:protobuf-javalite:"))

        // Group and module only: the versions are Firebase's to bump through the BOM.
        assertContains(result.output, Regex("""(?m)^com\.google\.protobuf:protobuf-javalite:\S"""))
        assertContains(result.output, Regex("""--- com\.google\.firebase:firebase-perf\b"""))
        val count =
            result.output.lines().single { it.startsWith(COUNT_PREFIX) }.removePrefix(COUNT_PREFIX)
        assertEquals("1", count)
    }

    @Test
    fun `no full protobuf runtime or protolite types reach the app`() {
        // dependencyInsight matches its argument as a substring of `group:name:version`, so
        // `protobuf-java` alone would find protobuf-javalite; the trailing colon ends the name.
        listOf(
                "com.google.protobuf:protobuf-java:",
                "com.google.firebase:protolite-well-known-types:",
            )
            .forEach { module ->
                val result = project().run(*insight(module))

                assertContains(result.output, NO_MATCH, message = "$module should not resolve")
            }
    }

    @Test
    fun `runtime classpath has no duplicate classes`() {
        project().run("checkProdDebugDuplicateClasses")
    }

    private fun insight(module: String) =
        arrayOf(
            "dependencyInsight",
            "--configuration",
            CLASSPATH,
            "--dependency",
            module,
            "countGeneratedMessageLite",
        )

    /**
     * `countGeneratedMessageLite` walks the classes jars AGP hands the dexer, so a second unshaded
     * protobuf runtime shows up as a count above one even when its coordinates differ. `ZipFile` is
     * imported because a bare `java.` inside a build script resolves to the `java` extension.
     */
    private fun project() =
        TestProject(projectDir)
            .withBuildScript(
                """
                import java.util.zip.ZipFile

                plugins {
                    id("bkp.android.app")
                    id("bkp.android.app.firebase")
                }

                bkpModule { features { environmentFlavors() } }

                android {
                    namespace = "dev.mayankmkh.basekmpproject.test"
                    defaultConfig { applicationId = "dev.mayankmkh.basekmpproject.test" }
                }

                dependencies {
                    implementation(libs.androidx.datastore.core)
                    implementation(libs.androidx.datastore.tink)
                    implementation(libs.tink.android)
                }

                tasks.register("countGeneratedMessageLite") {
                    val jars =
                        configurations["$CLASSPATH"].incoming
                            .artifactView {
                                attributes {
                                    attribute(
                                        Attribute.of("artifactType", String::class.java),
                                        "android-classes-jar",
                                    )
                                }
                            }
                            .files
                    doLast {
                        val count =
                            jars.sumOf { jar ->
                                ZipFile(jar).use { zip ->
                                    zip.entries().asSequence().count {
                                        it.name == "com/google/protobuf/GeneratedMessageLite.class"
                                    }
                                }
                            }
                        println("$COUNT_PREFIX" + count)
                    }
                }
                """
            )

    private companion object {
        const val CLASSPATH = "prodDebugRuntimeClasspath"
        const val NO_MATCH = "No dependencies matching given input were found"
        const val COUNT_PREFIX = "GENERATED_MESSAGE_LITE_COUNT="
    }
}
