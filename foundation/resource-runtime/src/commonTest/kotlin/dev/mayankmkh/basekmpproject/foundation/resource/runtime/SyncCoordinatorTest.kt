package dev.mayankmkh.basekmpproject.foundation.resource.runtime

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.Problem
import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
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
                Outcome.Completed(Unit)
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
            assertEquals(Outcome.Completed(Unit), first.await())
            assertEquals(Outcome.Completed(Unit), joined.await())
            assertEquals(IdleStatus.copy(hasSucceeded = true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failure is preserved at the next start and success clears it`() = runTest {
        val problem = Problem(ProblemKind.OFFLINE)
        val secondStarted = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator =
            coordinator<String> { _, _ ->
                calls++
                if (calls == 1) Outcome.Failed(problem)
                else {
                    secondStarted.complete(Unit)
                    releaseSecond.await()
                    Outcome.Completed(Unit)
                }
            }

        coordinator.status("key").test {
            assertEquals(IdleStatus, awaitItem())
            assertEquals(
                Outcome.Failed(problem),
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
            assertEquals(Outcome.Completed(Unit), second.await())
            assertEquals(IdleStatus.copy(hasSucceeded = true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failure preserves a previous success`() = runTest {
        val problem = Problem(ProblemKind.SERVER)
        var outcome: Outcome<Unit> = Outcome.Completed(Unit)
        val coordinator = coordinator<String> { _, _ -> outcome }

        assertEquals(Outcome.Completed(Unit), coordinator.sync("key", RefreshQos.visible()))
        outcome = Outcome.Failed(problem)
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
                Outcome.Completed(Unit)
            }
        assertEquals(Outcome.Completed(Unit), coordinator.syncIfDue("key", RefreshQos.background()))

        coordinator.status("key").test {
            assertEquals(IdleStatus.copy(hasSucceeded = true), awaitItem())
            assertNull(coordinator.syncIfDue("key", RefreshQos.background()))
            assertEquals(1, calls)
            expectNoEvents()

            time += 30.seconds
            assertEquals(
                Outcome.Completed(Unit),
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
                Outcome.Completed(Unit)
            }

        coordinator.syncIfDue("key", RefreshQos.background())
        assertEquals(Outcome.Completed(Unit), coordinator.sync("key", RefreshQos.visible()))
        assertEquals(2, calls)
    }

    @Test
    fun `observing starts an appearance sync and stopping removes the offline retry`() = runTest {
        val upstream = MutableSharedFlow<String>()
        val reconnects = MutableSharedFlow<Unit>()
        var calls = 0
        val coordinator =
            coordinator<String>(retryTriggers = reconnects) { _, _ ->
                calls++
                Outcome.Failed(Offline)
            }
        val collector = backgroundScope.launch { coordinator.observing("key", upstream).collect {} }
        runCurrent()
        assertEquals(1, calls)

        reconnects.emit(Unit)
        runCurrent()
        assertEquals(2, calls)
        collector.cancelAndJoin()
        reconnects.emit(Unit)
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun `a retry trigger restarts only observed keys whose last attempt failed offline`() =
        runTest {
            val upstream = MutableSharedFlow<Unit>()
            val reconnects = MutableSharedFlow<Unit>()
            val outcomes =
                mapOf(
                    "offline" to Outcome.Failed(Offline),
                    "temporary" to Outcome.Failed(Problem(ProblemKind.SERVER)),
                    "succeeded" to Outcome.Completed(Unit),
                )
            val calls = mutableMapOf<String, Int>()
            val coordinator =
                coordinator<String>(retryTriggers = reconnects) { key, _ ->
                    calls[key] = calls.getOrElse(key) { 0 } + 1
                    outcomes[key] ?: Outcome.Failed(Offline)
                }
            val observers =
                outcomes.keys.map { key ->
                    backgroundScope.launch { coordinator.observing(key, upstream).collect {} }
                }
            assertEquals(
                Outcome.Failed(Offline),
                coordinator.sync("unobserved", RefreshQos.visible()),
            )
            runCurrent()
            assertEquals(
                mapOf("offline" to 1, "temporary" to 1, "succeeded" to 1, "unobserved" to 1),
                calls,
            )

            reconnects.emit(Unit)
            runCurrent()

            assertEquals(
                mapOf("offline" to 2, "temporary" to 1, "succeeded" to 1, "unobserved" to 1),
                calls,
            )
            observers.forEach { it.cancelAndJoin() }
        }

    @Test
    fun `a retry trigger leaves an in-flight retry alone`() = runTest {
        val upstream = MutableSharedFlow<Unit>()
        val reconnects = MutableSharedFlow<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator =
            coordinator<String>(retryTriggers = reconnects) { _, _ ->
                calls++
                if (calls > 1) release.await()
                Outcome.Failed(Offline)
            }
        val observer = backgroundScope.launch { coordinator.observing("key", upstream).collect {} }
        runCurrent()
        reconnects.emit(Unit)
        runCurrent()
        assertEquals(2, calls)

        reconnects.emit(Unit)
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
                Outcome.Completed(Unit)
            }
        val cancelledCaller = launch { coordinator.sync("key", RefreshQos.visible()) }
        started.await()
        cancelledCaller.cancelAndJoin()
        val remainingCaller = backgroundScope.async {
            coordinator.sync("key", RefreshQos.visible())
        }

        release.complete(Unit)
        assertEquals(Outcome.Completed(Unit), remainingCaller.await())
        assertEquals(1, calls)
    }

    @Test
    fun `worker cancellation clears in-flight and active caller receives unexpected failure`() =
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
                        Outcome.Completed(Unit)
                    },
                )
            val caller = backgroundScope.async { coordinator.sync("key", RefreshQos.visible()) }
            started.await()
            workerScope.cancel()
            runCurrent()

            assertEquals(
                Outcome.Failed(Problem(ProblemKind.UNEXPECTED)),
                caller.await(),
            )
            assertEquals(IdleStatus, coordinator.status("key").first())
        }

    @Test
    fun `unexpected throwable becomes non-retryable unknown failure`() = runTest {
        val coordinator = coordinator<String> { _, _ -> error("boom") }

        val outcome = coordinator.sync("key", RefreshQos.visible())

        assertEquals(
            Outcome.Failed(Problem(ProblemKind.UNEXPECTED)),
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
                Outcome.Completed(Unit)
            }
        coordinator.syncIfDue("old", RefreshQos.background())
        time += 1.seconds
        coordinator.syncIfDue("new", RefreshQos.background())
        coordinator.status("third").first()

        assertEquals(Outcome.Completed(Unit), coordinator.syncIfDue("old", RefreshQos.background()))
        assertEquals(2, calls["old"])
        assertEquals(1, calls["new"])
    }

    @Test
    fun `releasing observers trims an oversized ledger without another lookup`() = runTest {
        val upstream = MutableSharedFlow<Unit>()
        val coordinator = coordinator<String>(maxEntries = 2) { _, _ -> Outcome.Completed(Unit) }
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
        retryTriggers: Flow<Unit> = emptyFlow(),
        sync: suspend (Key, RefreshQos) -> Outcome<Unit>,
    ) =
        SyncCoordinator(
            scope = backgroundScope,
            sync = sync,
            retryTriggers = retryTriggers,
            timeSource = timeSource,
            minInterval = minIntervalSeconds.seconds,
            maxEntries = maxEntries,
        )

    private companion object {
        val IdleStatus = SyncStatus(inFlight = false, lastFailure = null, hasSucceeded = false)
        val Offline = Problem(ProblemKind.OFFLINE)
    }
}
