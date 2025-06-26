package dev.mayankmkh.basekmpproject.shared.features.list.di

import dev.mayankmkh.basekmpproject.shared.features.list.data.ListRepositoryImpl
import dev.mayankmkh.basekmpproject.shared.features.list.domain.GetItemsUseCase
import dev.mayankmkh.basekmpproject.shared.features.list.domain.ListRepository
import dev.mayankmkh.basekmpproject.shared.features.list.presentation.ListViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val listFeatureModule = module {
    factoryOf(::ListRepositoryImpl) bind ListRepository::class
    factoryOf(::GetItemsUseCase)
    factoryOf(::ListViewModel)
}
