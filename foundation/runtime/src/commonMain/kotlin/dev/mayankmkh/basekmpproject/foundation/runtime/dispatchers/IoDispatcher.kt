package dev.mayankmkh.basekmpproject.foundation.runtime.dispatchers

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The dispatcher for work that blocks a thread rather than burning it -- disk and network.
 *
 * `Dispatchers.IO` would say this in one line everywhere but the browser, which has one thread and
 * no blocking primitives to schedule around, so kotlinx-coroutines does not declare it there.
 */
internal expect val ioDispatcher: CoroutineDispatcher
