package dev.mayankmkh.basekmpproject.shared.libs.networking

import io.ktor.client.plugins.logging.Logger

internal expect fun ktorPlatformLogger(delegate: Logger): Logger
