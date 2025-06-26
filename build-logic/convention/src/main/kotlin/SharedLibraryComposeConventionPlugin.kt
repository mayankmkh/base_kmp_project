import dev.mayankmkh.basekmpproject.configureKMPCompose
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension

class SharedLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.compose")
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")

            val compose = extensions.getByType<ComposeExtension>().dependencies
            configureKMPCompose(compose)
        }
    }
}
