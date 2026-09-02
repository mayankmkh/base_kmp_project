package dev.mayankmkh.basekmpproject.capability.identity.api

import kotlin.jvm.JvmInline

@JvmInline public value class AuthToken(public val value: String)

public sealed interface SessionState {
    public data object Anonymous : SessionState

    public data object SignedIn : SessionState
}
