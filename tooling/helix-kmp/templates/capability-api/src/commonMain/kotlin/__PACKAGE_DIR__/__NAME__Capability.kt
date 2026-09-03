package __PACKAGE__

import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import kotlinx.coroutines.flow.Flow

// Queries observe and never mutate. One Capability exposes one grouped Queries interface rather
// than a per-screen method bag.
/** The __name__ Capability's read surface. */
public interface __NAME__Queries {
    public fun observeAll(): Flow<List<__NAME__Record>>

    public fun observe(id: __NAME__Id): Flow<__NAME__Record?>
}

// A refresh Command returns the outcome of one attempt for transient caller feedback. Observers
// above remain the source of truth for persistent state. See `:capability:posts-api` for the
// `RefreshQos`-carrying form once this Capability owns a network or database resource.
/** The __name__ Capability's explicit synchronization intents. */
public interface __NAME__Commands {
    public suspend fun refresh(): RefreshOutcome
}
