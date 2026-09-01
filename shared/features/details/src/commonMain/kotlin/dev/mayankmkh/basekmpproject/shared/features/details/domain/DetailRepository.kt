package dev.mayankmkh.basekmpproject.shared.features.details.domain

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

internal interface DetailRepository {
    /**
     * The one item [id] names, kept current.
     *
     * A flow rather than a one-shot read so the screen can show the cached copy immediately and
     * update once the fetch lands. It fetches when the item is not cached, which is what makes a
     * cold deep link work: the list may never have run, so its cache write cannot be relied on.
     */
    fun getItem(id: String): Flow<Result<Item, Throwable>>
}
