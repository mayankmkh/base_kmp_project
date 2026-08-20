import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.bkp.desktop.app)
}

dependencies {
    implementation(projects.shared.app)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
}

compose.desktop {
    application {
        mainClass = "dev.mayankmkh.basekmpproject.desktopapp.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.mayankmkh.base_kmp_project"
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
