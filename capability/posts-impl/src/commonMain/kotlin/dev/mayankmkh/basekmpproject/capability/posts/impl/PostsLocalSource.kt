package dev.mayankmkh.basekmpproject.capability.posts.impl

import app.cash.sqldelight.async.coroutines.awaitAsOne
import dev.mayankmkh.basekmpproject.foundation.sqldelight.observeDatabase
import dev.mayankmkh.basekmpproject.foundation.sqldelight.observeList
import dev.mayankmkh.basekmpproject.foundation.sqldelight.observeOne
import dev.mayankmkh.basekmpproject.foundation.sqldelight.observeOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The posts table in capability-owned terms. Feed and detail writes share each entity row, so the
 * last committed write wins when they overlap. Multi-row feed writes land in one transaction.
 */
internal class PostsLocalSource(private val provider: PostsDatabaseProvider) {

    /** Emits the durable feed, then again after every write to `post` or `postFeedEntry`. */
    fun observeFeed(): Flow<List<PostEntity>> =
        observeDatabase(provider::database) { database ->
            database.postsSchemaQueries.selectFeed(::PostEntity).observeList()
        }

    fun observeById(id: String): Flow<PostEntity?> =
        observeDatabase(provider::database) { database ->
            database.postsSchemaQueries.selectById(id, ::PostEntity).observeOneOrNull()
        }

    /** Whether the feed endpoint has completed successfully at least once. */
    fun observeFeedInitialized(): Flow<Boolean> =
        observeDatabase(provider::database) { database ->
            database.postsSchemaQueries
                .feedInitializationCount()
                .observeOne()
                .map { it > 0L }
                .distinctUntilChanged()
        }

    suspend fun count(): Long = provider.database().postsSchemaQueries.countAll().awaitAsOne()

    /** Replaces feed membership and order in one transaction without deleting entity rows. */
    suspend fun replaceFeed(posts: List<PostEntity>) {
        val database = provider.database()
        val distinctPosts = posts.distinctBy { it.id }
        database.transaction {
            distinctPosts.forEach { post ->
                database.postsSchemaQueries.upsert(
                    id = post.id,
                    author_id = post.authorId,
                    title = post.title,
                    body = post.body,
                )
            }
            database.postsSchemaQueries.deleteFeedEntries()
            distinctPosts.forEachIndexed { index, post ->
                database.postsSchemaQueries.insertFeedEntry(post.id, index.toLong())
            }
            database.postsSchemaQueries.markFeedInitialized()
        }
    }

    /** Writes a single post without changing feed membership or order. */
    suspend fun upsert(post: PostEntity) {
        provider
            .database()
            .postsSchemaQueries
            .upsert(
                id = post.id,
                author_id = post.authorId,
                title = post.title,
                body = post.body,
            )
    }
}

/** A durable post, free of both the wire format and SQLDelight's generated row type. */
internal data class PostEntity(
    val id: String,
    val title: String,
    val body: String,
    val authorId: Long = 0,
)
