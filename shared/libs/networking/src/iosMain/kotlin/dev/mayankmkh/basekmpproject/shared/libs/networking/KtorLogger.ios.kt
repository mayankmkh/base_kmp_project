package dev.mayankmkh.basekmpproject.shared.libs.networking

import io.ktor.client.plugins.logging.Logger

internal actual fun ktorPlatformLogger(delegate: Logger): Logger = delegate
