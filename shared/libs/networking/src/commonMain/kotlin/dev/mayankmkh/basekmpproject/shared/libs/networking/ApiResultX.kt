package dev.mayankmkh.basekmpproject.shared.libs.networking

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse

/**
 * Unwraps an API result, throwing the failure's underlying cause.
 *
 * Store5's `Fetcher.of` turns thrown failures into `StoreReadResponse.Error`, so a repository
 * feeding it a `Result` has to unwrap the API error. This keeps that conversion beside [ApiError]
 * instead of repeating it in every Store fetcher.
 */
fun <V> Result<V, ApiError>.getOrThrow(): V = getOrElse { throw it.throwable }
