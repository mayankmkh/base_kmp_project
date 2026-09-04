package __PACKAGE__

import __API_PACKAGE__.__NAME__Record

// The origin of the data. A real Capability implements this over the shared Ktor client from
// `:foundation:network` (see `PostsRemoteSource`) and returns its typed `NetworkFailure`, which
// `toResourceProblem()` turns into the stable `ResourceProblem` taxonomy. The scaffold only needs
// success or failure.
/** Fetches every __name__ record from the backend. */
internal fun interface __NAME__RemoteSource {
    suspend fun fetchAll(): Result<List<__NAME__Record>>
}
