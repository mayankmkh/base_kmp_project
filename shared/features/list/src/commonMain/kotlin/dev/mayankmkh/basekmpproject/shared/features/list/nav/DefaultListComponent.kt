package dev.mayankmkh.basekmpproject.shared.features.list.nav

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.retainedInstance
import com.arkivanov.essenty.lifecycle.coroutines.repeatOnLifecycle
import dev.mayankmkh.basekmpproject.shared.features.list.presentation.ListViewModel
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.nav.mainCoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

internal class DefaultListComponent(
    componentContext: ComponentContext,
    private val onItemSelected: (item: String) -> Unit,
) : ListComponent(), ComponentContext by componentContext, KoinComponent {

    override val viewModel: ListViewModel = retainedInstance { get() }

    private val scope = mainCoroutineScope()

    init {
        scope.launch {
            lifecycle.repeatOnLifecycle { viewModel.eventsFlow.collect(::processEvent) }
        }
    }

    override fun processEvent(event: ListViewModel.Event) {
        when (event) {
            is ListViewModel.Event.ItemClicked -> onItemSelected(event.item)
        }
    }
}
