package dev.mayankmkh.basekmpproject.app.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Source rule: every `DEVELOPMENT_TEAM` in the Xcode project is `"${TEAM_ID}"`, the indirection
 * `app/ios/Configuration/Config.xcconfig` fills in.
 *
 * When a developer signs locally through Xcode's Signing & Capabilities pane, Xcode writes their
 * literal account id over that indirection in `project.pbxproj`. The build keeps working for them
 * and nothing else notices, so the id is one `git add -A` from being committed -- which leaks a
 * personal account into the repository and breaks signing for everyone on a different team. The
 * team belongs in the xcconfig (or a local override of it), never in the project file.
 */
class XcodeDevelopmentTeamRuleTest {
    @Test
    fun `the Xcode project names its team only through TEAM_ID`() {
        val values =
            File(repoRoot(), ProjectPath).readLines().mapNotNull {
                DevelopmentTeam.find(it)?.groupValues?.get(1)?.trim()
            }

        assertTrue(values.isNotEmpty(), "no DEVELOPMENT_TEAM setting in $ProjectPath")
        assertEquals(
            emptyList(),
            values.filterNot { it == ExpectedValue },
            "$ProjectPath sets DEVELOPMENT_TEAM to a literal value; Xcode rewrites it when you " +
                "sign locally. Restore $ExpectedValue and put your team in TEAM_ID instead",
        )
    }

    private companion object {
        const val ProjectPath = "app/ios/iosApp.xcodeproj/project.pbxproj"
        const val ExpectedValue = "\"\${TEAM_ID}\""
        val DevelopmentTeam = Regex("""\bDEVELOPMENT_TEAM = ([^;]*);""")
    }
}
