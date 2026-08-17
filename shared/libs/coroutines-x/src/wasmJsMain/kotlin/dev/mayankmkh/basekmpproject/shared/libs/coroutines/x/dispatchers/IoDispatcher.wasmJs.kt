package dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The browser runs Kotlin on the single JS event loop, where `Default`, `Main` and `Unconfined` all
 * dispatch to it. Nothing here blocks -- fetch and IndexedDB are already asynchronous -- so there
 * is no thread to keep free and nothing for a separate pool to do.
 */
internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
