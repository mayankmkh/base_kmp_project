package dev.mayankmkh.basekmpproject.shared.app.config

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain.UseCaseFailureListener

internal class AppUseCaseFailureListener(private val logger: Logger) : UseCaseFailureListener {
    override fun onFailure(throwable: Throwable, tag: String?, message: () -> String) {
        if (tag != null) {
            logger.w(throwable, tag, message)
        } else {
            logger.w(throwable, message = message)
        }
    }
}
