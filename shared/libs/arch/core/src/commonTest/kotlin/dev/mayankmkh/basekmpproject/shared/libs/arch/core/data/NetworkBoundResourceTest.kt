package dev.mayankmkh.basekmpproject.shared.libs.arch.core.data

import app.cash.turbine.test
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class NetworkBoundResourceTest {
    @Test
    fun `serves the cache without touching the network`() = runTest {
        val db = MutableStateFlow<String?>("cached")
        val resource = resource(db)

        resource.asFlow().test {
            assertEquals("cached", awaitItem().get())

            db.value = "updated"

            assertEquals("updated", awaitItem().get())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, resource.networkCalls)
    }

    @Test
    fun `fetches and saves when the cache is empty`() = runTest {
        val db = MutableStateFlow<String?>(null)
        val resource = resource(db, network = { "remote" })

        resource.asFlow().test {
            assertEquals("REMOTE", awaitItem().get())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, resource.networkCalls)
        assertEquals("REMOTE", db.value)
    }

    @Test
    fun `reports a network failure and emits it`() = runTest {
        val db = MutableStateFlow<String?>(null)
        val failures = mutableListOf<Throwable>()
        val resource =
            resource(
                db,
                network = { throw IllegalStateException("boom") },
                onFailure = failures::add,
            )

        resource.asFlow().test {
            assertIs<NoDataException>(awaitItem().getError())
            cancelAndIgnoreRemainingEvents()
        }
        // By message, not by identity: coroutine stacktrace recovery hands the listener a copy.
        assertEquals(listOf("boom"), failures.map { it.message })
    }

    private fun TestScope.resource(
        db: MutableStateFlow<String?>,
        network: suspend () -> String = { error("must not run") },
        onFailure: (Throwable) -> Unit = {},
    ) =
        FakeResource(
            testDispatchers(StandardTestDispatcher(testScheduler)),
            db,
            network,
            object : NetworkBoundResource.OnFailureListener {
                override fun onFetchFailed(throwable: Throwable) = onFailure(throwable)
            },
        )
}

/** Every dispatcher is the test one, so `runTest`'s scheduler drives the whole flow. */
private fun testDispatchers(dispatcher: CoroutineDispatcher) =
    AppDispatchers(dispatcher, dispatcher, dispatcher, dispatcher, dispatcher, dispatcher)

private class FakeResource(
    dispatchers: AppDispatchers,
    private val db: MutableStateFlow<String?>,
    private val network: suspend () -> String,
    failureListener: NetworkBoundResource.OnFailureListener,
) : NetworkBoundResource<String, String>(dispatchers, failureListener) {
    var networkCalls = 0
        private set

    override suspend fun processResponse(response: String) = response.uppercase()

    override suspend fun saveCallResult(item: String) {
        db.value = item
    }

    override suspend fun shouldFetch(data: String?) = data.isNullOrEmpty()

    override fun loadFromDb(): Flow<String?> = db

    override suspend fun loadFromNetwork(): String {
        networkCalls++
        return network()
    }
}
