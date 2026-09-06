package dev.mayankmkh.basekmpproject.app.shared

import java.io.File

/**
 * The checked-out repository, for the source rules that read the tree instead of the graph. Gradle
 * runs a test with the module directory as its working directory, so the walk goes up from there.
 */
internal fun repoRoot(): File =
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }
