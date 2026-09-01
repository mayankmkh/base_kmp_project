package dev.mayankmkh.basekmpproject.shared.features.details.domain

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

internal interface DetailRepository {
    /**
     * The one item [id] names, kept current.
     *
     * Store5 reads SQLDelight first and keeps observing it. A missing item falls through to the
     * fetcher, which is what makes a cold deep link work when the list has never populated cache.
     */
    fun getItem(id: String): Flow<Result<Item, Throwable>>
}
