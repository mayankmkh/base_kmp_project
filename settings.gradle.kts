rootProject.name = "base_kmp_project"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// One vararg call rather than ten statements: ktfmt inserts a blank line between consecutive
// top-level calls, which turns the module list into a page of scrolling.
include(
    ":app:android",
    ":app:desktop",
    ":app:shared",
    ":app:web",
    ":capability:identity-api",
    ":capability:identity-impl",
    ":capability:posts-api",
    ":capability:posts-impl",
    ":feature:posts",
    ":foundation:network",
    ":foundation:preferences",
    ":foundation:presentation",
    ":foundation:resource",
    ":foundation:resource-runtime",
    ":foundation:runtime",
    ":foundation:sqldelight",
    ":platform:connectivity",
    ":platform:secure-storage",
    ":storage:database",
    ":testkit:common",
    ":ui:design-system",
)
