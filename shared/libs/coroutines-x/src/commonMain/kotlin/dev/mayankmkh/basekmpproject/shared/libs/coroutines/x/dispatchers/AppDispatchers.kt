package dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class AppDispatchers
private constructor(
    val disk: CoroutineDispatcher,
    val network: CoroutineDispatcher,
    val main: CoroutineDispatcher,
    val cpu: CoroutineDispatcher,
    val unconfined: CoroutineDispatcher,
    val mainImmediate: CoroutineDispatcher,
) {
    companion object {
        private val instance =
            AppDispatchers(
                ioDispatcher,
                ioDispatcher,
                Dispatchers.Main,
                Dispatchers.Default,
                Dispatchers.Unconfined,
                Dispatchers.Main.immediate,
            )

        operator fun invoke() = instance
    }
}
