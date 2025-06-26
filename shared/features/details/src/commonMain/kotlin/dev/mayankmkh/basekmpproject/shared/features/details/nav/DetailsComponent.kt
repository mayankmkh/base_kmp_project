package dev.mayankmkh.basekmpproject.shared.features.details.nav

import com.arkivanov.decompose.ComponentContext
import dev.mayankmkh.basekmpproject.shared.features.details.presentation.DetailsViewModel
import dev.mayankmkh.basekmpproject.shared.features.details.presentation.DetailsViewModel.Event

abstract class DetailsComponent {
    internal abstract val viewModel: DetailsViewModel

    internal abstract fun processEvent(event: Event)

    companion object {
        operator fun invoke(
            componentContext: ComponentContext,
            itemId: String,
            onFinished: () -> Unit,
        ): DetailsComponent = DefaultDetailsComponent(componentContext, itemId, onFinished)
    }
}
