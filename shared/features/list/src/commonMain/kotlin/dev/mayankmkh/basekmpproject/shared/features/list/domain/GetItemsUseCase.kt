package dev.mayankmkh.basekmpproject.shared.features.list.domain

import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UnitFlowUseCase
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UseCaseFailureListener
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.mapResultOk
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers

internal class GetItemsUseCase(
    private val listRepository: ListRepository,
    appDispatchers: AppDispatchers,
    failureListener: UseCaseFailureListener,
) : UnitFlowUseCase<ItemsModel>(appDispatchers.disk, failureListener) {
    override fun execute() = listRepository.getItems().mapResultOk { ItemsModel(it.toList()) }
}
