package dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Production code takes this as a constructor parameter and calls [invoke] to get the real one. The
 * constructor stays public so a test can hand the same code a `TestDispatcher` instead.
 */
class AppDispatchers(
    val disk: CoroutineDispatcher,
    val network: CoroutineDispatcher,
    val main: CoroutineDispatcher,
    val cpu: CoroutineDispatcher,
    val unconfined: CoroutineDispatcher,
    val mainImmediate: CoroutineDispatcher,
) {
    companion object {
        // Lazy because `Dispatchers.Main.immediate` throws where no main dispatcher exists -- a
        // plain JVM test. Eager, that would break tests that only ever build their own instance.
        private val instance by lazy {
            AppDispatchers(
                ioDispatcher,
                ioDispatcher,
                Dispatchers.Main,
                Dispatchers.Default,
                Dispatchers.Unconfined,
                Dispatchers.Main.immediate,
            )
        }

        operator fun invoke() = instance
    }
}
