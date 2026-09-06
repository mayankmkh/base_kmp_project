package dev.mayankmkh.basekmpproject.app.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Source rule (master source §21.4, §18.7): the module list `startKoin` loads is literal module
 * names, one per line, and nothing else.
 *
 * The Koin compiler plugin only validates typed definitions across the whole graph while it can
 * read that list. A call, a spread, a variable or an inline `module { }` there makes the list
 * dynamic: the plugin reports `KOIN-W003` and falls back to whatever it happens to discover, so the
 * compile-time check quietly stops covering the graph while every gate still passes.
 */
class KoinApplicationModulesRuleTest {
    @Test
    fun `startKoin loads a literal list of module names`() {
        val lines = File(repoRoot(), KoinAppPath).readLines()

        val entryPoint = lines.indexOfFirst { it.contains("startKoin {") }
        assertTrue(entryPoint >= 0, "no startKoin block in $KoinAppPath")
        val opening = lines.drop(entryPoint).indexOfFirst { it.trim() == "modules(" }
        assertTrue(
            opening >= 0,
            "$KoinAppPath does not open a multiline modules( call in startKoin",
        )
        val entries =
            lines
                .drop(entryPoint + opening + 1)
                .takeWhile { it.trim() != ")" }
                .map { it.trim() }
                .filter { it.isNotEmpty() }

        assertTrue(entries.isNotEmpty(), "startKoin loads no modules in $KoinAppPath")
        assertEquals(
            emptyList(),
            entries.filterNot { ModuleName.matches(it) },
            "a call, a spread, a variable or an inline module { } in startKoin's modules(...) " +
                "reports KOIN-W003 and silently turns the compile-time graph check off",
        )
    }

    private companion object {
        const val KoinAppPath =
            "app/shared/src/commonMain/kotlin/dev/mayankmkh/basekmpproject/app/shared/di/KoinApp.kt"
        val ModuleName = Regex("""^\w+,?$""")
    }
}
