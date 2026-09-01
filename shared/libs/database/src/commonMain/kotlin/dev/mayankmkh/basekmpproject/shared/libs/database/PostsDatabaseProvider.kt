package dev.mayankmkh.basekmpproject.shared.libs.database

import dev.mayankmkh.basekmpproject.shared.libs.database.generated.PostsDatabase
import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Where [PostsLocalStore] gets its database from.
 *
 * An interface rather than the concrete provider so a test can supply an in-memory database without
 * a `DatabaseContext` and without touching the user's home directory.
 */
fun interface PostsDatabaseSource {
    suspend fun database(): PostsDatabase
}

/**
 * Opens the database once, on first use.
 *
 * A suspending factory cannot be a Koin `single { }`, and opening a SQLite file eagerly at startup
 * would put disk I/O on the path that builds the object graph. So the singleton is this provider,
 * and the database it hands out is created on whichever coroutine asks for it first.
 */
class PostsDatabaseProvider(private val context: DatabaseContext) : PostsDatabaseSource {
    private val mutex = Mutex()

    // `@Volatile` so the fast path below is a safe publication rather than a data race: without it
    // a thread could observe a non-null reference to a partly constructed database.
    @Volatile private var database: PostsDatabase? = null

    override suspend fun database(): PostsDatabase =
        database
            ?: mutex.withLock {
                // Re-checked inside the lock: several coroutines can pass the null test above
                // before any of them acquires it.
                database ?: PostsDatabase(createDriver(context)).also { database = it }
            }
}
