package dev.mayankmkh.basekmpproject.app.shared.config

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.logging.Logger as KtorLogger

/**
 * Points Ktor's request logging at the app's logger.
 *
 * Ktor's `Logger` and Kermit's share a name, hence the import alias; the app has one logging
 * destination and this is what keeps HTTP traffic going to it rather than to stdout. Like every
 * other consumer, it tags the logger it is given with its own name.
 */
internal class KermitKtorLogger(logger: Logger) : KtorLogger {
    private val logger = logger.withTag("HTTP")

    override fun log(message: String) {
        logger.d { message }
    }
}
