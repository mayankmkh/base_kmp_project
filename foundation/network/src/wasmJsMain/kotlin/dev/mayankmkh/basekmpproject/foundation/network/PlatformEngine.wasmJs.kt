package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

internal actual val platformEngineFactory: HttpClientEngineFactory<*> = Js
