package dev.mayankmkh.basekmpproject.convention.module

import com.android.build.api.dsl.ApplicationExtension
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import dev.mayankmkh.basekmpproject.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.exclude

class BkpAndroidAppFirebasePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Validation is per-project, so a plugin that layers onto a primary has to bring the
            // validator with it -- otherwise a module applying only this plugin has no primary and
            // nothing ever checks it.
            apply(plugin = "bkp.validation.graph")

            apply(plugin = "com.google.gms.google-services")
            apply(plugin = "com.google.firebase.firebase-perf")
            apply(plugin = "com.google.firebase.crashlytics")

            dependencies {
                val bom = libs.findLibrary("firebase.bom").get()
                "implementation"(platform(bom))
                "implementation"(libs.findLibrary("firebase.analytics").get())
                "implementation"(libs.findLibrary("firebase.performance").get()) {
                    exclude(group = "com.google.protobuf", module = "protobuf-javalite")
                    exclude(group = "com.google.firebase", module = "protolite-well-known-types")
                }
                "implementation"(libs.findLibrary("firebase.crashlytics").get())
            }

            extensions.configure<ApplicationExtension> {
                buildTypes.configureEach {
                    configure<CrashlyticsExtension> {
                        mappingFileUploadEnabled = name == "release"
                    }
                }
            }
        }
    }
}
