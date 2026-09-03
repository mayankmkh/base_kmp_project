package __PACKAGE__

import __API_PACKAGE__.__NAME__Commands
import __API_PACKAGE__.__NAME__Id
import __API_PACKAGE__.__NAME__Queries
import __API_PACKAGE__.__NAME__Record
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

// The whole implementation is `internal`: only the Koin module is visible outside this module, so
// no consumer can reach past the API.
//
// Next step: this scaffold holds its records in memory. When it owns a real resource, create
// `__NAME__RemoteSource`, `__NAME__LocalSource`, and `__NAME__DatabaseProvider`; add
// `__NAME__Schema.sq` with camelCase tables under its `db/` package; and follow
// `:capability:posts-impl` for the worked example. Adapt the Store5 `Store` with `StoreResource`
// from `:foundation:resource-store5`, and keep the capability child scope inside the
// implementation.
/** In-memory implementation of the __name__ Capability. */
internal class __NAME__CapabilityImpl : __NAME__Queries, __NAME__Commands {
    private val records = MutableStateFlow(emptyList<__NAME__Record>())

    override fun observeAll(): Flow<List<__NAME__Record>> = records.asStateFlow()

    override fun observe(id: __NAME__Id): Flow<__NAME__Record?> {
        return records.map { current -> current.firstOrNull { it.id == id } }
    }

    override suspend fun refresh(): RefreshOutcome {
        records.value = listOf(__NAME__Record(__NAME__Id("1"), "__name__ 1"))
        return RefreshOutcome.Succeeded
    }
}
