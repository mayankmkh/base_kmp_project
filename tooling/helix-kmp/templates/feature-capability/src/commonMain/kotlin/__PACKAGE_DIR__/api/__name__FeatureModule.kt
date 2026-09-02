package __PACKAGE__.api

import __PACKAGE__.__NAME__ViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** One Koin module per Feature. The app's composition root is the only place that loads it. */
public val __name__FeatureModule: Module = module {
    viewModel { parameters ->
        __NAME__ViewModel(
            id = parameters.get(),
            instanceKey = parameters.get(),
            queries = get(),
            commands = get(),
        )
    }
}
