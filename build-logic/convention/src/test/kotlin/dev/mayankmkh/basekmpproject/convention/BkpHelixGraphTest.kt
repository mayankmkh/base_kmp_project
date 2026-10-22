package dev.mayankmkh.basekmpproject.convention

import dev.mayankmkh.basekmpproject.convention.validation.HELIX_TODAY_PROPERTY
import java.io.File
import java.time.LocalDate
import kotlin.test.assertContains
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class BkpHelixGraphTest {
    @field:TempDir lateinit var projectDir: File

    @Test
    fun `wrongly placed role fires path mismatch`() {
        val result =
            helixProject()
                .withModule(":storage:oops", roleModule("bkp.kmp.feature"))
                .runAndFail("checkModuleGraph")

        assertContains(result.output, "[MOD-PATH-ROLE-MISMATCH] :storage:oops")
    }

    @Test
    fun `feature to capability impl is denied`() {
        val result =
            helixProject()
                .withModule(
                    ":feature:consumer",
                    roleModule(
                        "bkp.kmp.feature",
                        "implementation(project(\":capability:secret-impl\"))",
                    ),
                )
                .withModule(
                    ":capability:secret-impl",
                    roleModule("bkp.kmp.capability.impl"),
                )
                .runAndFail("checkModuleGraph")

        assertContains(
            result.output,
            "[DEP-ROLE-DENIED] :feature:consumer -> :capability:secret-impl",
        )
    }

    @Test
    fun `feature to capability api is allowed`() {
        val result =
            helixProject()
                .withModule(
                    ":feature:consumer",
                    roleModule(
                        "bkp.kmp.feature",
                        "implementation(project(\":capability:public-api\"))",
                    ),
                )
                .withModule(
                    ":capability:public-api",
                    roleModule("bkp.kmp.capability.api"),
                )
                .run("checkModuleGraph")

        assertFalse(result.output.contains("DEP-ROLE-DENIED"))
    }

    @Test
    fun `schema two report includes graph context metadata`() {
        helixProject()
            .withModule(
                ":feature:consumer",
                roleModule(
                    "bkp.kmp.feature",
                    "api(project(\":capability:public-api\"))",
                ),
                mapOf(
                    "src/commonMain/kotlin/example/api/Consumer.kt" to
                        "package example.api\n\npublic interface Consumer\n"
                ),
            )
            .withModule(
                ":capability:public-api",
                roleModule("bkp.kmp.capability.api"),
                mapOf(
                    "src/commonMain/kotlin/example/PublicApi.kt" to
                        "package example\n\npublic interface PublicApi\n"
                ),
            )
            .run("checkModuleGraph")

        val report = projectDir.resolve("build/reports/helix/module-graph.json").readText()
        assertContains(report, "\"schema\": 2")
        assertContains(report, "\"projectDir\": \"feature/consumer\"")
        assertContains(report, "\"targets\"")
        assertContains(report, "\"publicApiDirs\"")
        assertContains(report, "\"configuration\": \"api\"")
    }

    @Test
    fun `active exception downgrades denied edge to warning`() {
        val result =
            deniedFeatureProject(
                    exceptions(
                        expires = "2099-01-01",
                        rule = "DEP-ROLE-DENIED",
                        scope = ":feature:consumer -> :capability:secret-impl",
                    )
                )
                .run("checkModuleGraph")

        assertContains(result.output, "temporary exception")
        assertContains(result.output, "[DEP-ROLE-DENIED]")
    }

    @Test
    fun `expired exception fails with stable rule id`() {
        val result =
            deniedFeatureProject(
                    exceptions(
                        expires = "2000-01-01",
                        rule = "DEP-ROLE-DENIED",
                        scope = ":feature:consumer -> :capability:secret-impl",
                    )
                )
                .runAndFail("checkModuleGraph")

        assertContains(result.output, "[EXC-EXPIRED]")
    }

    @Test
    fun `api impl family collapse detects logical cycle`() {
        val result =
            helixProject()
                .withModule(
                    ":platform:a-api",
                    roleModule("bkp.kmp.platform.api"),
                )
                .withModule(
                    ":platform:a-impl",
                    roleModule(
                        "bkp.kmp.platform.impl",
                        "implementation(project(\":platform:b-api\"))",
                    ),
                )
                .withModule(
                    ":platform:b-api",
                    roleModule("bkp.kmp.platform.api"),
                )
                .withModule(
                    ":platform:b-impl",
                    roleModule(
                        "bkp.kmp.platform.impl",
                        "implementation(project(\":platform:a-api\"))",
                    ),
                )
                .runAndFail("checkModuleGraph")

        assertContains(result.output, "[GRAPH-CYCLE-LOGICAL]")
        assertFalse(result.output.contains("[GRAPH-CYCLE-PHYSICAL]"))
    }

    @Test
    fun `an app shell may depend on the shared app root`() {
        val result =
            helixProject()
                .withModule(":app:shared", roleModule("bkp.kmp.app"))
                .withModule(
                    ":app:host",
                    roleModule("bkp.kmp.app", "implementation(project(\":app:shared\"))"),
                )
                .run("checkModuleGraph")

        assertFalse(result.output.contains("DEP-ROLE-DENIED"))
        assertFalse(result.output.contains("MOD-PATH-ROLE-MISMATCH"))
    }

    @Test
    fun `the adoption-era shared path is no longer grandfathered`() {
        val result =
            helixProject()
                .withModule(":shared:app", roleModule("bkp.kmp.app"))
                .runAndFail("checkModuleGraph")

        assertContains(result.output, "[MOD-PATH-ROLE-MISMATCH] :shared:app")
    }

    @Test
    fun `a module with no role plugin is reported wherever it sits`() {
        val result =
            helixProject()
                .withModule(
                    ":shared:libs",
                    """
                    plugins { id("bkp.kmp.lib") }
                    kotlin { bkpTargets { exception("graph tests stay JVM-only") { jvm() } } }
                    """,
                )
                .runAndFail("checkModuleGraph")

        assertContains(result.output, "[MOD-ROLE-MISSING] :shared:libs")
    }

    @Test
    fun `a public declaration added after a cached run is still caught`() {
        val project =
            helixProject()
                .withModule(
                    ":feature:late",
                    roleModule("bkp.kmp.feature"),
                    mapOf(
                        "$FEATURE_SOURCE_ROOT/Existing.kt" to
                            "package dev.mayankmkh.basekmpproject.feature.late\n\n" +
                                "internal val existing: Int = 1\n"
                    ),
                )

        val first = project.run("checkModuleGraph", "--configuration-cache")
        assertFalse(first.output.contains("FEATURE-PUBLIC-SURFACE-OUTSIDE-API"))

        // No `--rerun-tasks`, and nothing that would reconfigure the build: the file is new, and
        // only a source *directory* declared as a task input can notice it on a cache hit.
        project.withFile(
            "feature/late/$FEATURE_SOURCE_ROOT/Leaked.kt",
            "package dev.mayankmkh.basekmpproject.feature.late\n\nfun leaked(): Int = 2\n",
        )

        val second = project.runExpectingFailure("checkModuleGraph", "--configuration-cache")
        assertContains(second.output, "Reusing configuration cache")
        assertContains(second.output, "[FEATURE-PUBLIC-SURFACE-OUTSIDE-API] :feature:late")
    }

    @Test
    fun `annotated and indented top-level declarations are public surface but nested ones are not`() {
        val header = "package dev.mayankmkh.basekmpproject.feature.late\n\n"
        val result =
            helixProject()
                .withModule(
                    ":feature:late",
                    roleModule("bkp.kmp.feature"),
                    mapOf(
                        "$FEATURE_SOURCE_ROOT/Annotated.kt" to
                            header + "@JvmInline public value class Leaked(val id: Long)\n",
                        "$FEATURE_SOURCE_ROOT/Indented.kt" to
                            header + "    @Suppress(\"Unused\") fun indented(): Int = 1\n",
                        "$FEATURE_SOURCE_ROOT/Internal.kt" to
                            header +
                                """
                                @Immutable internal data class Fine(val id: Long)

                                internal class Holder {
                                    fun nested(): Int = 1

                                    @Suppress("Unused") public val alsoNested: Int = 2
                                }

                                /* a comment with an unbalanced brace {
                                fun commented() = 1
                                */
                                private val brace: Char = '{'
                                private val text: String = "${'$'}{brace} {"

                                private val listed: List<Any> =
                                    listOf(
                                        object : Any() {},
                                    )

                                private val anonymous: Any =
                                    object : Any() {}
                                """
                                    .trimIndent() +
                                "\n",
                    ),
                )
                .runAndFail("checkModuleGraph")

        assertContains(result.output, "Annotated.kt:3 declares public feature surface")
        assertContains(result.output, "Indented.kt:3 declares public feature surface")
        assertFalse(result.output.contains("Internal.kt:"), result.output)
    }

    @Test
    fun `a declaration's visibility is read across lines and through comments and semicolons`() {
        val header = "package dev.mayankmkh.basekmpproject.feature.late\n\n"
        val result =
            helixProject()
                .withModule(
                    ":feature:late",
                    roleModule("bkp.kmp.feature"),
                    mapOf(
                        "$FEATURE_SOURCE_ROOT/MultilineAnnotation.kt" to
                            header + "@Suppress(\n    \"unused\"\n) public fun leaked() = 1\n",
                        "$FEATURE_SOURCE_ROOT/BlockComment.kt" to
                            header + "/** product API */ public fun commented() = 1\n",
                        "$FEATURE_SOURCE_ROOT/PublicAfterSemicolon.kt" to
                            header + "private val x = 1; public fun afterSemicolon() = 1\n",
                        "$FEATURE_SOURCE_ROOT/ImplicitAfterSemicolon.kt" to
                            header + "internal val y = 1; fun implicit() = 2\n",
                        "$FEATURE_SOURCE_ROOT/SplitPublic.kt" to
                            header + "public\nfun split() = 1\n",
                        "$FEATURE_SOURCE_ROOT/DataObject.kt" to
                            header + "@Serializable\ndata object Route\n",
                        "$FEATURE_SOURCE_ROOT/FunInterface.kt" to
                            header + "fun interface Callback {\n    fun call()\n}\n",
                        "$FEATURE_SOURCE_ROOT/Quiet.kt" to
                            header +
                                """
                                private
                                fun hidden() = 1

                                @Suppress(
                                    "unused"
                                ) private fun quiet() = 1

                                /* public fun inComment() = 1 */ internal fun commented() = 1

                                internal val first = 1; private fun second() = 2

                                @Suppress("unused")
                                internal
                                /* between the modifier and the keyword */
                                fun spread() = 1

                                internal val type = Any::class
                                private val afterClassLiteral = 1

                                private val anonymous = fun String.(): Int = length
                                """
                                    .trimIndent() +
                                "\n",
                    ),
                )
                .runAndFail("checkModuleGraph")

        assertContains(result.output, "MultilineAnnotation.kt:5 declares public feature surface")
        assertContains(result.output, "BlockComment.kt:3 declares public feature surface")
        assertContains(result.output, "PublicAfterSemicolon.kt:3 declares public feature surface")
        assertContains(result.output, "ImplicitAfterSemicolon.kt:3 declares public feature surface")
        assertContains(result.output, "SplitPublic.kt:3 declares public feature surface")
        assertContains(result.output, "FunInterface.kt:3 declares public feature surface")
        assertContains(result.output, "DataObject.kt:4 declares public feature surface")
        assertFalse(result.output.contains("Quiet.kt:"), result.output)
    }

    @Test
    fun `a receiver in parentheses or with type annotations is public surface but an anonymous function is not`() {
        val header = "package dev.mayankmkh.basekmpproject.feature.late\n\n"
        val result =
            helixProject()
                .withModule(
                    ":feature:late",
                    roleModule("bkp.kmp.feature"),
                    mapOf(
                        "$FEATURE_SOURCE_ROOT/ParenthesizedReceiver.kt" to
                            header + "public fun (String).escaped(): Int = length\n",
                        "$FEATURE_SOURCE_ROOT/ParenthesizedPropertyReceiver.kt" to
                            header + "public val (String).escapedProp: Int get() = length\n",
                        "$FEATURE_SOURCE_ROOT/NullableFunctionReceiver.kt" to
                            header + "fun ((Int) -> Unit)?.invokeZero() = this?.invoke(0)\n",
                        "$FEATURE_SOURCE_ROOT/GenericReceiver.kt" to
                            header + "val <A> (List<A>).second: A get() = this[1]\n",
                        "$FEATURE_SOURCE_ROOT/AnnotatedReceiver.kt" to
                            header + "fun @Tagged(\"x\") String?.annotated() = 0\n",
                        "$FEATURE_SOURCE_ROOT/Quiet.kt" to
                            header +
                                """
                                private @Suppress
                                ("unused") fun quiet() = 1

                                internal @Suppress /* why */ ("unused")
                                val commented = 1

                                internal fun (String).hidden(): Int = length
                                private val (String).hiddenProp: Int get() = length

                                private val condition = true
                                private val anonymous = if (condition) fun (x: Int) = x else null
                                private val withReceiver = if (condition) fun String.() = 1 else null
                                private val inParentheses = if (condition) fun (String).() = 1 else null
                                """
                                    .trimIndent() +
                                "\n",
                    ),
                )
                .runAndFail("checkModuleGraph")

        assertContains(result.output, "ParenthesizedReceiver.kt:3 declares public feature surface")
        assertContains(
            result.output,
            "ParenthesizedPropertyReceiver.kt:3 declares public feature surface",
        )
        assertContains(
            result.output,
            "NullableFunctionReceiver.kt:3 declares public feature surface",
        )
        assertContains(result.output, "GenericReceiver.kt:3 declares public feature surface")
        assertContains(result.output, "AnnotatedReceiver.kt:3 declares public feature surface")
        assertFalse(result.output.contains("Quiet.kt:"), result.output)
    }

    /** A value in an initializer that is named like a modifier is no part of what follows it. */
    @Test
    fun `a value named private or internal ends its initializer and not the next declaration`() {
        val header = "package dev.mayankmkh.basekmpproject.feature.late\n\n"
        val result =
            helixProject()
                .withModule(
                    ":feature:late",
                    roleModule("bkp.kmp.feature"),
                    mapOf(
                        "$FEATURE_SOURCE_ROOT/Bleed.kt" to
                            header +
                                """
                                private val internal = lazy { 1 }
                                private val cached by internal
                                fun leakedAfterDelegate() = 1

                                private val private = 1
                                private val pair = 1 to private
                                public fun leakedAfterInfix() = 1

                                private val split = 1 to
                                    private
                                fun leakedAfterSplitInfix() = 1

                                private val splitDelegate by
                                    internal
                                @Suppress("unused") fun leakedAfterSplitDelegate() = 1

                                private val choice =
                                    if (split.first == 1) {
                                        private
                                    }
                                    else private
                                private fun stillPrivate() = choice

                                private val product = 2 * 3
                                fun afterMultiplication() = product
                                private val typed = listOf(product) as List<*>
                                fun afterTypeArguments() = typed
                                """
                                    .trimIndent() +
                                "\n"
                    ),
                )
                .runAndFail("checkModuleGraph")

        for (line in listOf(5, 9, 13, 17, 27, 29)) {
            assertContains(result.output, "Bleed.kt:$line declares public feature surface")
        }
        assertFalse(result.output.contains("Bleed.kt:24"), result.output)
    }

    /**
     * A binary operator ending a line waits for its operand on the next, and a type is read whole,
     * so neither `*`, `<` nor `>` leaves an initializer's operand to pass for a modifier.
     */
    @Test
    fun `an operator at the end of a line keeps its operand in the initializer`() {
        val header = "package dev.mayankmkh.basekmpproject.feature.late\n\n"
        val result =
            helixProject()
                .withModule(
                    ":feature:late",
                    roleModule("bkp.kmp.feature"),
                    mapOf(
                        "$FEATURE_SOURCE_ROOT/Split.kt" to
                            header +
                                """
                                private val internal = 2
                                private val product = 2 *
                                    internal
                                fun leakedAfterProduct() = 1

                                private val comparison = 2 <
                                    internal
                                public fun leakedAfterComparison() = 1

                                private val greater = 2 >
                                    internal
                                fun leakedAfterGreater() = 1

                                private val cast = mapOf(1 to 2) as Map<Int, Int>?
                                fun afterNullableCast() = cast

                                private val check = cast is Map<*, *>
                                fun afterCheck() = check

                                private val explicit = listOf<Int>()
                                fun afterTypeArguments() = explicit

                                private val ordering = object : Comparator<Int> {
                                    override fun compare(a: Int, b: Int) = a - b
                                }
                                fun afterObject() = ordering

                                private val callback = { _: Int -> } as (Int) -> Unit
                                fun afterFunctionType() = callback
                                """
                                    .trimIndent() +
                                "\n"
                    ),
                )
                .runAndFail("checkModuleGraph")

        for (line in listOf(6, 10, 14, 17, 20, 23, 28, 31)) {
            assertContains(result.output, "Split.kt:$line declares public feature surface")
        }
    }

    /** A typealias's right-hand side is a type: its `<` and `>` wait for nothing after them. */
    @Test
    fun `a typealias ends at its type and not in the next declaration`() {
        val header = "package dev.mayankmkh.basekmpproject.feature.late\n\n"
        val result =
            helixProject()
                .withModule(
                    ":feature:late",
                    roleModule("bkp.kmp.feature"),
                    mapOf(
                        "$FEATURE_SOURCE_ROOT/Alias.kt" to
                            header +
                                """
                                private typealias Pairing = Pair<Int, Int>
                                public fun leakedAfterAlias() = 1

                                private typealias MaybePairing = Pair<Int, Int>?
                                fun leakedAfterNullableAlias(): MaybePairing = null

                                private typealias Keyed<T> = Map<String, T>
                                private fun stillPrivate(): Keyed<Int> = emptyMap()

                                internal typealias Callback = (Pairing) -> Unit
                                fun leakedAfterFunctionAlias(): Callback = {}

                                private val stillSplit = 2 >
                                    1
                                fun leakedAfterSplit() = stillSplit
                                """
                                    .trimIndent() +
                                "\n"
                    ),
                )
                .runAndFail("checkModuleGraph")

        for (line in listOf(4, 7, 13, 17)) {
            assertContains(result.output, "Alias.kt:$line declares public feature surface")
        }
        assertFalse(result.output.contains("Alias.kt:10"), result.output)
    }

    /** `by` delegates only in a property or a class; an alias may be named, or take, `by`. */
    @Test
    fun `an alias named by or like a modifier still ends at its type`() {
        val header = "package dev.mayankmkh.basekmpproject.feature.late\n\n"
        val result =
            helixProject()
                .withModule(
                    ":feature:late",
                    roleModule("bkp.kmp.feature"),
                    mapOf(
                        "$FEATURE_SOURCE_ROOT/SoftAlias.kt" to
                            header +
                                """
                                private typealias by = List<Int>
                                public fun leakedAfterNamedByAlias(): by = emptyList()

                                private typealias Wrapper<by> = List<by>
                                public fun leakedAfterByParameterAlias(): Wrapper<Int> = emptyList()

                                private typealias internal = Map<String, Int>
                                fun leakedAfterModifierNamedAlias(): internal = emptyMap()

                                private val delegated by lazy { listOf(1) }
                                fun leakedAfterDelegate() = delegated

                                private interface Greeter { fun greet(): Int }
                                private object Base : Greeter { override fun greet() = 1 }
                                private class Delegating : Greeter by Base
                                fun leakedAfterClassDelegation() = Delegating().greet()
                                """
                                    .trimIndent() +
                                "\n"
                    ),
                )
                .runAndFail("checkModuleGraph")

        for (line in listOf(4, 7, 10, 13, 18)) {
            assertContains(result.output, "SoftAlias.kt:$line declares public feature surface")
        }
    }

    @Test
    fun `an exception expires on a later day even when the configuration cache is reused`() {
        val expires = LocalDate.now().plusYears(1)
        val project =
            deniedFeatureProject(
                exceptions(
                    expires = expires.toString(),
                    rule = "DEP-ROLE-DENIED",
                    scope = ":feature:consumer -> :capability:secret-impl",
                )
            )

        val first = project.run("checkModuleGraph", "--configuration-cache")
        assertContains(first.output, "temporary exception")

        // The day after expiry, replayed from the entry the first run stored: nothing in the build
        // changed, so only a date obtained at execution time can see the exception lapse.
        val second =
            project.runExpectingFailure(
                "checkModuleGraph",
                "--configuration-cache",
                "-D$HELIX_TODAY_PROPERTY=${expires.plusDays(1)}",
            )
        assertContains(second.output, "Reusing configuration cache")
        assertContains(second.output, "[EXC-EXPIRED]")
    }

    @Test
    fun `migration versions must be unique across modules`() {
        val result =
            helixProject()
                .withModule(
                    ":capability:one-impl",
                    roleModule("bkp.kmp.capability.impl"),
                    mapOf("src/commonMain/sqldelight/example/2.sqm" to "-- first\n"),
                )
                .withModule(
                    ":storage:database",
                    roleModule("bkp.kmp.storage"),
                    mapOf("src/commonMain/sqldelight/example/2.sqm" to "-- second\n"),
                )
                .runAndFail("checkModuleGraph")

        assertContains(result.output, "[STORAGE-MIGRATION-DUPLICATE] migration version 2")
        assertContains(result.output, "capability/one-impl/src/commonMain/sqldelight/example/2.sqm")
        assertContains(result.output, "storage/database/src/commonMain/sqldelight/example/2.sqm")
    }

    @Test
    fun `migration file name must be a positive integer`() {
        val result =
            helixProject()
                .withModule(
                    ":storage:database",
                    roleModule("bkp.kmp.storage"),
                    mapOf("src/commonMain/sqldelight/example/next.sqm" to "-- invalid\n"),
                )
                .runAndFail("checkModuleGraph")

        assertContains(
            result.output,
            "[STORAGE-MIGRATION-NAME] storage/database/src/commonMain/sqldelight/example/next.sqm",
        )
    }

    private fun deniedFeatureProject(exceptionJson: String): TestProject =
        helixProject(exceptionJson)
            .withModule(
                ":feature:consumer",
                roleModule(
                    "bkp.kmp.feature",
                    "implementation(project(\":capability:secret-impl\"))",
                ),
            )
            .withModule(
                ":capability:secret-impl",
                roleModule("bkp.kmp.capability.impl"),
            )

    private fun helixProject(exceptionJson: String = EMPTY_EXCEPTIONS): TestProject =
        TestProject(projectDir)
            .withBuildScript("plugins { id(\"bkp.validation.graph\") }")
            .withFile("config/helix/dependency-policy.json", POLICY)
            .withFile("config/helix/exceptions.json", exceptionJson)

    private fun roleModule(plugin: String, dependency: String = ""): String =
        """
        plugins { id("$plugin") }
        kotlin {
            bkpTargets { exception("TestKit keeps graph tests JVM-only") { jvm() } }
            sourceSets.commonMain.dependencies { $dependency }
        }
        """

    private fun exceptions(expires: String, rule: String, scope: String): String =
        """
        {
          "schema": 1,
          "exceptions": [
            {
              "rule": "$rule",
              "scope": "$scope",
              "owner": "architecture-test",
              "reason": "proves downgrade semantics",
              "created": "2026-01-01",
              "expires": "$expires",
              "removalCondition": "test completes"
            }
          ]
        }
        """
            .trimIndent()

    private fun TestProject.runAndFail(vararg tasks: String) = runExpectingFailure(*tasks)

    companion object {
        private const val FEATURE_SOURCE_ROOT =
            "src/commonMain/kotlin/dev/mayankmkh/basekmpproject/feature/late"
        private const val EMPTY_EXCEPTIONS = """{"schema":1,"exceptions":[]}"""
        private val POLICY =
            """
            {
              "schema": 2,
              "defaultDecision": "deny",
              "roles": {
                "app": { "allow": ["feature", "ui", "capability_api", "capability_impl", "foundation_api", "foundation_runtime", "platform", "platform_api", "platform_impl", "storage"] },
                "feature": { "allow": ["ui", "capability_api", "foundation_api", "platform", "platform_api"] },
                "ui": { "allow": ["ui", "foundation_api"] },
                "capability_api": { "allow": ["capability_api", "foundation_api"] },
                "capability_impl": { "allow": ["capability_api", "foundation_api", "foundation_runtime", "platform", "platform_api"] },
                "foundation_api": { "allow": ["foundation_api"] },
                "foundation_runtime": { "allow": ["foundation_api", "foundation_runtime"] },
                "platform": { "allow": ["foundation_api", "foundation_runtime"] },
                "platform_api": { "allow": ["foundation_api"] },
                "platform_impl": { "allow": ["platform_api", "foundation_api", "foundation_runtime"] },
                "storage": { "allow": ["capability_impl", "foundation_api", "foundation_runtime"] }
              },
              "conditionalAllows": []
            }
            """
                .trimIndent()
    }
}
