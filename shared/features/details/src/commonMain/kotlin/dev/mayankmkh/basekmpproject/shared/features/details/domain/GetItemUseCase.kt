package dev.mayankmkh.basekmpproject.shared.features.details.domain

import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.SuspendUseCase
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UseCaseFailureListener
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers

internal class GetItemUseCase(
    private val detailRepository: DetailRepository,
    appDispatchers: AppDispatchers,
    failureListener: UseCaseFailureListener,
) : SuspendUseCase<GetItemUseCase.Param, Item>(appDispatchers.disk, failureListener) {
    override suspend fun execute(parameter: Param) = detailRepository.getItem(parameter.id)

    data class Param(val id: String)
}
