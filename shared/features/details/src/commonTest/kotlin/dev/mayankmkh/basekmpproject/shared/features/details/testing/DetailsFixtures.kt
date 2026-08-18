package dev.mayankmkh.basekmpproject.shared.features.details.testing

import dev.mayankmkh.basekmpproject.shared.features.details.domain.DetailRepository
import dev.mayankmkh.basekmpproject.shared.features.details.domain.GetItemUseCase
import dev.mayankmkh.basekmpproject.shared.features.details.domain.Item
import dev.mayankmkh.basekmpproject.shared.features.details.presentation.DetailsViewModel
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UseCaseFailureListener
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher

internal val items = listOf(Item("1", "First", "First body"), Item("2", "Second", "Second body"))

internal class FakeDetailRepository(
    private val stored: List<Item> = items,
    private val failure: Throwable? = null,
) : DetailRepository {
    override suspend fun getItem(id: String): Item {
        if (failure != null) throw failure
        return stored.single { it.id == id }
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
