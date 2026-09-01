package dev.mayankmkh.basekmpproject.shared.features.details.testing

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.shared.features.details.domain.DetailRepository
import dev.mayankmkh.basekmpproject.shared.features.details.domain.GetItemUseCase
import dev.mayankmkh.basekmpproject.shared.features.details.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.details.presentation.DetailsViewModel
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UseCaseFailureListener
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal val items = listOf(Item("1", "First", "First body"), Item("2", "Second", "Second body"))

internal class FakeDetailRepository(
    private val stored: List<Item> = items,
    private val failure: Throwable? = null,
) : DetailRepository {
    override fun getItem(id: String): Flow<Result<Item, Throwable>> = flow {
        // Throws rather than emitting an `Err`, which is how the real repository fails: the
        // network-bound resource lets the exception out and the use case is what converts it.
        failure?.let { throw it }
        emit(Ok(stored.single { it.id == id }))
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

internal fun detailsViewModel(
    dispatcher: CoroutineDispatcher,
    itemId: String = "1",
    repository: DetailRepository = FakeDetailRepository(),
    failureListener: UseCaseFailureListener = RecordingFailureListener(),
) =
    DetailsViewModel(
        itemId,
        GetItemUseCase(repository, testDispatchers(dispatcher), failureListener),
    )
