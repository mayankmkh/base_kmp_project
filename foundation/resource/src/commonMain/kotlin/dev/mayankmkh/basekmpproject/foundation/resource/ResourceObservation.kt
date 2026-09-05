package dev.mayankmkh.basekmpproject.foundation.resource

public data class ResourceObservation<T : Any>(
    val value: T?,
    val operation: ResourceOperation,
) {
    public companion object {
        /** The canonical not-yet-loaded observation. */
        public fun <T : Any> initial(): ResourceObservation<T> =
            ResourceObservation(
                value = null,
                operation = ResourceOperation.Unsynchronized,
            )
    }
}

public sealed interface ResourceOperation {
    /**
     * No synchronization has completed in this process yet; a durable value, if any, is shown as
     * is.
     */
    public data object Unsynchronized : ResourceOperation

    public data object Idle : ResourceOperation

    public data object Refreshing : ResourceOperation

    public data class Failed(val problem: Problem) : ResourceOperation
}

/** Process-local synchronization status for one resource key. */
public data class SyncStatus(
    val inFlight: Boolean,
    val lastFailure: Problem?,
    val hasSucceeded: Boolean,
)

/**
 * The contract's status mapping: the operation a durable value with this status is observed under.
 *
 * `Refreshing` while a sync is in flight, then `Failed` with the last failure, then
 * `Unsynchronized` until one has succeeded, otherwise `Idle`. The durable value never takes part:
 * whether there is something to show is the observation's other half.
 */
public fun SyncStatus.toOperation(): ResourceOperation =
    when {
        inFlight -> ResourceOperation.Refreshing
        lastFailure != null -> ResourceOperation.Failed(lastFailure)
        !hasSucceeded -> ResourceOperation.Unsynchronized
        else -> ResourceOperation.Idle
    }

public val ResourceObservation<*>.hasValue: Boolean
    get() = value != null

public val ResourceObservation<*>.isRefreshing: Boolean
    get() = operation is ResourceOperation.Refreshing

public val ResourceObservation<*>.failure: Problem?
    get() = (operation as? ResourceOperation.Failed)?.problem

/** Whether there is nothing to show yet and the resource may still produce a first value. */
public val ResourceObservation<*>.isInitialLoading: Boolean
    get() =
        value == null &&
            (operation is ResourceOperation.Unsynchronized ||
                operation is ResourceOperation.Refreshing)

/** Whether synchronization confirmed that this resource has no durable value. */
public val ResourceObservation<*>.isAbsent: Boolean
    get() = value == null && operation is ResourceOperation.Idle
