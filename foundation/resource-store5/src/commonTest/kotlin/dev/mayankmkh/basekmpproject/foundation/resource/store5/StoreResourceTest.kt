package dev.mayankmkh.basekmpproject.foundation.resource.store5

import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceFreshness
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceOperation
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.FetcherResult
import org.mobilenativefoundation.store.store5.SourceOfTruth
import org.mobilenativefoundation.store.store5.StoreBuilder

class StoreResourceTest {
    @Test
    fun `cached value becomes fresh on success and stays stale on failure`() = runTest {
        val source = MutableStateFlow<String?>("cached")
        var fetchResult: FetcherResult<String> = FetcherResult.Data("fresh")
        val store =
            StoreBuilder.from(
                    fetcher = Fetcher.ofResult<Unit, String> { fetchResult },
                    sourceOfTruth =
                        SourceOfTruth.of<Unit, String, String>(
                            reader = { source },
                            writer = { _, value -> source.value = value },
                        ),
                )
                .disableCache()
                .scope(backgroundScope)
                .build()
        val resource =
            StoreResource(
                scope = backgroundScope,
                store = store,
                key = Unit,
                mapValue = { it },
            )

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
    }
}
