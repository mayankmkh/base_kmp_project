package dev.mayankmkh.basekmpproject.foundation.resource.runtime

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import dev.mayankmkh.basekmpproject.foundation.resource.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SyncCoordinatorTest {
    @Test
    fun `start join and success follow the state table`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator =
            coordinator<String> { _, _ ->
                calls++
                started.complete(Unit)
                release.await()
                RefreshOutcome.Succeeded
            }

        coordinator.status("key").test {
            assertEquals(IdleStatus, awaitItem())
            val first = backgroundScope.async { coordinator.sync("key", RefreshQos.visible()) }
            started.await()
            assertEquals(IdleStatus.copy(inFlight = true), awaitItem())

            val joined = backgroundScope.async {
                coordinator.syncIfDue("key", RefreshQos.background())
            }
            runCurrent()
            assertEquals(1, calls)
            expectNoEvents()

            release.complete(Unit)
            assertSame(RefreshOutcome.Succeeded, first.await())
            assertSame(RefreshOutcome.Succeeded, joined.await())
            assertEquals(IdleStatus.copy(hasSucceeded = true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failure is preserved at the next start and success clears it`() = runTest {
        val problem = ResourceProblem(ResourceProblemCategory.OFFLINE, retryable = true)
        val secondStarted = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator =
            coordinator<String> { _, _ ->
                calls++
                if (calls == 1) RefreshOutcome.Failed(problem)
                else {
                    secondStarted.complete(Unit)
                    releaseSecond.await()
                    RefreshOutcome.Succeeded
                }
            }

        coordinator.status("key").test {
            assertEquals(IdleStatus, awaitItem())
            assertEquals(
                RefreshOutcome.Failed(problem),
                coordinator.sync("key", RefreshQos.visible()),
            )
            assertEquals(IdleStatus.copy(inFlight = true), awaitItem())
            assertEquals(IdleStatus.copy(lastFailure = problem), awaitItem())

            val second = backgroundScope.async { coordinator.sync("key", RefreshQos.visible()) }
            secondStarted.await()
            assertEquals(
                SyncStatus(inFlight = true, lastFailure = problem, hasSucceeded = false),
                awaitItem(),
            )
            releaseSecond.complete(Unit)
            assertSame(RefreshOutcome.Succeeded, second.await())
            assertEquals(IdleStatus.copy(hasSucceeded = true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failure preserves a previous success`() = runTest {
        val problem = ResourceProblem(ResourceProblemCategory.TEMPORARY, retryable = true)
        var outcome: RefreshOutcome = RefreshOutcome.Succeeded
        val coordinator = coordinator<String> { _, _ -> outcome }

        assertSame(RefreshOutcome.Succeeded, coordinator.sync("key", RefreshQos.visible()))
        outcome = RefreshOutcome.Failed(problem)
        assertEquals(outcome, coordinator.sync("key", RefreshQos.visible()))

        assertEquals(
            SyncStatus(inFlight = false, lastFailure = problem, hasSucceeded = true),
            coordinator.status("key").first(),
        )
    }

    @Test
    fun `not due skips without state change and expiry starts work`() = runTest {
        val time = TestTimeSource()
        var calls = 0
        val coordinator =
            coordinator<String>(timeSource = time, minIntervalSeconds = 30) { _, _ ->
                calls++
                RefreshOutcome.Succeeded
            }
        assertSame(RefreshOutcome.Succeeded, coordinator.syncIfDue("key", RefreshQos.background()))

        coordinator.status("key").test {
            assertEquals(IdleStatus.copy(hasSucceeded = true), awaitItem())
            assertNull(coordinator.syncIfDue("key", RefreshQos.background()))
            assertEquals(1, calls)
            expectNoEvents()

            time += 30.seconds
            assertSame(
                RefreshOutcome.Succeeded,
                coordinator.syncIfDue("key", RefreshQos.background()),
            )
            assertEquals(2, calls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `explicit sync bypasses the interval`() = runTest {
        var calls = 0
        val coordinator =
            coordinator<String> { _, _ ->
                calls++
                RefreshOutcome.Succeeded
            }

        coordinator.syncIfDue("key", RefreshQos.background())
        assertSame(RefreshOutcome.Succeeded, coordinator.sync("key", RefreshQos.visible()))
        assertEquals(2, calls)
    }

    @Test
    fun `observing starts an appearance sync and stopping removes the offline retry`() = runTest {
        val upstream = MutableSharedFlow<String>()
        var calls = 0
        val coordinator =
            coordinator<String> { _, _ ->
                calls++
                RefreshOutcome.Failed(Offline)
            }
        val collector = backgroundScope.launch { coordinator.observing("key", upstream).collect {} }
        runCurrent()
        assertEquals(1, calls)

        coordinator.retryOffline(RefreshQos.background())
        runCurrent()
        assertEquals(2, calls)
        collector.cancelAndJoin()
        coordinator.retryOffline(RefreshQos.background())
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun `retryOffline restarts only observed keys whose last attempt failed offline`() = runTest {
        val upstream = MutableSharedFlow<Unit>()
        val outcomes =
            mapOf(
                "offline" to RefreshOutcome.Failed(Offline),
                "temporary" to
                    RefreshOutcome.Failed(
                        ResourceProblem(ResourceProblemCategory.TEMPORARY, retryable = true)
                    ),
                "succeeded" to RefreshOutcome.Succeeded,
            )
        val calls = mutableMapOf<String, Int>()
        val coordinator =
            coordinator<String> { key, _ ->
                calls[key] = calls.getOrElse(key) { 0 } + 1
                outcomes[key] ?: RefreshOutcome.Failed(Offline)
            }
        val observers =
            outcomes.keys.map { key ->
                backgroundScope.launch { coordinator.observing(key, upstream).collect {} }
            }
        assertEquals(
            RefreshOutcome.Failed(Offline),
            coordinator.sync("unobserved", RefreshQos.visible()),
        )
        runCurrent()
        assertEquals(
            mapOf("offline" to 1, "temporary" to 1, "succeeded" to 1, "unobserved" to 1),
            calls,
        )

        coordinator.retryOffline(RefreshQos.background())
        runCurrent()

        assertEquals(
            mapOf("offline" to 2, "temporary" to 1, "succeeded" to 1, "unobserved" to 1),
            calls,
        )
        observers.forEach { it.cancelAndJoin() }
    }

    @Test
    fun `retryOffline leaves an in-flight retry alone`() = runTest {
        val upstream = MutableSharedFlow<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator =
            coordinator<String> { _, _ ->
                calls++
                if (calls > 1) release.await()
                RefreshOutcome.Failed(Offline)
            }
        val observer = backgroundScope.launch { coordinator.observing("key", upstream).collect {} }
        runCurrent()
        coordinator.retryOffline(RefreshQos.background())
        runCurrent()
        assertEquals(2, calls)

        coordinator.retryOffline(RefreshQos.background())
        runCurrent()
        assertEquals(2, calls)

        release.complete(Unit)
        runCurrent()
        assertEquals(2, calls)
        observer.cancelAndJoin()
    }

    @Test
    fun `caller cancellation stops waiting but does not cancel the worker`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator =
            coordinator<String> { _, _ ->
                calls++
                started.complete(Unit)
                release.await()
                RefreshOutcome.Succeeded
            }
        val cancelledCaller = launch { coordinator.sync("key", RefreshQos.visible()) }
        started.await()
        cancelledCaller.cancelAndJoin()
        val remainingCaller = backgroundScope.async {
            coordinator.sync("key", RefreshQos.visible())
        }

        release.complete(Unit)
        assertSame(RefreshOutcome.Succeeded, remainingCaller.await())
        assertEquals(1, calls)
    }

    @Test
    fun `worker cancellation clears in-flight and active caller receives retryable unknown`() =
        runTest {
            val workerScope = TestScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
            val started = CompletableDeferred<Unit>()
            val never = CompletableDeferred<Unit>()
            val coordinator =
                SyncCoordinator<String>(
                    scope = workerScope,
                    sync = { _, _ ->
                        started.complete(Unit)
                        never.await()
                        RefreshOutcome.Succeeded
                    },
                )
            val caller = backgroundScope.async { coordinator.sync("key", RefreshQos.visible()) }
            started.await()
            workerScope.cancel()
            runCurrent()

            assertEquals(
                RefreshOutcome.Failed(
                    ResourceProblem(ResourceProblemCategory.UNKNOWN, retryable = true)
                ),
                caller.await(),
            )
            assertEquals(IdleStatus, coordinator.status("key").first())
        }

    @Test
    fun `unexpected throwable becomes non-retryable unknown failure`() = runTest {
        val coordinator = coordinator<String> { _, _ -> error("boom") }

        val outcome = coordinator.sync("key", RefreshQos.visible())

        assertEquals(
            RefreshOutcome.Failed(
                ResourceProblem(ResourceProblemCategory.UNKNOWN, retryable = false)
            ),
            outcome,
        )
    }

    @Test
    fun `entry eviction removes the oldest idle unobserved attempt`() = runTest {
        val time = TestTimeSource()
        val calls = mutableMapOf<String, Int>()
        val coordinator =
            coordinator<String>(timeSource = time, maxEntries = 2) { key, _ ->
                calls[key] = calls.getOrElse(key) { 0 } + 1
                RefreshOutcome.Succeeded
            }
        coordinator.syncIfDue("old", RefreshQos.background())
        time += 1.seconds
        coordinator.syncIfDue("new", RefreshQos.background())
        coordinator.status("third").first()

        assertSame(RefreshOutcome.Succeeded, coordinator.syncIfDue("old", RefreshQos.background()))
        assertEquals(2, calls["old"])
        assertEquals(1, calls["new"])
    }

    @Test
    fun `releasing observers trims an oversized ledger without another lookup`() = runTest {
        val upstream = MutableSharedFlow<Unit>()
        val coordinator = coordinator<String>(maxEntries = 2) { _, _ -> RefreshOutcome.Succeeded }
        val collectors =
            (1..4).map { key ->
                backgroundScope.launch {
                    coordinator.observing(key.toString(), upstream).collect {}
                }
            }
        runCurrent()
        assertEquals(4, coordinator.ledgerSize())

        collectors.forEach { it.cancelAndJoin() }

        assertEquals(2, coordinator.ledgerSize())
    }

    private fun <Key : Any> TestScope.coordinator(
        timeSource: TestTimeSource = TestTimeSource(),
        minIntervalSeconds: Int = 30,
        maxEntries: Int = 256,
        sync: suspend (Key, RefreshQos) -> RefreshOutcome,
    ) =
        SyncCoordinator(
            scope = backgroundScope,
            sync = sync,
            timeSource = timeSource,
            minInterval = minIntervalSeconds.seconds,
            maxEntries = maxEntries,
        )

    private companion object {
        val IdleStatus = SyncStatus(inFlight = false, lastFailure = null, hasSucceeded = false)
        val Offline = ResourceProblem(ResourceProblemCategory.OFFLINE, retryable = true)
    }
}
