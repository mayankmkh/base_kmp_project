package dev.mayankmkh.basekmpproject.shared.libs.arch.core.data

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transform
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import org.mobilenativefoundation.store.store5.doThrow

/** Keeps Store's loading protocol inside the data layer while preserving the domain Result API. */
fun <Key : Any, Output : Any> Store<Key, Output>.streamAsResult(
    request: StoreReadRequest<Key>
): Flow<Result<Output, Throwable>> =
    stream(request)
        .transform { response ->
            when (response) {
                is StoreReadResponse.Data -> emit(Ok(response.value))
                is StoreReadResponse.Error -> emit(Err(response.doThrow()))
                is StoreReadResponse.Initial,
                is StoreReadResponse.Loading,
                is StoreReadResponse.NoNewData -> Unit
            }
        }
        .distinctUntilChanged()
