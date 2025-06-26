package dev.mayankmkh.basekmpproject.shared.features.details.di

import dev.mayankmkh.basekmpproject.shared.features.details.data.DetailRepositoryImpl
import dev.mayankmkh.basekmpproject.shared.features.details.domain.DetailRepository
import dev.mayankmkh.basekmpproject.shared.features.details.domain.GetItemUseCase
import dev.mayankmkh.basekmpproject.shared.features.details.presentation.DetailsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val detailsFeatureModule = module {
    factoryOf(::DetailRepositoryImpl) bind DetailRepository::class
    factoryOf(::GetItemUseCase)
    factoryOf(::DetailsViewModel)
}
