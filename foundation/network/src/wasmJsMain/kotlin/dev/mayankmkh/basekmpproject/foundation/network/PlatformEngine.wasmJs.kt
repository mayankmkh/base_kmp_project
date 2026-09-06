package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

public actual fun createPlatformHttpClientEngine(): HttpClientEngine = Js.create()
