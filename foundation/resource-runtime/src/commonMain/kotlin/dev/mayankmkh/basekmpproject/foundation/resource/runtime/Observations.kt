package dev.mayankmkh.basekmpproject.foundation.resource.runtime

import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.toOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observes a durable [values] flow as resource observations for [key].
 *
 * Wraps [SyncCoordinator.observing] around [values] combined with the key's
 * [SyncCoordinator.status] so the appearance sync, the offline retry on reconnect and the status
 * collection all cover the same key, and applies the contract's [toOperation] mapping. The
 * Capability decides what `null` means before calling this: a collection that must tell "never
 * synchronized" from "empty" applies its durable marker to [values] first.
 */
public fun <Key : Any, T : Any> SyncCoordinator<Key>.observations(
    key: Key,
    values: Flow<T?>,
): Flow<ResourceObservation<T>> =
    observing(
        key,
        combine(values.distinctUntilChanged(), status(key)) { value, status ->
                ResourceObservation(value, status.toOperation(hasValue = value != null))
            }
            .distinctUntilChanged(),
    )
