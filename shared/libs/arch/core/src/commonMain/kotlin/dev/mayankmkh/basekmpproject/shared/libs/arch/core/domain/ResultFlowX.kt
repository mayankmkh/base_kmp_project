package dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

inline fun <V, E, U> Flow<Result<V, E>>.mapResultOk(
    crossinline transform: (V) -> U
): Flow<Result<U, E>> = map { result -> result.map(transform) }
