package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.db.SqlDriver
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.dispatchers.AppDispatchers
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/**
 * [AppDatabaseDriverProvider.driver] and [AppDatabaseDriverProvider.close] racing on real threads.
 *
 * Each round builds a provider whose opener pauses at a random point -- a yield, a delay or a
 * blocking sleep -- and sometimes fails, starts several callers on a four-thread pool and closes
 * the provider from a plain thread at a random moment. Whatever the interleaving: at most one
 * driver is ever handed out, every driver the opener made is closed exactly once by the time
 * everyone is done, and no caller that started after `close` returned gets a driver.
 */
class AppDatabaseDriverProviderStressTest {
    // One round's schedule and its checks read best in one place.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    @Test
    fun `driver and close racing on several threads hand out one driver and close every driver once`() {
        val pool = Executors.newFixedThreadPool(Threads)
        val dispatcher = pool.asCoroutineDispatcher()
        val closer = Executors.newSingleThreadExecutor()
        val violations = Collections.synchronizedList(mutableListOf<String>())
        var handedOut = 0
        var refused = 0
        try {
            repeat(Rounds) { round ->
                val random = Random(round)
                val made = Collections.synchronizedList(mutableListOf<CountingDriver>())
                val opens = AtomicInteger()
                val failNext = AtomicBoolean(random.nextInt(4) == 0)
                val provider =
                    AppDatabaseDriverProvider(
                        PlatformContext("com.example.app"),
                        dispatchers(dispatcher),
                    ) {
                        opens.incrementAndGet()
                        val local = ThreadLocalRandom.current()
                        when (local.nextInt(3)) {
                            0 -> yield()
                            1 -> delay(local.nextLong(0, 2))
                            else -> Thread.sleep(0, local.nextInt(0, 200_000))
                        }
                        if (failNext.getAndSet(false)) error("the disk was busy")
                        CountingDriver().also { made += it }
                    }
                val closed = AtomicBoolean(false)
                val closeAt = random.nextLong(0, 300_000)
                val closing = CountDownLatch(1)
                closer.execute {
                    val until = System.nanoTime() + closeAt
                    while (System.nanoTime() < until) Thread.onSpinWait()
                    provider.close()
                    closed.set(true)
                    closing.countDown()
                }
                val results =
                    runBlocking(dispatcher) {
                        (0 until Callers)
                            .map {
                                async {
                                    if (ThreadLocalRandom.current().nextBoolean()) yield()
                                    val startedAfterClose = closed.get()
                                    val result = runCatching { provider.driver() }
                                    startedAfterClose to result
                                }
                            }
                            .awaitAll()
                    }
                check(closing.await(5, TimeUnit.SECONDS)) { "close never ran" }
                // A second close after everything settled closes nothing again.
                provider.close()
                val drivers = results.mapNotNull { it.second.getOrNull() }.distinct()
                handedOut += results.count { it.second.isSuccess }
                refused += results.count { it.second.isFailure }
                if (drivers.size > 1)
                    violations += "round $round: ${drivers.size} drivers handed out"
                if (made.count { it in drivers } > 1) violations += "round $round: two published"
                results.forEach { (afterClose, result) ->
                    if (afterClose && result.isSuccess)
                        violations += "round $round: a driver after close"
                    result.exceptionOrNull()?.let {
                        if (it !is IllegalStateException) violations += "round $round: $it"
                    }
                }
                made.forEach {
                    if (it.closes.get() != 1)
                        violations += "round $round: a driver closed ${it.closes.get()} times"
                }
                if (made.size > 1) violations += "round $round: ${made.size} drivers opened"
                if (opens.get() > 2) violations += "round $round: ${opens.get()} opens"
            }
        } finally {
            closer.shutdownNow()
            pool.shutdownNow()
        }
        println(
            "STRESS AppDatabaseDriverProvider: $Rounds rounds, $handedOut handed out, $refused refused"
        )
        if (violations.isNotEmpty()) fail(violations.take(30).joinToString("\n"))
        assertEquals(Rounds * Callers, handedOut + refused)
    }

    /**
     * The narrow race: two closes, and the open's publish, all landing within nanoseconds. The
     * opener returns at once and two closer threads are released by the same spin flag the opener
     * sets on its way out, so the close's swap and the open's compare-and-set meet head on.
     */
    @Test
    fun `closes landing as the open publishes close the driver exactly once`() {
        val pool = Executors.newFixedThreadPool(2)
        val dispatcher = pool.asCoroutineDispatcher()
        val violations = mutableListOf<String>()
        var lostToClose = 0
        try {
            repeat(TightRounds) { round ->
                val driver = CountingDriver()
                val opening = AtomicBoolean(false)
                val provider =
                    AppDatabaseDriverProvider(
                        PlatformContext("com.example.app"),
                        dispatchers(dispatcher),
                    ) {
                        opening.set(true)
                        driver
                    }
                val closers =
                    List(2) {
                        Thread {
                            while (!opening.get()) Thread.onSpinWait()
                            provider.close()
                        }
                    }
                closers.forEach(Thread::start)
                val handed = runBlocking { runCatching { provider.driver() } }
                closers.forEach(Thread::join)
                if (handed.isFailure) lostToClose++
                if (driver.closes.get() != 1)
                    violations += "round $round: closed ${driver.closes.get()} times"
            }
        } finally {
            pool.shutdownNow()
        }
        println(
            "STRESS AppDatabaseDriverProvider tight: $TightRounds rounds, $lostToClose lost to close"
        )
        if (violations.isNotEmpty()) fail(violations.take(30).joinToString("\n"))
    }

    private fun dispatchers(dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        AppDispatchers(dispatcher, dispatcher, dispatcher, dispatcher, dispatcher, dispatcher)

    /** A driver that only counts its closes; any other call is a test bug. */
    private class CountingDriver(val closes: AtomicInteger = AtomicInteger()) :
        SqlDriver by proxy(closes)

    private companion object {
        const val Rounds = 2_000
        const val Callers = 6
        const val Threads = 4
        const val TightRounds = 5_000

        fun proxy(closes: AtomicInteger): SqlDriver =
            Proxy.newProxyInstance(
                SqlDriver::class.java.classLoader,
                arrayOf(SqlDriver::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "close" -> {
                        closes.incrementAndGet()
                        null
                    }
                    else -> error("unexpected ${method.name}")
                }
            } as SqlDriver
    }
}
