package dev.mayankmkh.basekmpproject.platform.securestorage

import co.touchlab.kermit.Logger
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Random runs of launches from a fresh install, the OS vault up, down or refusing at each one.
 *
 * The promise under test: a value the store acknowledged is read back by every later launch, or the
 * read fails with [SecretStoreException]; it never silently reads as gone. Losing the OS vault's
 * own keyset is outside it, so the vault here only ever goes away and comes back.
 */
class KeysetVaultSequenceTest {
    private enum class Vault {
        Up,
        Unavailable,
        Refusing,
    }

    private class OsVault : KeysetVault {
        var json: String? = null
        var state = Vault.Up

        override fun read(): String? {
            check()
            return json
        }

        override fun write(json: String) {
            check()
            this.json = json
        }

        private fun check() {
            when (state) {
                Vault.Up -> Unit
                Vault.Unavailable ->
                    throw KeysetVaultUnavailableException("vault is down", IllegalStateException())
                Vault.Refusing -> throw SecretStoreException("vault refused")
            }
        }
    }

    @Test
    fun `an acknowledged secret is never read back as missing`() = runTest {
        val random = Random(Seed)
        repeat(Runs) { run ->
            val directory = Files.createTempDirectory("keyset-sequence").toFile()
            try {
                val osVault = OsVault()
                var acknowledged: String? = null
                val history = mutableListOf<String>()
                repeat(Launches) { index ->
                    osVault.state = Vault.entries[random.nextInt(Vault.entries.size)]
                    val writes = random.nextBoolean()
                    launch(directory, osVault) { store ->
                        val read = runCatching { store.get(Key) }
                        history += "${osVault.state}:${read.getOrNull() ?: read.exceptionOrNull()}"
                        when {
                            read.isSuccess && read.getOrNull() != acknowledged ->
                                fail(
                                    "run $run read ${read.getOrNull()}, wanted $acknowledged: $history"
                                )
                            read.isFailure && read.exceptionOrNull() !is SecretStoreException ->
                                throw checkNotNull(read.exceptionOrNull())
                        }
                        if (writes) {
                            val value = "value-$run-$index"
                            if (runCatching { store.set(Key, value) }.isSuccess)
                                acknowledged = value
                        }
                    }
                }
            } finally {
                directory.deleteRecursively()
            }
        }
    }

    private suspend fun TestScope.launch(
        directory: File,
        osVault: OsVault,
        block: suspend (SecretStore) -> Unit,
    ) {
        val vault =
            FallbackKeysetVault(osVault, FileKeysetVault(fallbackKeysetFile(directory)), Logger) {
                directory.hasEncryptedSecrets()
            }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        try {
            block(
                dataStoreSecretStore(
                    name = StoreName,
                    logger = Logger,
                    produceSerializer = { encryptedSerializer(StoreName, vault) },
                    scope = scope,
                    produceFile = { directory.resolve("$StoreName$SecretsFileSuffix") },
                )
            )
        } finally {
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }

    private companion object {
        const val StoreName = "credentials"
        const val Key = "token"
        const val Seed = 20261008
        const val Runs = 120
        const val Launches = 6
    }
}
