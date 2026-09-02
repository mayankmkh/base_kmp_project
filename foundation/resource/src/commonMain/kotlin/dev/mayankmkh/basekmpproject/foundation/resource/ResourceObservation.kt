package dev.mayankmkh.basekmpproject.foundation.resource

public data class ResourceObservation<T : Any>(
    val value: T?,
    val freshness: ResourceFreshness,
    val operation: ResourceOperation,
) {
    init {
        if (value == null) {
            require(freshness == ResourceFreshness.UNKNOWN) {
                "An observation without a value must have UNKNOWN freshness"
            }
            require(operation !is ResourceOperation.Idle) {
                "An observation without a value must be refreshing or failed"
            }
        } else {
            require(freshness != ResourceFreshness.UNKNOWN) {
                "An observation with a value must have FRESH or STALE freshness"
            }
        }
    }

    public companion object {
        /** The canonical not-yet-loaded observation. */
        public fun <T : Any> initial(): ResourceObservation<T> =
            ResourceObservation(
                value = null,
                freshness = ResourceFreshness.UNKNOWN,
                operation = ResourceOperation.Refreshing,
            )
    }
}

public enum class ResourceFreshness {
    UNKNOWN,
    FRESH,
    STALE,
}

public sealed interface ResourceOperation {
    public data object Idle : ResourceOperation

    public data object Refreshing : ResourceOperation

    public data class Failed(val problem: ResourceProblem) : ResourceOperation
}

public data class ResourceProblem(
    val category: ResourceProblemCategory,
    val retryable: Boolean,
    val message: String? = null,
)

public enum class ResourceProblemCategory {
    OFFLINE,
    TEMPORARY,
    ACCESS,
    PERMANENT,
    UNKNOWN,
}

public val ResourceObservation<*>.hasValue: Boolean
    get() = value != null

public val ResourceObservation<*>.isRefreshing: Boolean
    get() = operation is ResourceOperation.Refreshing

public val ResourceObservation<*>.failure: ResourceProblem?
    get() = (operation as? ResourceOperation.Failed)?.problem
