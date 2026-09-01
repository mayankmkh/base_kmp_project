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

    // Fetch only when there is nothing to show. Defaulted rather than left abstract so it cannot
    // fall out of step with the two rules above, which already read an empty collection as "no
    // cache". A subclass wanting a staleness window still overrides it.
    override suspend fun shouldFetch(data: Collection<ResultType>?): Boolean = data.isNullOrEmpty()

    override fun mapDataToResultOnNetworkFailure(
        data: Collection<ResultType>?,
        throwable: Throwable,
    ): Result<Collection<ResultType>, Throwable> {
        return if (!data.isNullOrEmpty()) Ok(data) else Err(throwable)
    }
}
