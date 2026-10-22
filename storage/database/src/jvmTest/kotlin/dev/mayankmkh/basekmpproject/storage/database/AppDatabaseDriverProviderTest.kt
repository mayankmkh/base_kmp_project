package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.dispatchers.AppDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class AppDatabaseDriverProviderTest {

    @Test
    fun `a driver that finishes opening after close is closed and never handed out`() = runTest {
        val opening = CompletableDeferred<Unit>()
        val driver = CountingDriver()
        val provider =
            AppDatabaseDriverProvider(PlatformContext("com.example.app"), dispatchers()) {
                opening.await()
                driver
            }

        val caller = async { runCatching { provider.driver() } }
        runCurrent()
        provider.close()
        opening.complete(Unit)

        assertFailsWith<IllegalStateException> { caller.await().getOrThrow() }
        assertEquals(1, driver.closeCount)
        provider.close()
        assertEquals(1, driver.closeCount)
    }

    @Test
    fun `close closes the opened driver exactly once`() = runTest {
        val driver = CountingDriver()
        val provider =
            AppDatabaseDriverProvider(PlatformContext("com.example.app"), dispatchers()) { driver }

        assertSame(driver, provider.driver())
        assertSame(driver, provider.driver())
        provider.close()
        provider.close()

        assertEquals(1, driver.closeCount)
        assertFailsWith<IllegalStateException> { provider.driver() }
    }

    private fun TestScope.dispatchers(): AppDispatchers {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return AppDispatchers(
            dispatcher,
            dispatcher,
            dispatcher,
            dispatcher,
            dispatcher,
            dispatcher,
        )
    }

    private class CountingDriver(
        private val delegate: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    ) : SqlDriver by delegate {
        var closeCount = 0
            private set

        override fun close() {
            closeCount++
            delegate.close()
        }
    }
}
