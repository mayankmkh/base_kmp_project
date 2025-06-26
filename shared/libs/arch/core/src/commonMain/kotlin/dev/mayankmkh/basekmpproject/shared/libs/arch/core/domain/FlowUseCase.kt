package dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach

abstract class UnitFlowUseCase<out R>(
    coroutineDispatcher: CoroutineDispatcher,
    failureListener: UseCaseFailureListener,
) : FlowUseCase<Unit, R>(coroutineDispatcher, failureListener) {
    operator fun invoke() = invoke(Unit)

    final override fun execute(parameters: Unit) = execute()

    abstract fun execute(): Flow<Result<R, Throwable>>
}

abstract class FlowUseCase<in P, out R>(
    coroutineDispatcher: CoroutineDispatcher,
    failureListener: UseCaseFailureListener,
) : BaseFlowUseCase<P, R, Throwable>(coroutineDispatcher, failureListener) {
    final override fun Throwable.toError() = this

    final override fun Throwable.toThrowable(): Throwable = this
}

abstract class BaseFlowUseCase<in P, out R, E>
internal constructor(
    private val coroutineDispatcher: CoroutineDispatcher,
    private val failureListener: UseCaseFailureListener,
) {
    operator fun invoke(parameters: P): Flow<Result<R, E>> {
        return execute(parameters)
            .onEach { result ->
                result.onFailure { error ->
                    val throwable = error.toThrowable()
                    if (throwable is CancellationException) {
                        throw throwable
                    }
                    val tag = this@BaseFlowUseCase::class.simpleName
                    failureListener.onFailure(throwable, tag = tag) { "onFailure" }
                }
            }
            .catch { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                val tag = this@BaseFlowUseCase::class.simpleName
                failureListener.onFailure(throwable, tag = tag) { "onFailure" }
                emit(Err(throwable.toError()))
            }
            .flowOn(coroutineDispatcher)
    }

    protected abstract fun execute(parameters: P): Flow<Result<R, E>>

    protected abstract fun Throwable.toError(): E

    protected abstract fun E.toThrowable(): Throwable
}
