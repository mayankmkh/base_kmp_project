import dev.mayankmkh.basekmpproject.configureKotlinMultiplatform
import dev.mayankmkh.basekmpproject.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class SharedLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.multiplatform")
            apply(plugin = "basekmpproject.jvm.library")
            apply(plugin = "basekmpproject.style.enforcer")

            extensions.configure<KotlinMultiplatformExtension> {
                configureKotlinMultiplatform(this)
            }
            dependencies {
                "commonTestImplementation"(libs.findLibrary("kotlin.test").get())
            }
        }
    }
}
