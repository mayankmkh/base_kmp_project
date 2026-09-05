package __PACKAGE__

import com.github.michaelbull.result.Result
import __API_PACKAGE__.__NAME__Record
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem

// The origin of the data. A real Capability implements this over the shared Ktor client from
// `:foundation:network` (see `PostsRemoteSource`) and returns its typed `NetworkFailure`, which
// `toResourceProblem()` turns into the stable `ResourceProblem` taxonomy. The scaffold has no
// transport, so its failure type is the problem itself. Implementations return kotlin-result's
// `Result` with a sealed failure type; stdlib `kotlin.Result` is not used in product code.
/** Fetches every __name__ record from the backend. */
internal fun interface __NAME__RemoteSource {
    suspend fun fetchAll(): Result<List<__NAME__Record>, ResourceProblem>
}
