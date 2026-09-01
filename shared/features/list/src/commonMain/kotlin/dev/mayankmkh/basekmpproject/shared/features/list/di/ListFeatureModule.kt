package dev.mayankmkh.basekmpproject.shared.features.list.di

import dev.mayankmkh.basekmpproject.shared.features.list.data.ListRepositoryImpl
import dev.mayankmkh.basekmpproject.shared.features.list.domain.GetItemsUseCase
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ListRepository
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ObserveReconnectsUseCase
import dev.mayankmkh.basekmpproject.shared.features.list.domain.RefreshItemsUseCase
import dev.mayankmkh.basekmpproject.shared.features.list.presentation.ListViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val listFeatureModule = module {
    // `single`: the repository is what pairs the cache with the network, and a refresh has to
    // land in the same cache the read is observing. The use cases below hold no state, so they
    // stay factories.
    singleOf(::ListRepositoryImpl) bind ListRepository::class
    factoryOf(::GetItemsUseCase)
    factoryOf(::RefreshItemsUseCase)
    factoryOf(::ObserveReconnectsUseCase)
    viewModelOf(::ListViewModel)
}
