package dev.mayankmkh.basekmpproject.shared.libs.networking

import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.MessageLengthLimitingLogger

internal actual fun ktorPlatformLogger(delegate: Logger): Logger =
    MessageLengthLimitingLogger(delegate = delegate)
