package dev.mayankmkh.basekmpproject.foundation.sqldelight

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class LazyDatabaseTest {
    @Test
    fun `create runs once across concurrent callers`() = runTest {
        val releaseDriver = CompletableDeferred<Unit>()
        var driverRequests = 0
        var creates = 0
        val database = Any()
        val lazyDatabase =
            LazyDatabase(
                drivers =
                    FakeSqlDriverProvider(driverLane()) {
                        driverRequests += 1
                        releaseDriver.await()
                        FakeSqlDriver
                    },
                create = {
                    creates += 1
                    database
                },
            )

        val callers = List(8) { async { lazyDatabase.use { this } } }
        runCurrent()
        releaseDriver.complete(Unit)
        val results = callers.awaitAll()

        assertEquals(1, driverRequests)
        assertEquals(1, creates)
        results.forEach { assertSame(database, it) }
    }

    @Test
    fun `observe stays cold until collection`() = runTest {
        var opened = false
        val lazyDatabase =
            LazyDatabase(
                drivers =
                    FakeSqlDriverProvider(driverLane()) {
                        opened = true
                        FakeSqlDriver
                    },
                create = { "database" },
            )
        val observation = lazyDatabase.observe { flowOf(this) }

        assertFalse(opened)
        assertEquals("database", observation.first())
        assertTrue(opened)
    }

    @Test
    fun `use runs the block on the provider's lane rather than the caller's`() = runTest {
        val lane = driverLane()

        assertSame(lane, lazyDatabaseOn(lane).use { currentDispatcher() })
    }

    @Test
    fun `observe collects the query on the provider's lane rather than the caller's`() = runTest {
        val lane = driverLane()

        val queryLane = lazyDatabaseOn(lane).observe { flow { emit(currentDispatcher()) } }.first()

        assertSame(lane, queryLane)
    }

    // `runTest` runs the body on its own dispatcher, so a query that comes back carrying this one
    // can only have got there by switching lanes. Sharing the scheduler keeps that switch ordered.
    private fun TestScope.driverLane(): CoroutineDispatcher =
        UnconfinedTestDispatcher(testScheduler)

    private fun lazyDatabaseOn(lane: CoroutineDispatcher) =
        LazyDatabase(
            drivers = FakeSqlDriverProvider(lane) { FakeSqlDriver },
            create = { "database" },
        )
}

private suspend fun currentDispatcher(): ContinuationInterceptor? =
    currentCoroutineContext()[ContinuationInterceptor]

private class FakeSqlDriverProvider(
    override val dispatcher: CoroutineDispatcher,
    private val open: suspend () -> SqlDriver,
) : SqlDriverProvider {
    override suspend fun driver(): SqlDriver = open()
}

private object FakeSqlDriver : SqlDriver {
    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = error("Not used by this test")

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = error("Not used by this test")

    override fun newTransaction(): QueryResult<Transacter.Transaction> =
        error("Not used by this test")

    override fun currentTransaction(): Transacter.Transaction? = null

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit

    override fun notifyListeners(vararg queryKeys: String) = Unit

    override fun close() = Unit
}
