package dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapBoth
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

sealed class UiState<out T> {
    data object Initial : UiState<Nothing>()

    data object InProgress : UiState<Nothing>()

    data class Success<out T>(val data: T) : UiState<T>()

    data class Failure(val error: Throwable) : UiState<Nothing>()
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<Result<T, Throwable>>.mapLatestToUiStateFlow(): Flow<UiState<T>> = mapLatest {
    it.toUiState()
}

fun <T> Result<T, Throwable>.toUiState(): UiState<T> =
    mapBoth(success = { UiState.Success(it) }, failure = { UiState.Failure(it) })

@OptIn(ExperimentalContracts::class)
public inline infix fun <T, U> UiState<T>.map(transform: (T) -> U): UiState<U> {
    contract { callsInPlace(transform, InvocationKind.AT_MOST_ONCE) }

    return when (this) {
        is UiState.Failure -> this
        UiState.InProgress -> UiState.InProgress
        UiState.Initial -> UiState.Initial
        is UiState.Success -> UiState.Success(transform(data))
    }
}
