package dev.mayankmkh.basekmpproject.capability.posts.impl

import dev.mayankmkh.basekmpproject.capability.posts.impl.db.AppDatabase
import dev.mayankmkh.basekmpproject.testkit.asProvider
import dev.mayankmkh.basekmpproject.testkit.inMemorySqliteDriver

internal fun createInMemoryPostsLocalSource(): PostsLocalSource {
    val driver = inMemorySqliteDriver(AppDatabase.Schema)
    return PostsLocalSource(driver.asProvider())
}
