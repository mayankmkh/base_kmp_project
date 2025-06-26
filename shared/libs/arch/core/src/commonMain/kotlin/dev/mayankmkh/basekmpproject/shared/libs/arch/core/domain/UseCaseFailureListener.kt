package dev.mayankmkh.basekmpproject.shared.libs.arch.core.domain

interface UseCaseFailureListener {
    fun onFailure(throwable: Throwable, tag: String? = null, message: () -> String)
}
