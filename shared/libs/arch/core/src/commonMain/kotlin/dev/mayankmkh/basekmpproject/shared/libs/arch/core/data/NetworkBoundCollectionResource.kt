package dev.mayankmkh.basekmpproject.shared.libs.arch.core.data

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers

abstract class NetworkBoundCollectionResource<RequestType : Any, ResultType : Any>(
    dispatchers: AppDispatchers,
    failureListener: OnFailureListener,
) : NetworkBoundResource<RequestType, Collection<ResultType>>(dispatchers, failureListener) {
    override fun canEmitInitialDbValue(data: Collection<ResultType>): Boolean = data.isNotEmpty()

    override fun mapDataToResultOnNetworkFailure(
        data: Collection<ResultType>?,
        throwable: Throwable,
    ): Result<Collection<ResultType>, Throwable> {
        return if (!data.isNullOrEmpty()) Ok(data) else Err(throwable)
    }
}
