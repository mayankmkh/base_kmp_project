package dev.mayankmkh.basekmpproject.foundation.resource

/** Semantics are defined in the architecture source of truth §13.7 (RefreshQos semantics). */
public enum class RefreshPriority {
    CRITICAL_VISIBLE,
    VISIBLE,
    BACKGROUND,
    PREFETCH,
}

/** Semantics are defined in the architecture source of truth §13.7 (RefreshQos semantics). */
public enum class NetworkPreference {
    ANY_NETWORK,
    UNMETERED_PREFERRED,
    UNMETERED_ONLY,
}

public data class RefreshQos(
    val priority: RefreshPriority,
    val network: NetworkPreference,
) {
    public companion object {
        /** A user-visible refresh that may use the currently available network. */
        public fun visible(): RefreshQos =
            RefreshQos(
                priority = RefreshPriority.VISIBLE,
                network = NetworkPreference.ANY_NETWORK,
            )

        /** Best-effort maintenance work that should not compete with visible interaction. */
        public fun background(): RefreshQos =
            RefreshQos(
                priority = RefreshPriority.BACKGROUND,
                network = NetworkPreference.ANY_NETWORK,
            )
    }
}
