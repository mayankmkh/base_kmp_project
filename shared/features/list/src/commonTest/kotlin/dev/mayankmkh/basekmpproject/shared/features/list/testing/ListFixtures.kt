package dev.mayankmkh.basekmpproject.shared.features.list.testing

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.shared.features.list.domain.GetItemsUseCase
import dev.mayankmkh.basekmpproject.shared.features.list.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ListRepository
import dev.mayankmkh.basekmpproject.shared.features.list.presentation.ListViewModel
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UseCaseFailureListener
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal val items = listOf(Item("1", "First", "First body"), Item("2", "Second", "Second body"))

internal class FakeListRepository(private val results: Flow<Result<Collection<Item>, Throwable>>) :
    ListRepository {
    override fun getItems() = results
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
) =
    ListViewModel(
        GetItemsUseCase(FakeListRepository(results), testDispatchers(dispatcher), failureListener)
    )
