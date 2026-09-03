package dev.mayankmkh.basekmpproject.foundation.resource

/**
 * The outcome of one refresh attempt, for the caller's transient feedback (snackbar or toast).
 * Persistent status stays on the observation stream.
 */
public sealed interface RefreshOutcome {
    public data object Succeeded : RefreshOutcome

    public data class Failed(val problem: ResourceProblem) : RefreshOutcome
}
