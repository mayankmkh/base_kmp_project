package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

public actual fun createPlatformHttpClientEngine(): HttpClientEngine = OkHttp.create()
