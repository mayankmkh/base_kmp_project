package dev.mayankmkh.basekmpproject.foundation.resource.runtime

import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import dev.mayankmkh.basekmpproject.foundation.resource.SyncStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coordinates process-local sync work while the database remains the value owner.
 *
 * One worker runs per key. [sync] starts or joins it, [syncIfDue] additionally skips a key whose
 * last attempt is younger than [minInterval], [observing] counts observers around an upstream flow
 * and starts one due background sync per collection, [retryOffline] restarts every observed key
 * whose last attempt failed offline, and [status] exposes the per-key ledger. Collect [status]
 * inside [observing] for the same key: an unobserved key may be evicted once the ledger exceeds
 * [maxEntries], which resets its status. [observations] wires all three together for the common
 * case.
 *
 * | Event               | State change                                                 |
 * |---------------------|--------------------------------------------------------------|
 * | start               | set in-flight and attempt time; preserve success and failure |
 * | join                | none                                                         |
 * | skip                | none                                                         |
 * | success             | clear in-flight, mark succeeded, clear failure               |
 * | failure             | clear in-flight, set failure, preserve succeeded             |
 * | worker cancellation | non-cancellable clear of in-flight; preserve ledger history  |
 * | caller cancellation | none; the shared worker keeps running                        |
 * | sync throws         | treated as failure with an unknown, non-retryable problem    |
 *
 * A caller whose worker was cancelled receives a retryable unknown failure unless the caller itself
 * was cancelled, in which case its own cancellation propagates.
 */
@Suppress("TooManyFunctions")
public class SyncCoordinator<Key : Any>(
    private val scope: CoroutineScope,
    private val sync: suspend (Key, RefreshQos) -> RefreshOutcome,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val minInterval: Duration = 30.seconds,
    private val maxEntries: Int = 256,
) {
    private val mutex = Mutex()
    private val entries = mutableMapOf<Key, Entry>()

    init {
        require(maxEntries > 0)
        require(!minInterval.isNegative())
    }

    public suspend fun sync(key: Key, qos: RefreshQos): RefreshOutcome =
        await(
            mutex.withLock {
                val entry = entryLocked(key)
                entry.inFlight ?: startLocked(key, entry, qos)
            }
        )

    public suspend fun syncIfDue(key: Key, qos: RefreshQos): RefreshOutcome? =
        mutex.withLock { startIfDueLocked(key, entryLocked(key), qos) }?.let { await(it) }

    public fun <T> observing(key: Key, upstream: Flow<T>): Flow<T> = flow {
        val entry = mutex.withLock {
            entryLocked(key).also {
                it.observers++
                startIfDueLocked(key, it, RefreshQos.background())
            }
        }
        try {
            emitAll(upstream)
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    entry.observers--
                    evictLocked(keep = null)
                }
            }
        }
    }

    /**
     * Starts a worker for every observed key whose last attempt failed with an
     * [ResourceProblemCategory.OFFLINE] problem and returns without awaiting them. Keys that
     * succeeded, failed for another reason, or already have a worker are left alone; connectivity
     * returning is not evidence that their value changed.
     */
    public suspend fun retryOffline(qos: RefreshQos) {
        mutex.withLock {
            for ((key, entry) in entries) {
                if (entry.observers > 0 && entry.inFlight == null && entry.failedOffline()) {
                    startLocked(key, entry, qos)
                }
            }
        }
    }

    public fun status(key: Key): Flow<SyncStatus> = flow {
        emitAll(mutex.withLock { entryLocked(key) }.status)
    }

    internal suspend fun ledgerSize(): Int = mutex.withLock { entries.size }

    private fun startIfDueLocked(
        key: Key,
        entry: Entry,
        qos: RefreshQos,
    ): Deferred<RefreshOutcome>? {
        entry.inFlight?.let {
            return it
        }
        val attempt = entry.lastAttempt
        return if (attempt != null && attempt.elapsedNow() < minInterval) null
        else startLocked(key, entry, qos)
    }

    private fun startLocked(key: Key, entry: Entry, qos: RefreshQos): Deferred<RefreshOutcome> {
        val worker = scope.async {
            var outcome: RefreshOutcome? = null
            try {
                runSync(key, qos).also { outcome = it }
            } finally {
                withContext(NonCancellable) {
                    mutex.withLock {
                        entry.settle(outcome)
                        evictLocked(keep = key)
                    }
                }
            }
        }
        // A worker launched into an already-cancelled scope never runs its body, so it never
        // settles.
        if (!worker.isCompleted) entry.start(worker, timeSource.markNow())
        return worker
    }

    private suspend fun await(worker: Deferred<RefreshOutcome>): RefreshOutcome =
        try {
            worker.await()
        } catch (_: CancellationException) {
            currentCoroutineContext().ensureActive()
            RefreshOutcome.Failed(CancelledWorkerProblem)
        }

    private suspend fun runSync(key: Key, qos: RefreshQos): RefreshOutcome =
        try {
            sync.invoke(key, qos)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            RefreshOutcome.Failed(UnexpectedWorkerProblem)
        }

    private fun entryLocked(key: Key): Entry =
        entries[key]
            ?: Entry().also {
                entries[key] = it
                evictLocked(keep = key)
            }

    private fun evictLocked(keep: Key?) {
        while (entries.size > maxEntries) {
            val victim =
                entries.entries
                    .asSequence()
                    .filter { (candidate, entry) ->
                        candidate != keep && entry.observers == 0 && entry.inFlight == null
                    }
                    .maxByOrNull { it.value.lastAttempt?.elapsedNow() ?: Duration.INFINITE }
                    ?: return
            entries.remove(victim.key)
        }
    }

    /** Per-key ledger; every field is read and written under [mutex]. */
    private class Entry {
        val status =
            MutableStateFlow(SyncStatus(inFlight = false, lastFailure = null, hasSucceeded = false))
        var inFlight: Deferred<RefreshOutcome>? = null
        var lastAttempt: TimeMark? = null
        var observers = 0

        fun failedOffline(): Boolean =
            status.value.lastFailure?.category == ResourceProblemCategory.OFFLINE

        fun start(worker: Deferred<RefreshOutcome>, at: TimeMark) {
            inFlight = worker
            lastAttempt = at
            status.update { it.copy(inFlight = true) }
        }

        /** [outcome] is null when the worker was cancelled before it produced one. */
        fun settle(outcome: RefreshOutcome?) {
            inFlight = null
            status.update { current ->
                when (outcome) {
                    null -> current.copy(inFlight = false)
                    RefreshOutcome.Succeeded ->
                        current.copy(inFlight = false, lastFailure = null, hasSucceeded = true)
                    is RefreshOutcome.Failed ->
                        current.copy(inFlight = false, lastFailure = outcome.problem)
                }
            }
        }
    }
}

private val CancelledWorkerProblem =
    ResourceProblem(ResourceProblemCategory.UNKNOWN, retryable = true)
private val UnexpectedWorkerProblem =
    ResourceProblem(ResourceProblemCategory.UNKNOWN, retryable = false)
