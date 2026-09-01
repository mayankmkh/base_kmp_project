package dev.mayankmkh.basekmpproject.shared.libs.networking

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse

/**
 * Unwraps an API result, throwing the failure's underlying cause.
 *
 * `NetworkBoundResource.loadFromNetwork` signals failure by throwing -- it wraps the call in its
 * own `runCatching` to decide between emitting cached data and surfacing the error. So a repository
 * feeding it a `Result` has to convert, and this is that conversion in one place instead of once
 * per repository. It lives beside [ApiError] rather than in an API module so the next one does not
 * need its own copy.
 */
fun <V> Result<V, ApiError>.getOrThrow(): V = getOrElse { throw it.throwable }
