package dev.mayankmkh.basekmpproject.testkit

import dev.mayankmkh.basekmpproject.foundation.runtime.dispatchers.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Every application lane uses one scheduler, making ordering deterministic in tests. */
fun testDispatchers(dispatcher: CoroutineDispatcher): AppDispatchers =
    AppDispatchers(dispatcher, dispatcher, dispatcher, dispatcher, dispatcher, dispatcher)

/**
 * A multiplatform Main-dispatcher rule analogue for suites that own their test lifecycle.
 *
 * Only for suites whose runner blocks until the body finishes, such as `runComposeUiTest` on the
 * JVM. Asynchronous runners return before the body runs, so pair those with [runMainTest] instead.
 */
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) {
    fun starting() {
        Dispatchers.setMain(dispatcher)
    }

    fun finished() {
        Dispatchers.resetMain()
    }
}

/**
 * Runs a test with AndroidX ViewModel's Main dispatcher installed for the full coroutine body.
 *
 * Install and reset happen *inside* the coroutine on purpose. On JS and Wasm `TestResult` is a
 * `Promise`, so `runTest` returns before the body has run; installing around the call would reset
 * `Dispatchers.Main` first and leave `viewModelScope` on the real, never-dispatching
 * `Dispatchers.Main.immediate`, which starts flows inline instead of on the test scheduler.
 *
 * The drain before the reset settles work still parked on `viewModelScope` — a
 * `SharingStarted.WhileSubscribed` timeout, for example — while `Dispatchers.Main` is still the
 * test dispatcher. Left to `runTest`'s own teardown it would reach for a platform Main dispatcher
 * that host-side JVM and Android unit tests do not have.
 */
fun runMainTest(
    dispatcher: TestDispatcher = StandardTestDispatcher(),
    block: suspend TestScope.() -> Unit,
): TestResult =
    runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        try {
            block()
        } finally {
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }
