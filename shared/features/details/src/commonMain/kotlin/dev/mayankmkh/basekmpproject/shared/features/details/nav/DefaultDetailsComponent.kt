package dev.mayankmkh.basekmpproject.shared.features.details.nav

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.retainedInstance
import com.arkivanov.essenty.lifecycle.coroutines.repeatOnLifecycle
import dev.mayankmkh.basekmpproject.shared.features.details.presentation.DetailsViewModel
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.nav.mainCoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf

internal class DefaultDetailsComponent(
    componentContext: ComponentContext,
    itemId: String,
    private val onFinished: () -> Unit,
) : DetailsComponent(), ComponentContext by componentContext, KoinComponent {
    override val viewModel: DetailsViewModel = retainedInstance { get { parametersOf(itemId) } }
    private val scope = mainCoroutineScope()

    init {
        scope.launch {
            lifecycle.repeatOnLifecycle { viewModel.eventsFlow.collect(::processEvent) }
        }
    }

    override fun processEvent(event: DetailsViewModel.Event) {
        when (event) {
            DetailsViewModel.Event.Close -> onFinished()
        }
    }
}
