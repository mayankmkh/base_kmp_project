package dev.mayankmkh.basekmpproject.app.shared.di

import co.touchlab.kermit.Severity
import dev.mayankmkh.basekmpproject.app.shared.config.BuildEnvironment
import io.ktor.client.plugins.logging.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The four builds that can exist, and what each one is configured to do.
 *
 * The logging assertion is the one that earns its keep. Kermit's minimum severity and Ktor's log
 * level are two gates in series: Ktor decides what to hand the adapter, Kermit decides what
 * survives. Setting only one of them produces a build that formats every header line and then
 * discards it, and nothing observable says so.
 */
class AppEnvironmentTest {
    @Test
    fun aBuildToldToLogHttpCanActuallyEmitIt() {
        forEveryBuild { environment ->
            if (environment.ktorLogLevel != LogLevel.NONE) {
                // `KermitKtorLogger` emits at Debug, so the floor has to be Debug or lower.
                assertTrue(
                    environment.minSeverity <= Severity.Debug,
                    "${describe(environment)} asks Ktor for ${environment.ktorLogLevel} but " +
                        "Kermit drops everything below ${environment.minSeverity}",
                )
            }
        }
    }

    @Test
    fun noShippedBuildLogsHeadersOrBodies() {
        forEveryBuild { environment ->
            if (!environment.isDebug) {
                // A release binary is an artifact somebody else is holding, so header exposure
                // stops at the developer's own machine. `NetworkFailure` carries the request id
                // and the response body itself, so a shipped build gives up no failure diagnostics
                // by staying below this line.
                assertFalse(environment.ktorLogLevel.headers, describe(environment))
                assertFalse(environment.ktorLogLevel.body, describe(environment))
            }
        }
    }

    @Test
    fun onlyAProductionReleaseIsSilent() {
        forEveryBuild { environment ->
            val silent =
                environment.ktorLogLevel == LogLevel.NONE &&
                    environment.minSeverity == Severity.Warn
            assertEquals(
                !environment.isDebug && environment.environment == BuildEnvironment.Production,
                silent,
                describe(environment),
            )
        }
    }

    private fun forEveryBuild(assertion: (AppEnvironment) -> Unit) {
        listOf(true, false).forEach { isDebug ->
            BuildEnvironment.entries.forEach { target ->
                assertion(AppEnvironment(isDebug = isDebug, environment = target))
            }
        }
    }

    private fun describe(environment: AppEnvironment) =
        "${environment.environment} ${if (environment.isDebug) "debug" else "release"}"
}
