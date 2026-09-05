package dev.mayankmkh.basekmpproject.storage.database

import app.cash.sqldelight.db.SqlDriver
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext

/**
 * Opens a driver with the schema in place.
 *
 * The Web Worker driver and fresh desktop schema creation are suspending operations.
 * `AppDatabaseDriverProvider` keeps callers from paying that cost more than once.
 */
internal expect suspend fun createDriver(context: PlatformContext): SqlDriver

internal const val DatabaseName = "app.db"
