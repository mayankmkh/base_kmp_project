package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

public actual fun createPlatformHttpClientEngine(): HttpClientEngine = Darwin.create()
