package dev.mayankmkh.basekmpproject.foundation.runtime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class ApplicationRuntimeScopeTest {
    @Test
    fun cancellingOneChildDoesNotCancelItsSibling() = runTest {
        val runtime = runtimeScope()
        val first = runtime.childScope("first")
        val second = runtime.childScope("second")
        val firstWork = first.launch { awaitCancellation() }
        val secondWork = second.launch { awaitCancellation() }

        first.cancel()

        assertTrue(firstWork.isCancelled)
        assertTrue(secondWork.isActive)
        runtime.close()
    }

    @Test
    fun closeCancelsTheParentAndAllChildren() = runTest {
        val runtime = runtimeScope()
        val childWork = runtime.childScope("posts").launch { awaitCancellation() }

        runtime.close()

        assertFalse(runtime.scope.coroutineContext[Job]?.isActive ?: true)
        assertTrue(childWork.isCancelled)
    }

    private fun kotlinx.coroutines.test.TestScope.runtimeScope(): ApplicationRuntimeScope =
        ApplicationRuntimeScope(
            dispatcher = StandardTestDispatcher(testScheduler),
            exceptionHandler = CoroutineExceptionHandler { _, _ -> },
        )
}
