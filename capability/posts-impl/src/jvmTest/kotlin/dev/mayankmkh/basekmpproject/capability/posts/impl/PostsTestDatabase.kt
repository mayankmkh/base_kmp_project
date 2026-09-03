package dev.mayankmkh.basekmpproject.capability.posts.impl

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.mayankmkh.basekmpproject.capability.posts.impl.db.AppDatabase

internal fun createInMemoryPostsLocalSource(): PostsLocalSource {
    val driver =
        JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            schema = AppDatabase.Schema.synchronous(),
        )
    val database = AppDatabase(driver)
    return PostsLocalSource(PostsDatabaseProvider { database })
}
