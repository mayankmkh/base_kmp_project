package dev.mayankmkh.basekmpproject.platform.connectivity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class ConnectivityMonitorTest {
    @Test
    fun `shared monitor uses one upstream collection and sends changes to every collector`() =
        runTest {
            val online = MutableStateFlow(false)
            var upstreamCollections = 0
            val monitor =
                ConnectivityMonitor {
                        flow {
                            upstreamCollections += 1
                            emitAll(online)
                        }
                    }
                    .shared(backgroundScope)

            val first = async { monitor.isOnline().take(2).toList() }
            val second = async { monitor.isOnline().take(2).toList() }
            runCurrent()
            assertEquals(1, upstreamCollections)

            online.value = true

            assertEquals(listOf(false, true), first.await())
            assertEquals(listOf(false, true), second.await())
            assertEquals(1, upstreamCollections)
        }
}
