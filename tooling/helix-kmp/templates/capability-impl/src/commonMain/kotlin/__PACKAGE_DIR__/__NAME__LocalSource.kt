package __PACKAGE__

import __API_PACKAGE__.__NAME__Id
import __API_PACKAGE__.__NAME__Record
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// Durable truth. This scaffold keeps it in memory; a real Capability replaces the body with
// SQLDelight queries and keeps the same three properties, as `PostsLocalSource` does:
// entity rows, a synchronized marker in its own table, and one transaction that writes both. The
// marker is what lets an empty synchronized collection differ from one never synchronized.
// Feed and detail writes share entity rows, so the last committed write wins when they overlap.
/** Local source of truth for the __name__ Capability. */
internal class __NAME__LocalSource {
    private val snapshot = MutableStateFlow(Snapshot())

    fun observeAll(): Flow<List<__NAME__Record>> {
        return snapshot.map { it.records }.distinctUntilChanged()
    }

    fun observe(id: __NAME__Id): Flow<__NAME__Record?> {
        return snapshot.map { current -> current.records.firstOrNull { it.id == id } }
    }

    // The marker query: true once any synchronization has committed, including an empty one.
    fun observeSynchronized(): Flow<Boolean> =
        snapshot.map { it.synchronized }.distinctUntilChanged()

    // The single transactional write: rows and marker change together or not at all.
    fun replaceAll(records: List<__NAME__Record>) {
        snapshot.value = Snapshot(records = records, synchronized = true)
    }

    private data class Snapshot(
        val records: List<__NAME__Record> = emptyList(),
        val synchronized: Boolean = false,
    )
}
