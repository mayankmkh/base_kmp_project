package dev.mayankmkh.basekmpproject.shared.features.details.domain

import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.FlowUseCase
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UseCaseFailureListener
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers

internal class GetItemUseCase(
    private val detailRepository: DetailRepository,
    appDispatchers: AppDispatchers,
    failureListener: UseCaseFailureListener,
) : FlowUseCase<GetItemUseCase.Param, Item>(appDispatchers.disk, failureListener) {
    override fun execute(parameters: Param) = detailRepository.getItem(parameters.id)

    data class Param(val id: String)
}
