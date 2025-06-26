package dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

abstract class UnitSuspendUseCase<out R>(
    coroutineDispatcher: CoroutineDispatcher,
    failureListener: UseCaseFailureListener,
) : SuspendUseCase<Unit, R>(coroutineDispatcher, failureListener) {
    suspend operator fun invoke() = invoke(Unit)
}

abstract class SuspendUseCase<in P, out R>(
    coroutineDispatcher: CoroutineDispatcher,
    failureListener: UseCaseFailureListener,
) : BaseSuspendUseCase<P, R, Throwable>(coroutineDispatcher, failureListener) {
    final override fun Throwable.toError() = this
}

abstract class ResultSuspendUseCase<in P, out R, out E>(
    coroutineDispatcher: CoroutineDispatcher,
    failureListener: UseCaseFailureListener,
) : BaseSuspendUseCase<P, R, E>(coroutineDispatcher, failureListener) {
    abstract override suspend fun executeWith(parameter: P): Result<R, E>

    override suspend fun execute(parameter: P): R {
        error("This should not be called")
    }
}

abstract class BaseSuspendUseCase<in P, out R, out E>
internal constructor(
    private val coroutineDispatcher: CoroutineDispatcher,
    private val failureListener: UseCaseFailureListener,
) {
    suspend operator fun invoke(parameter: P): Result<R, E> {
        return try {
            withContext(coroutineDispatcher) {
                executeWith(parameter).onFailure { onFailure(UseCaseException("$it")) }
            }
        } catch (e: CancellationException) {
            @Suppress("RethrowCaughtException") throw e
        } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
            onFailure(throwable)
            Err(throwable.toError())
        }
    }

    private fun onFailure(throwable: Throwable) {
        failureListener.onFailure(throwable, tag = this::class.simpleName!!) { "onFailure" }
    }

    open suspend fun executeWith(parameter: P): Result<R, E> = Ok(execute(parameter))

    @Throws(RuntimeException::class) protected abstract suspend fun execute(parameter: P): R

    protected abstract fun Throwable.toError(): E
}

private class UseCaseException(message: String) : Exception(message)
