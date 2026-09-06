package dev.mayankmkh.basekmpproject.foundation.presentation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.ParametersDefinition

/** Resolves a ViewModel under presentation identity plus its concrete state-owner type. */
@Composable
public inline fun <reified T : ViewModel> featureViewModel(
    instanceKey: FeatureInstanceKey,
    noinline parameters: ParametersDefinition? = null,
): T =
    koinViewModel(
        key = "${instanceKey.value}#${T::class.simpleName}",
        parameters = parameters,
    )
