package dev.mayankmkh.basekmpproject.shared.libs.coroutines.x.dispatchers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
