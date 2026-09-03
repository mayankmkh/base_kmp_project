package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

internal actual val platformEngineFactory: HttpClientEngineFactory<*> = OkHttp
