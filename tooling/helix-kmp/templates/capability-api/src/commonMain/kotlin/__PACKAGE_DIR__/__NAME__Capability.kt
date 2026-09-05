package __PACKAGE__

import dev.mayankmkh.basekmpproject.foundation.resource.Outcome
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshQos
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceObservation
import dev.mayankmkh.basekmpproject.foundation.resource.Violation
import kotlinx.coroutines.flow.Flow

// Queries observe and never mutate. One Capability exposes one grouped Queries interface rather
// than a per-screen method bag. A remotely synchronized read is a `ResourceObservation`: the
// durable value plus the current operation (idle, refreshing, or failed). Collecting it is what
// tells the Capability that the value is on screen, so the Capability can synchronize on
// appearance and on reconnect. A purely local read may stay a plain `Flow<T>`.
/** The __name__ Capability's read surface. */
public interface __NAME__Queries {
    public fun observeAll(): Flow<ResourceObservation<List<__NAME__Record>>>

    public fun observe(id: __NAME__Id): Flow<ResourceObservation<__NAME__Record>>
}

// A refresh Command returns the outcome of one attempt for transient caller feedback. Observers
// above remain the source of truth for persistent state, and a failure never clears a value. The
// QoS is domain-blind: it says how urgent the work is, never what the product means by fresh.
/** The __name__ Capability's explicit synchronization intents. */
public interface __NAME__Commands {
    public suspend fun refresh(qos: RefreshQos = RefreshQos.visible()): Outcome<Unit>

    public suspend fun create(label: String): Outcome<Create__NAME__Result>
}

public sealed interface Create__NAME__Result {
    public data class Created(val id: __NAME__Id) : Create__NAME__Result

    public data class InvalidInput(val violations: List<Violation<__NAME__Field>>) :
        Create__NAME__Result
}
