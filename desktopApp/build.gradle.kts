import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.bkp.desktop.app)
}

kotlin {
    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(projects.shared.app)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.mayankmkh.basekmpproject.desktopapp.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "KMP-Parallax"
            packageVersion = "1.0.0"
            macOS {
                iconFile.set(project.file("icon.icns"))
            }
        }

        buildTypes.release.proguard {
            this.configurationFiles.from(file("src/desktopMain/proguard-rules.pro"))
        }
    }
}
