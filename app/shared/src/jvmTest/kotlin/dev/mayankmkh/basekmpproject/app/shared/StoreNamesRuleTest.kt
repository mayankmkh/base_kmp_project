package dev.mayankmkh.basekmpproject.app.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Source rule (master source §21.4, preferences.md §6): every preferences file and secret store a
 * Capability opens has a name unique across the app and prefixed with the Capability's own name.
 *
 * The runtime registry only fails when both colliding stores are opened in one process, and Koin
 * singles are lazy, so a duplicate can pass every other gate. This test reads the names off the
 * source instead.
 */
class StoreNamesRuleTest {
    @Test
    fun `store names are unique and owned by the capability that opens them`() {
        val names =
            capabilityImplSources().flatMap { (capability, file) ->
                storeNames(file).map { Triple(capability, it, file) }
            }
        assertTrue(
            names.isNotEmpty(),
            "expected at least one store name in capability implementations",
        )

        val duplicates = names.groupBy { it.second }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet(), duplicates, "store names must be unique across the app")

        val foreign = names.filterNot { (capability, name, _) -> name.startsWith("$capability.") }
        assertEquals(
            emptyList(),
            foreign.map { (capability, name, file) ->
                "$name in ${file.name} is not prefixed with '$capability.'"
            },
        )
    }

    /** Pairs of capability name (the `-impl` module's name without the suffix) and source file. */
    private fun capabilityImplSources(): List<Pair<String, File>> {
        val root =
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "settings.gradle.kts").exists() }
        return File(root, "capability")
            .listFiles { file -> file.isDirectory && file.name.endsWith("-impl") }
            .orEmpty()
            .flatMap { module ->
                val capability = module.name.removeSuffix("-impl")
                File(module, "src")
                    .walkTopDown()
                    .filter {
                        it.isFile && it.extension == "kt" && "Main" in it.path && "Test" !in it.path
                    }
                    .map { capability to it }
                    .toList()
            }
    }

    private fun storeNames(file: File): List<String> {
        val text = file.readText()
        val preferences = PrefFileName.findAll(text).map { it.groupValues[1] }
        val secrets =
            if ("SecretStores" in text) SecretOpen.findAll(text).map { it.groupValues[1] }
            else emptySequence()
        return (preferences + secrets).toList()
    }

    private companion object {
        val PrefFileName = Regex("""PrefFile\("([^"]+)"\)""")
        val SecretOpen = Regex("""\.open\("([^"]+)"\)""")
    }
}
