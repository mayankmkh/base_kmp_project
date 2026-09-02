package dev.mayankmkh.basekmpproject.foundation.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/** Collects one presentation event stream only while its host lifecycle is started. */
@Composable
public fun <T> CollectWhileStarted(flow: Flow<T>, onEach: suspend (T) -> Unit) {
    val currentOnEach by rememberUpdatedState(onEach)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(flow, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { currentOnEach(it) }
        }
    }
}
