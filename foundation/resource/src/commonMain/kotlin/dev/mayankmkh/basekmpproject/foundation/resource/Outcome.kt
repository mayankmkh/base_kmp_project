package dev.mayankmkh.basekmpproject.foundation.resource

/** The result of a command that either completed with a decision or failed to reach one. */
public sealed interface Outcome<out T> {
    public data class Completed<T>(val value: T) : Outcome<T>

    public data class Failed(val problem: Problem) : Outcome<Nothing>
}

/** Product-neutral classification of a command or resource failure. */
public data class Problem(val kind: ProblemKind, val reference: String? = null) {
    /** A retry without changing anything may succeed. */
    public val retryable: Boolean
        get() =
            kind == ProblemKind.OFFLINE || kind == ProblemKind.TIMEOUT || kind == ProblemKind.SERVER

    /** The request may have reached the server; a command's effect is unknown. */
    public val mayHaveApplied: Boolean
        get() = kind == ProblemKind.TIMEOUT
}

public enum class ProblemKind {
    OFFLINE,
    TIMEOUT,
    SERVER,
    FORBIDDEN,
    UNEXPECTED,
}

/**
 * One rejected input. [field] is null when the violation applies to the whole submission rather
 * than to one input; [message] is display-ready server text when the server supplied it.
 */
public data class Violation<out F>(val field: F?, val code: String, val message: String? = null)
