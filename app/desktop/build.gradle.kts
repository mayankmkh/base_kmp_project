import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.bkp.desktop.app)
}

dependencies {
    implementation(projects.app.shared)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
}

// Desktop has neither product flavors nor build configurations, so the environment is a Gradle
// property that both the `run` task and every packaged launcher carry as a JVM system property:
//
//     ./gradlew :app:desktop:run -Pbkp.environment=staging
//     ./gradlew :app:desktop:packageDistributionForCurrentOS -Pbkp.environment=staging
//
// Saying nothing builds production, matching what the entry point assumes when the property is
// absent entirely. An unknown value fails at startup rather than here, in the one place that owns
// the id -> environment mapping.
val environmentProperty = "bkp.environment"
val appEnvironment = providers.gradleProperty(environmentProperty).getOrElse("prod")
val isStaging = appEnvironment == "staging"

// Rejected here rather than at startup: the id -> environment mapping is owned by `:app:shared`,
// but a typo that only fails when someone launches the installer has already been packaged,
// signed and handed out. The names are the `BuildEnvironment.id` values.
require(appEnvironment in setOf("staging", "prod")) {
    "-P$environmentProperty must be 'staging' or 'prod', was '$appEnvironment'"
}

compose.desktop {
    application {
        mainClass = "dev.mayankmkh.basekmpproject.desktopapp.MainKt"

        jvmArgs += "-D$environmentProperty=$appEnvironment"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Distinct package names, so a staging install does not replace a production one and
            // the two keep separate application directories.
            packageName =
                if (isStaging) {
                    "dev.mayankmkh.base_kmp_project.staging"
                } else {
                    "dev.mayankmkh.base_kmp_project"
                }
            packageVersion = "1.0.0"

            // Reported by `suggestRuntimeModules`. DataStore's bundled protobuf needs
            // sun.misc.Unsafe, which jlink leaves out unless jdk.unsupported is asked for.
            modules("java.instrument", "jdk.unsupported")
        }

        buildTypes.release.proguard {
            this.configurationFiles.from(file("src/main/proguard-rules.pro"))
        }
    }
}
