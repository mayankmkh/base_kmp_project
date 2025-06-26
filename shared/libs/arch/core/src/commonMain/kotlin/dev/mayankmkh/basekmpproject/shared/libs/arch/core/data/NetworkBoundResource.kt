package dev.mayankmkh.basekmpproject.shared.libs.arch.core.data

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.toResultOr
import dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions")
abstract class NetworkBoundResource<RequestType : Any, ResultType : Any>(
    private val dispatchers: AppDispatchers,
    private val failureListener: OnFailureListener,
) {
    fun asFlow() =
        flow {
                val dbFlow = loadFromDb()
                val first = dbFlow.first()
                if (shouldFetch(first)) {
                    emitInitialDbValue(first)
                    fetchFromNetwork(dbFlow)
                } else {
                    emitAllFromDb(dbFlow)
                }
            }
            .distinctUntilChanged()
            .flowOn(dispatchers.disk)

    private suspend fun ResultFlowCollector<ResultType>.emitInitialDbValue(first: ResultType?) {
        if (first != null && canEmitInitialDbValue(first)) {
            emit(Ok(first))
        }
    }

    private suspend fun ResultFlowCollector<ResultType>.fetchFromNetwork(
        dbFlow: Flow<ResultType?>
    ) {
        runCatching { withContext(dispatchers.network) { loadFromNetwork() } }
            .onSuccess { apiResponse ->
                val processedResponse =
                    withContext(dispatchers.cpu) { processResponse(apiResponse) }
                saveCallResult(processedResponse)
                emitAllFromDb(dbFlow)
            }
            .onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                } else {
                    onFetchFailed(throwable)
                    emitAll(
                        dbFlow.map {
                            mapDataToResultOnNetworkFailure(
                                it,
                                NoDataException(
                                    "On network failure: ${NoDataException.NO_SUCH_DATA}"
                                ),
                            )
                        }
                    )
                }
            }
    }

    private suspend fun ResultFlowCollector<ResultType>.emitAllFromDb(dbFlow: Flow<ResultType?>) {
        emitAll(dbFlow.map { mapDataToResult(it) })
    }

    protected open fun mapDataToResult(data: ResultType?): Result<ResultType, Throwable> {
        return data.toResultOr { NoDataException() }
    }

    protected open fun mapDataToResultOnNetworkFailure(
        data: ResultType?,
        throwable: Throwable,
    ): Result<ResultType, Throwable> {
        return data.toResultOr { throwable }
    }

    protected open fun onFetchFailed(throwable: Throwable) {
        failureListener.onFetchFailed(throwable)
    }

    protected open fun canEmitInitialDbValue(data: ResultType): Boolean = true

    protected abstract suspend fun processResponse(response: RequestType): ResultType

    protected abstract suspend fun saveCallResult(item: ResultType)

    protected abstract suspend fun shouldFetch(data: ResultType?): Boolean

    protected abstract fun loadFromDb(): Flow<ResultType?>

    protected abstract suspend fun loadFromNetwork(): RequestType

    interface OnFailureListener {
        fun onFetchFailed(throwable: Throwable)
    }
}

private typealias ResultFlowCollector<ResultType> = FlowCollector<Result<ResultType, Throwable>>

suspend fun <RequestType : Any, ResultType : Any> NetworkBoundResource<RequestType, ResultType>
    .executeAsOne() = asFlow().first().getOrElse { throw it }
