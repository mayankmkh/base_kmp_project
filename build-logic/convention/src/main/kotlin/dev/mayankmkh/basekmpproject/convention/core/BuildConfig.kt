package dev.mayankmkh.basekmpproject.convention.core

import dev.mayankmkh.basekmpproject.libs
import org.gradle.api.Project

internal data class AndroidSdkConfig(
    val compileSdk: Int,
    val minSdk: Int,
    val targetSdk: Int,
)

internal fun Project.androidSdkConfig(): AndroidSdkConfig = AndroidSdkConfig(
    compileSdk = versionAsInt("android-compileSdk"),
    minSdk = versionAsInt("android-minSdk"),
    targetSdk = versionAsInt("android-targetSdk"),
)

internal fun Project.versionAsInt(versionAlias: String): Int =
    libs.findVersion(versionAlias).get().requiredVersion.toInt()
