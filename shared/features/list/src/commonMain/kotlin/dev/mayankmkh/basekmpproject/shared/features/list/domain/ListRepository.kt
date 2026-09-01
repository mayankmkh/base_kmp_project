package dev.mayankmkh.basekmpproject.shared.features.list.domain

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

internal interface ListRepository {
    /**
     * The cached feed, kept current.
     *
     * Backed by the database rather than by the network, so it emits whatever is cached straight
     * away and emits again once a fetch has written. It fetches only when there is nothing cached.
     */
    fun getItems(): Flow<Result<Collection<Item>, Throwable>>

    /**
     * Fetches the feed and replaces the cache with it, unconditionally.
     *
     * Separate from [getItems] rather than folded into its fetch decision: a user pulling to
     * refresh is asking for the network whatever the cache holds, and the new rows reach the screen
     * through the flow [getItems] already returned.
     */
    suspend fun refresh()
}
