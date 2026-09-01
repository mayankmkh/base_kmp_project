package dev.mayankmkh.basekmpproject.shared.app.config

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.logging.Logger as KtorLogger

/**
 * Points Ktor's request logging at the app's logger.
 *
 * Ktor's `Logger` and Kermit's share a name, hence the import alias; the app has one logging
 * destination and this is what keeps HTTP traffic going to it rather than to stdout.
 */
internal class KermitKtorLogger(private val logger: Logger) : KtorLogger {
    override fun log(message: String) {
        logger.d(tag = TAG) { message }
    }

    private companion object {
        const val TAG = "HTTP"
    }
}
