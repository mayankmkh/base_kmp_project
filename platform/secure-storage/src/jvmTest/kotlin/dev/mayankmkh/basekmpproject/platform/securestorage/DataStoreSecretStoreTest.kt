package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.Logger
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class DataStoreSecretStoreTest {
    @Test
    fun `the real serializer is built on the first read, not when the store opens`() = runTest {
        val file = Files.createTempDirectory("lazy-serializer").resolve("test.secrets").toFile()
        var builds = 0
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val store =
            dataStoreSecretStore(
                name = "test",
                logger = Logger,
                produceSerializer = {
                    builds++
                    MapStringSerializer
                },
                scope = scope,
            ) {
                file
            }
        assertEquals(0, builds)

        store.set("token", "secret")
        assertEquals("secret", store.get("token"))

        assertEquals(1, builds)
        scope.coroutineContext[Job]?.cancelAndJoin()
    }
}
