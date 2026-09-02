package dev.mayankmkh.basekmpproject.foundation.network

import io.ktor.client.plugins.logging.Logger

internal expect fun ktorPlatformLogger(delegate: Logger): Logger
