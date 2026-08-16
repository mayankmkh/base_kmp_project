package dev.mayankmkh.basekmpproject.convention

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

/**
 * A throwaway single-module build for exercising the convention plugins.
 *
 * The plugins come from `withPluginClasspath()` rather than a real included build, so nothing is
 * resolved from a repository to run a test. The version catalog is the project's own -- see the
 * `bkp.test.versionCatalog` system property in the build script -- so a test fails if a plugin asks
 * for a key the project does not actually have.
 */
internal class TestProject(private val dir: File) {

    fun withBuildScript(script: String): TestProject {
        val catalog = requireNotNull(System.getProperty("bkp.test.versionCatalog")) {
            "bkp.test.versionCatalog is not set; run these tests through Gradle, not the IDE's plain JUnit runner"
        }
        // `invariantSeparatorsPath`: the path is interpolated into Kotlin source, where a Windows
        // backslash would be an escape sequence.
        dir.resolve("settings.gradle.kts").writeText(
            """
            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                }
                versionCatalogs {
                    create("libs") { from(files("${File(catalog).invariantSeparatorsPath}")) }
                }
            }
            rootProject.name = "bkp-test"
            """.trimIndent(),
        )
        // TestKit's daemon otherwise runs on Gradle's 512M/384M defaults, which AGP plus KGP plus the
        // Kotlin DSL compiler exhaust -- the daemon dies of metaspace part-way through the suite.
        dir.resolve("gradle.properties").writeText("org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=1g\n")
        dir.resolve("build.gradle.kts").writeText(script.trimIndent())
        return this
    }

    /** Adds a file to the project, for conventions that key off one being there. */
    fun withFile(path: String, content: String): TestProject {
        dir.resolve(path).writeText(content)
        return this
    }

    /**
     * Runs [tasks], defaulting to `help` -- the cheapest task that still forces the whole project to
     * configure, which is where the convention plugins and the validator do their work.
     */
    fun run(vararg tasks: String): BuildResult = runner(tasks).build()

    /** Configures the project, expecting the validator to reject it. */
    fun configureAndFail(): BuildResult = runner(emptyArray()).buildAndFail()

    // The lint check exists because `withPluginClasspath()` injects only what
    // `pluginUnderTestMetadata` was told about, and a plugin whose dependencies are `compileOnly`
    // silently gets an incomplete classpath. That is the exact failure mode the
    // `testPluginClasspath` configuration in `build.gradle.kts` fixes. The alternative it suggests
    // -- publishing the plugin to a local repository per test -- buys nothing here and costs a
    // third-party plugin plus a resolve on every run.
    @Suppress("WithPluginClasspathUsage")
    private fun runner(tasks: Array<out String>): GradleRunner = GradleRunner.create()
        .withProjectDir(dir)
        .withPluginClasspath()
        .withArguments(*tasks.ifEmpty { arrayOf("help") }, "--stacktrace")
        .forwardOutput()
}
