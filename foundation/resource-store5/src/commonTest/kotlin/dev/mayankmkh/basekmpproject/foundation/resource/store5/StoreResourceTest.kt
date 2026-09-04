package dev.mayankmkh.basekmpproject.foundation.resource.store5

import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceFreshness
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceOperation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.FetcherResult
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder

class StoreResourceTest {
    @Test
    fun `source of truth is idle without observation subscribers`() = runTest {
        val source = MutableStateFlow<String?>("cached")
        var readerStarts = 0
        val resource =
            resource(
                store = store(source, reader = source.onStart { readerStarts++ }),
                stopTimeoutSeconds = 1,
            )

        runCurrent()

        assertEquals(0, readerStarts)
        resource.close()
    }

    @Test
    fun `upstream follows subscribers and grace while retaining its last value`() = runTest {
        val source = MutableStateFlow<String?>("cached")
        var readerStarts = 0
        var readerStops = 0
        val reader = source.onStart { readerStarts++ }.onCompletion { readerStops++ }
        val resource = resource(store(source, reader = reader), stopTimeoutSeconds = 1)
        val observations = mutableListOf<ResourceObservation<String>>()

        val first = backgroundScope.launch { resource.observations.collect(observations::add) }
        runCurrent()
        assertEquals(1, readerStarts)
        assertEquals("cached", observations.last().value)

        first.cancelAndJoin()
        advanceTimeBy(999)
        runCurrent()
        assertEquals(0, readerStops)

        val insideGrace = backgroundScope.launch {
            resource.observations.collect(observations::add)
        }
        runCurrent()
        assertEquals(1, readerStarts)
        insideGrace.cancelAndJoin()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, readerStops)

        source.value = "updated"
        observations.clear()
        val afterGrace = backgroundScope.launch { resource.observations.collect(observations::add) }
        runCurrent()

        assertEquals(2, readerStarts)
        assertEquals("updated", observations.last().value)
        assertEquals(ResourceFreshness.STALE, observations.last().freshness)

        afterGrace.cancelAndJoin()
        resource.close()
    }

    @Test
    fun `cached value becomes fresh on success and stays stale on failure`() = runTest {
        val source = MutableStateFlow<String?>("cached")
        var fetchResult: FetcherResult<String> = FetcherResult.Data("fresh")
        val resource = resource(store(source) { fetchResult })

        val cached = resource.observations.filter { it.value != null }.first()
        assertEquals("cached", cached.value)
        assertEquals(ResourceFreshness.STALE, cached.freshness)
        assertSame(ResourceOperation.Idle, cached.operation)

        assertSame(RefreshOutcome.Succeeded, resource.refresh(RefreshQos.visible()))
        val fresh = resource.observations.value
        assertEquals("fresh", fresh.value)
        assertEquals(ResourceFreshness.FRESH, fresh.freshness)
        assertSame(ResourceOperation.Idle, fresh.operation)

        fetchResult = FetcherResult.Error.Exception(IOException("offline"))
        val outcome = assertIs<RefreshOutcome.Failed>(resource.refresh(RefreshQos.visible()))
        val failed = resource.observations.value
        assertEquals(ResourceProblemCategory.OFFLINE, outcome.problem.category)
        assertEquals(true, outcome.problem.retryable)
        assertEquals("fresh", failed.value)
        assertEquals(ResourceFreshness.STALE, failed.freshness)
        assertEquals(outcome.problem, assertIs<ResourceOperation.Failed>(failed.operation).problem)

        resource.close()
    }

    @Test
    fun `cancelling refresh caller still lets the resource settle`() = runTest {
        val source = MutableStateFlow<String?>("cached")
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val resource =
            resource(
                store(source) {
                    fetchStarted.complete(Unit)
                    releaseFetch.await()
                    FetcherResult.Data("fresh")
                }
            )
        val observer = backgroundScope.launch { resource.observations.collect() }
        runCurrent()
        val caller = launch { resource.refresh(RefreshQos.visible()) }
        runCurrent()
        fetchStarted.await()

        caller.cancelAndJoin()
        releaseFetch.complete(Unit)
        runCurrent()

        assertFalse(resource.observations.value.operation is ResourceOperation.Refreshing)
        assertSame(ResourceOperation.Idle, resource.observations.value.operation)

        observer.cancelAndJoin()
        resource.close()
    }

    @Test
    fun `close stops upstream with an active collector`() = runTest {
        val source = MutableStateFlow<String?>("cached")
        var readerStarts = 0
        var readerStops = 0
        val reader = source.onStart { readerStarts++ }.onCompletion { readerStops++ }
        val resource = resource(store(source, reader = reader), stopTimeoutSeconds = 1)
        val observer = backgroundScope.launch { resource.observations.collect() }
        runCurrent()
        assertEquals(1, readerStarts)

        resource.close()
        runCurrent()

        assertEquals(1, readerStops)
        observer.cancelAndJoin()
    }

    private fun TestScope.resource(
        store: Store<Unit, String>,
        stopTimeoutSeconds: Int = 5,
    ): StoreResource<Unit, String, String> =
        StoreResource(
            scope = backgroundScope,
            store = store,
            key = Unit,
            mapValue = { it },
            stopTimeout = stopTimeoutSeconds.seconds,
        )

    private fun TestScope.store(
        source: MutableStateFlow<String?>,
        reader: Flow<String?> = source,
        fetch: suspend () -> FetcherResult<String> = { FetcherResult.Data("fresh") },
    ): Store<Unit, String> =
        StoreBuilder.from(
                fetcher = Fetcher.ofResult<Unit, String> { fetch() },
                sourceOfTruth =
                    SourceOfTruth.of<Unit, String, String>(
                        reader = { reader },
                        writer = { _, value -> source.value = value },
                    ),
            )
            .disableCache()
            .scope(backgroundScope)
            .build()
}
