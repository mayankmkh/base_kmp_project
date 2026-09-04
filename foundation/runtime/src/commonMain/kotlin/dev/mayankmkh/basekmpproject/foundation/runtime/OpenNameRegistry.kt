package dev.mayankmkh.basekmpproject.foundation.runtime

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Refuses to hand out a process-lifetime resource twice under the same name.
 *
 * Stores backed by DataStore or the Keychain are opened once during graph creation and never
 * closed. A second open of the same name is a wiring mistake; failing here names the store, where
 * the platform would otherwise fail later with a path-only exception or two instances that silently
 * disagree. Registration uses compare-and-set so the guarantee holds off the graph-creation thread.
 */
@OptIn(ExperimentalAtomicApi::class)
public class OpenNameRegistry(private val resource: String) {
    private val names = AtomicReference<Set<String>>(emptySet())

    /** Records [name] or throws [IllegalStateException] if it was registered before. */
    public fun register(name: String) {
        while (true) {
            val current = names.load()
            check(name !in current) { "A $resource '$name' is already open." }
            if (names.compareAndSet(current, current + name)) return
        }
    }
}
