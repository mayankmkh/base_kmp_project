package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import dev.mayankmkh.basekmpproject.storage.database.db.AppDatabase
import kotlin.js.ExperimentalWasmJsInterop
import org.w3c.dom.Worker

actual class DatabaseContext

/**
 * Opens a sql.js database inside a Web Worker.
 *
 * Worth knowing about the web target specifically: sql.js keeps the database in the worker's own
 * memory, so unlike the other three platforms the cache does not survive a page reload. Offline
 * reads still work within a session, and the repository's behaviour is identical -- the difference
 * is only how long "cached" lasts. Persisting it would mean exporting the database to
 * IndexedDB/OPFS on write, which is a larger change than this module.
 */
@OptIn(ExperimentalWasmJsInterop::class)
internal actual suspend fun createDriver(context: DatabaseContext): SqlDriver =
    WebWorkerDriver(createSqlJsWorker()).also { AppDatabase.Schema.create(it).await() }

// `new Worker(new URL(...))` rather than a string path: the bundler has to see the URL literally to
// emit the worker as a separate chunk and rewrite it to the hashed filename it actually ships.
@OptIn(ExperimentalWasmJsInterop::class)
private fun createSqlJsWorker(): Worker =
    js("new Worker(new URL('@cashapp/sqldelight-sqljs-worker/sqljs.worker.js', import.meta.url))")
