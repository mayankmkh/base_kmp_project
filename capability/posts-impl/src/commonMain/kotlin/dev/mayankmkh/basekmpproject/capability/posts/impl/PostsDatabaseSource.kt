package dev.mayankmkh.basekmpproject.capability.posts.impl

import dev.mayankmkh.basekmpproject.capability.posts.impl.db.AppDatabase

/** Supplies the posts capability's view of the application database. */
public fun interface PostsDatabaseSource {
    public suspend fun database(): AppDatabase
}
