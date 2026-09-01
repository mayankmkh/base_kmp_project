package dev.mayankmkh.basekmpproject.shared.features.list.domain

import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UnitSuspendUseCase
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UseCaseFailureListener
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers

internal class RefreshItemsUseCase(
    private val listRepository: ListRepository,
    appDispatchers: AppDispatchers,
    failureListener: UseCaseFailureListener,
) : UnitSuspendUseCase<Unit>(appDispatchers.disk, failureListener) {
    override suspend fun execute(parameter: Unit) = listRepository.refresh()
}
