package __PACKAGE__

import com.github.michaelbull.result.Result
import __API_PACKAGE__.__NAME__Field
import __API_PACKAGE__.__NAME__Record
import dev.mayankmkh.basekmpproject.foundation.network.NetworkFailure
import dev.mayankmkh.basekmpproject.foundation.resource.Violation

/** Backend answers that the implementation maps before the generic failure bridge runs. */
internal sealed interface Create__NAME__RemoteAnswer {
    data class Created(val record: __NAME__Record) : Create__NAME__RemoteAnswer

    data class InvalidInput(val violations: List<Violation<__NAME__Field>>) : Create__NAME__RemoteAnswer
}

/** The implementation-only backend seam for the __name__ Capability. */
internal interface __NAME__RemoteSource {
    suspend fun fetchAll(): Result<List<__NAME__Record>, NetworkFailure>

    suspend fun create(label: String): Result<Create__NAME__RemoteAnswer, NetworkFailure>
}
