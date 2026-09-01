package dev.mayankmkh.basekmpproject.shared.features.list.domain

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

internal interface ListRepository {
    /**
     * The cached feed, kept current.
     *
     * Backed by a Store5 source of truth, so it emits whatever SQLDelight has cached and emits
     * again once the fetcher writes. Store's validator fetches until the feed has initialized,
     * preserving a successfully fetched empty feed as valid cached data.
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
