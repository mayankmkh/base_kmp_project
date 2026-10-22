package dev.mayankmkh.basekmpproject.capability.identity.impl

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.foundation.runtime.causeClassName
import dev.mayankmkh.basekmpproject.foundation.runtime.logEvent
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStoreException
import dev.mayankmkh.basekmpproject.platform.securestorage.SecretStores
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class CredentialStore(stores: SecretStores, logger: Logger) {
    private val store = stores.open("identity.credentials")
    private val logger = logger.withTag("identity")

    /** Orders every write, so a compare-and-remove never clears a token written meanwhile. */
    private val writeLock = Mutex()

    suspend fun getAuthToken(): String? = store.get(TokenKey)

    suspend fun setAuthToken(token: String) = writeLock.withLock { store.set(TokenKey, token) }

    suspend fun removeAuthToken() = writeLock.withLock { store.remove(TokenKey) }

    /**
     * Removes the token only while it is still [ifCurrent], reading, comparing and removing under
     * the lock every write takes: a rejection of an older token must not end a newer session.
     * Returns whether it removed anything.
     */
    suspend fun removeAuthToken(ifCurrent: String): Boolean = writeLock.withLock {
        val removes = store.get(TokenKey) == ifCurrent
        if (removes) store.remove(TokenKey)
        removes
    }

    suspend fun hasAuthToken() = store.get(TokenKey) != null

    /**
     * The token for as long as it is observed. A store that cannot be read right now -- a desktop
     * OS vault that is down while secrets are stored under its keyset -- is waited out with a pause
     * that doubles with each failure in a row, from 1 s up to 30 s, and starts again once a read
     * succeeds, rather than thrown into the collector's scope. Nothing is emitted meanwhile: the
     * token is unknown, not gone, so the session is neither signed in nor signed out until it
     * reads.
     */
    fun observeAuthToken(): Flow<String?> = flow {
        var failuresInARow = 0
        emitAll(
            store
                .observe(TokenKey)
                .onEach { failuresInARow = 0 }
                .retryWhen { cause, _ ->
                    val unreadable = cause is SecretStoreException
                    if (unreadable) {
                        if (failuresInARow == 0) warnUnreadable(cause)
                        delay(unreadableRetryDelay(failuresInARow++))
                    }
                    unreadable
                }
        )
    }

    /**
     * One line per unreadable read: the token flow says it once per run of failures rather than at
     * every retry, the credential provider once per request it sends without a credential.
     */
    fun warnUnreadable(failure: Throwable) {
        logger.w {
            logEvent("credential_store_unreadable", "causeClass" to failure.causeClassName)
        }
    }

    private companion object {
        const val TokenKey = "token"
    }
}

private val FirstUnreadableRetry = 1.seconds
private val MaxUnreadableRetry = 30.seconds

/** 1 s, 2 s, 4 s ... capped at 30 s, for the [failuresBefore]+1-th failure in a row. */
internal fun unreadableRetryDelay(failuresBefore: Int): Duration =
    minOf(
        FirstUnreadableRetry * (1 shl failuresBefore.coerceAtMost(MaxDoublings)),
        MaxUnreadableRetry,
    )

private const val MaxDoublings = 5
