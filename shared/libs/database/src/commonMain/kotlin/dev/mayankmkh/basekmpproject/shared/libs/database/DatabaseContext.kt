package dev.mayankmkh.basekmpproject.shared.libs.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Whatever the platform's SQLite driver needs in order to be opened.
 *
 * Android is the only target that needs anything -- a `Context` to resolve the database directory
 * -- so the other three actuals are empty. Mirrors `PrefContext` in `libs:prefs` deliberately: the
 * app already knows how to supply one platform-shaped handle per storage library.
 */
expect class DatabaseContext

/**
 * Opens a driver with the schema in place.
 *
 * Suspending because two of the four platforms genuinely are: the Web Worker driver can only be
 * talked to over `postMessage`, and creating the schema on a fresh desktop file is a query like any
 * other. `PostsDatabaseProvider` is what keeps callers from paying that cost more than once.
 */
internal expect suspend fun createDriver(context: DatabaseContext): SqlDriver

internal const val DatabaseName = "posts.db"
