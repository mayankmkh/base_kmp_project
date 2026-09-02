package dev.mayankmkh.basekmpproject.foundation.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Application-owned parent for work that may outlive a screen or composition.
 *
 * Capability implementations must create a named [childScope] and close their own child when the
 * Capability lifetime ends. The app closes this parent at runtime shutdown.
 */
public class ApplicationRuntimeScope(
    dispatcher: CoroutineDispatcher,
    exceptionHandler: CoroutineExceptionHandler,
) : AutoCloseable {
    private val job = SupervisorJob()
    private val runtimeContext = dispatcher + exceptionHandler

    public val scope: CoroutineScope =
        CoroutineScope(job + runtimeContext + CoroutineName("application-runtime"))

    /** Creates an isolated Capability scope whose failures do not cancel sibling scopes. */
    public fun childScope(name: String): CoroutineScope {
        require(name.isNotBlank()) { "A runtime child scope requires a non-blank capability name" }
        return CoroutineScope(
            SupervisorJob(job) + runtimeContext + CoroutineName("application-runtime/$name")
        )
    }

    override fun close(): Unit = scope.cancel()
}
