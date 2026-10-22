package dev.mayankmkh.basekmpproject.capability.identity.impl

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStore
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStores
import dev.mayankmkh.basekmpproject.platform.securestorage.inMemorySecretStores
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * A new sign-in racing a 401 for the credential before it, on a multi-threaded dispatcher with
 * every storage step yielding a random number of times. Whatever the order, the rejection of the
 * old credential never clears the new one.
 */
class CredentialStoreRaceTest {
    @Suppress("InjectDispatcher") // Steps running on several threads at once is the point.
    @Test
    fun `a rejection of the old credential never clears the one that replaced it`() = runTest {
        val random = Random(Seed)
        withContext(Dispatchers.Default) {
            repeat(Rounds) { round ->
                val credentials = CredentialStore(YieldingStores(), Logger)
                val provider = provider(credentials)
                credentials.setAuthToken("old")
                val signsOut = random.nextInt(4) == 0
                coroutineScope {
                    launch { credentials.setAuthToken("new") }
                    launch { provider.refreshCredential("old") }
                    if (signsOut) launch { credentials.removeAuthToken() }
                    repeat(Readers) { launch { repeat(Reads) { provider.currentCredential() } } }
                }
                val token = credentials.getAuthToken()
                if (token == null) {
                    assertTrue(signsOut, "round $round: the new credential was cleared")
                } else {
                    assertEquals("new", token, "round $round")
                }
            }
        }
    }

    /**
     * Every storage step yields before and after, a random number of times. The draws come from the
     * shared default generator on several threads; only their spread matters, not a seed.
     */
    private class YieldingStores : SecretStores {
        override fun open(name: String): SecretStore {
            val real = inMemorySecretStores().open(name)
            return object : SecretStore by real {
                override suspend fun get(key: String): String? = around { real.get(key) }

                override suspend fun set(key: String, value: String) = around {
                    real.set(key, value)
                }

                override suspend fun remove(key: String) = around { real.remove(key) }

                override suspend fun clear() = around { real.clear() }
            }
        }

        private suspend fun <T> around(step: suspend () -> T): T {
            repeat(Random.nextInt(MaxYields)) { yield() }
            return step().also { repeat(Random.nextInt(MaxYields)) { yield() } }
        }
    }

    private companion object {
        const val Seed = 20261022
        const val Rounds = 400
        const val Readers = 2
        const val Reads = 3
        const val MaxYields = 4
    }
}
