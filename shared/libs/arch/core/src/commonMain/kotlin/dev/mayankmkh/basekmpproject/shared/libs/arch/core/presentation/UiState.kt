package dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapBoth
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

sealed interface UiState<out T> {
    data object Initial : UiState<Nothing>

    data object InProgress : UiState<Nothing>

    data class Success<out T>(val data: T) : UiState<T>

    data class Failure(val error: Throwable) : UiState<Nothing>
}

/**
 * How long the upstream stays collected after the last subscriber goes away.
 *
 * Long enough to survive a configuration change or a there-and-back navigation, so returning to a
 * screen shows what it had instead of re-fetching; short enough that a screen left behind stops
 * observing its database.
 */
private const val SubscriptionTimeoutMillis = 5_000L

/**
 * The state flow a screen observes, from the flow of results behind it.
 *
 * Bundles the three decisions every view model was otherwise repeating: map results to [UiState],
 * report progress ahead of the first one, and share the upstream on
 * [SharingStarted.WhileSubscribed]. Here rather than per view model so the timeout is one number
 * for the app instead of a literal per screen.
 */
fun <T> Flow<Result<T, Throwable>>.stateInUiState(scope: CoroutineScope): StateFlow<UiState<T>> =
    mapLatestToUiStateFlow()
        .onStart { emit(UiState.InProgress) }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(SubscriptionTimeoutMillis),
            initialValue = UiState.Initial,
        )

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<Result<T, Throwable>>.mapLatestToUiStateFlow(): Flow<UiState<T>> = mapLatest {
    it.toUiState()
}

fun <T> Result<T, Throwable>.toUiState(): UiState<T> =
    mapBoth(success = { UiState.Success(it) }, failure = { UiState.Failure(it) })

@OptIn(ExperimentalContracts::class)
inline infix fun <T, U> UiState<T>.map(transform: (T) -> U): UiState<U> {
    contract { callsInPlace(transform, InvocationKind.AT_MOST_ONCE) }

    return when (this) {
        is UiState.Failure -> this
        UiState.InProgress -> UiState.InProgress
        UiState.Initial -> UiState.Initial
        is UiState.Success -> UiState.Success(transform(data))
    }
}
