package dev.mayankmkh.basekmpproject.shared.features.list.domain

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

internal interface ListRepository {
    fun getItems(): Flow<Result<Collection<Item>, Throwable>>
}
