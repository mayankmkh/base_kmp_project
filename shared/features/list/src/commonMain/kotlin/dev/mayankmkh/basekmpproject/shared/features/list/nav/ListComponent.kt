package dev.mayankmkh.basekmpproject.shared.features.list.nav

import com.arkivanov.decompose.ComponentContext
import dev.mayankmkh.basekmpproject.shared.features.list.presentation.ListViewModel
import dev.mayankmkh.basekmpproject.shared.features.list.presentation.ListViewModel.Event

abstract class ListComponent {

    internal abstract val viewModel: ListViewModel

    internal abstract fun processEvent(event: Event)

    companion object {
        operator fun invoke(
            componentContext: ComponentContext,
            onItemSelected: (id: String) -> Unit,
        ): ListComponent = DefaultListComponent(componentContext, onItemSelected)
    }
}
