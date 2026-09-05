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
                operation = ResourceOperation.Refreshing,
            )
    }
}

public sealed interface ResourceOperation {
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
 * `Refreshing` while a sync is in flight, then `Failed` with the last failure, then `Refreshing`
 * while there is no value to show, otherwise `Idle`. A clean ledger without a value therefore stays
 * `Refreshing` while the durable query catches up or until a later attempt confirms a result.
 */
public fun SyncStatus.toOperation(hasValue: Boolean): ResourceOperation =
    when {
        inFlight -> ResourceOperation.Refreshing
        lastFailure != null -> ResourceOperation.Failed(lastFailure)
        !hasValue && !hasSucceeded -> ResourceOperation.Refreshing
        else -> ResourceOperation.Idle
    }

public val ResourceObservation<*>.hasValue: Boolean
    get() = value != null

public val ResourceObservation<*>.isRefreshing: Boolean
    get() = operation is ResourceOperation.Refreshing

public val ResourceObservation<*>.failure: Problem?
    get() = (operation as? ResourceOperation.Failed)?.problem

/** Whether synchronization confirmed that this resource has no durable value. */
public val ResourceObservation<*>.isAbsent: Boolean
    get() = value == null && operation is ResourceOperation.Idle
