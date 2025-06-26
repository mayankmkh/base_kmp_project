package dev.mayankmkh.basekmpproject.shared.app.config

import co.touchlab.kermit.Logger
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.data.NetworkBoundResource

internal class NBRFailureListener(private val logger: Logger) :
    NetworkBoundResource.OnFailureListener {
    override fun onFetchFailed(throwable: Throwable) {
        logger.w(throwable) { "onFetchFailed" }
    }
}
