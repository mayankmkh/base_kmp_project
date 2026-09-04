package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.storage.database.db.AppDatabase
import kotlin.js.ExperimentalWasmJsInterop
import org.w3c.dom.Worker

/**
 * Opens a sql.js database inside a Web Worker.
 *
 * sql.js keeps the database in the worker's memory, so the cache does not survive a page reload.
 * Offline reads still work within a session. Persistence would require exporting the database to
 * IndexedDB or OPFS on write.
 */
@OptIn(ExperimentalWasmJsInterop::class)
internal actual suspend fun createDriver(context: PlatformContext): SqlDriver =
    WebWorkerDriver(createSqlJsWorker()).also { AppDatabase.Schema.create(it).await() }

// The bundler must see the worker URL literally to emit and rename the separate worker chunk.
@OptIn(ExperimentalWasmJsInterop::class)
private fun createSqlJsWorker(): Worker =
    js("new Worker(new URL('@cashapp/sqldelight-sqljs-worker/sqljs.worker.js', import.meta.url))")
