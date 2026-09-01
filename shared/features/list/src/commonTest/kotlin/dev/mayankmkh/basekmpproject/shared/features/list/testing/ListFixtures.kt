package dev.mayankmkh.basekmpproject.shared.features.list.testing

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.shared.features.list.domain.GetItemsUseCase
import dev.mayankmkh.basekmpproject.shared.features.list.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ListRepository
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ObserveReconnectsUseCase
import dev.mayankmkh.basekmpproject.shared.features.list.domain.RefreshItemsUseCase
import dev.mayankmkh.basekmpproject.shared.features.list.presentation.ListViewModel
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UseCaseFailureListener
import dev.mayankmkh.basekmpproject.shared.libs.connectivity.ConnectivityMonitor
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal val items = listOf(Item("1", "First", "First body"), Item("2", "Second", "Second body"))

internal class FakeListRepository(
    private val results: Flow<Result<Collection<Item>, Throwable>>,
    private val refreshFailure: Throwable? = null,
) : ListRepository {
    var refreshCount = 0
        private set

    override fun getItems() = results

    override suspend fun refresh() {
        refreshCount++
        refreshFailure?.let { throw it }
    }
}

internal class RecordingFailureListener : UseCaseFailureListener {
    val failures = mutableListOf<Throwable>()

    override fun onFailure(throwable: Throwable, tag: String?, message: () -> String) {
        failures += throwable
    }
}

/** Every dispatcher is the test one, so `runTest`'s scheduler drives the whole flow. */
internal fun testDispatchers(dispatcher: CoroutineDispatcher) =
    AppDispatchers(dispatcher, dispatcher, dispatcher, dispatcher, dispatcher, dispatcher)

internal fun listViewModel(
    dispatcher: CoroutineDispatcher,
    results: Flow<Result<Collection<Item>, Throwable>> = flowOf(Ok(items)),
    failureListener: UseCaseFailureListener = RecordingFailureListener(),
    // Both use cases share it, as they do in the running app: a refresh has to land in the same
    // place the read is observing.
    repository: ListRepository = FakeListRepository(results),
    // Online and staying that way, which the use case reads as no reconnection at all: a test that
    // does not care about connectivity gets no extra refreshes out of it.
    connectivityMonitor: ConnectivityMonitor = ConnectivityMonitor { flowOf(true) },
) =
    ListViewModel(
        GetItemsUseCase(repository, testDispatchers(dispatcher), failureListener),
        RefreshItemsUseCase(repository, testDispatchers(dispatcher), failureListener),
        ObserveReconnectsUseCase(
            connectivityMonitor,
            testDispatchers(dispatcher),
            failureListener,
        ),
    )
