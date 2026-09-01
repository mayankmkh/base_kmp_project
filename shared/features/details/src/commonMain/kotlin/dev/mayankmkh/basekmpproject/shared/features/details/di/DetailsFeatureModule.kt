package dev.mayankmkh.basekmpproject.shared.features.details.di

import dev.mayankmkh.basekmpproject.shared.features.details.data.DetailRepositoryImpl
import dev.mayankmkh.basekmpproject.shared.features.details.domain.DetailRepository
import dev.mayankmkh.basekmpproject.shared.features.details.domain.GetItemUseCase
import dev.mayankmkh.basekmpproject.shared.features.details.presentation.DetailsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val detailsFeatureModule = module {
    // `single` for the same reason as the list's: it is the seam between the cache and the
    // network, and both screens are reading the one cache.
    singleOf(::DetailRepositoryImpl) bind DetailRepository::class
    factoryOf(::GetItemUseCase)
    viewModelOf(::DetailsViewModel)
}
