package dev.mayankmkh.basekmpproject.foundation.sqldelight

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class QueriesTest {
    @Test
    fun `observeList emits current rows and rows after invalidation`() = runTest {
        val query = MutableStringQuery(listOf("first"))

        query.observeList().test {
            assertEquals(listOf("first"), awaitItem())

            query.rows = listOf("first", "second")
            query.notifyDataChanged()

            assertEquals(listOf("first", "second"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeOneOrNull emits null when the query is empty`() = runTest {
        assertNull(MutableStringQuery(emptyList()).observeOneOrNull().first())
    }
}

private class MutableStringQuery(initialRows: List<String>) :
    Query<String>({ cursor -> requireNotNull(cursor.getString(0)) }) {
    private val listeners = mutableSetOf<Query.Listener>()

    var rows: List<String> = initialRows

    override fun addListener(listener: Query.Listener) {
        listeners += listener
    }

    override fun removeListener(listener: Query.Listener) {
        listeners -= listener
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        mapper(StringCursor(rows))

    fun notifyDataChanged() {
        listeners.toList().forEach(Query.Listener::queryResultsChanged)
    }
}

private class StringCursor(private val rows: List<String>) : SqlCursor {
    private var position = -1

    override fun next(): QueryResult<Boolean> = QueryResult.Value(++position < rows.size)

    override fun getString(index: Int): String? {
        require(index == 0)
        return rows[position]
    }

    override fun getLong(index: Int): Long? = null

    override fun getBytes(index: Int): ByteArray? = null

    override fun getDouble(index: Int): Double? = null

    override fun getBoolean(index: Int): Boolean? = null
}
