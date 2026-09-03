package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual val platformEngineFactory: HttpClientEngineFactory<*> = Darwin
