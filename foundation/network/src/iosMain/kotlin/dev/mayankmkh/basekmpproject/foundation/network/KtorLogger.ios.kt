package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.plugins.logging.Logger

internal actual fun ktorPlatformLogger(delegate: Logger): Logger = delegate
