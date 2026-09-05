/**
 * Flow helpers for SQLDelight's asynchronous API. They hide that `asFlow()` emits the query rather
 * than its rows, and that opening an asynchronous database suspends even though building an
 * observation flow must not.
 */
package dev.mayankmkh.basekmpproject.foundation.sqldelight

import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Emits the rows of this query now and again after every write to a table it reads. */
public fun <T : Any> Query<T>.observeList(): Flow<List<T>> = asFlow().map { it.awaitAsList() }

/** Emits the single row or null now and again after every write to a table it reads. */
public fun <T : Any> Query<T>.observeOneOrNull(): Flow<T?> = asFlow().map { it.awaitAsOneOrNull() }

/** Emits the single row now and again after every write to a table it reads. */
public fun <T : Any> Query<T>.observeOne(): Flow<T> = asFlow().map { it.awaitAsOne() }

/**
 * Defers opening the database until first collection. Building the flow never suspends, so a
 * coordinator-backed observation stays cold until somebody collects it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <D, T> observeDatabase(
    database: suspend () -> D,
    query: (D) -> Flow<T>,
): Flow<T> = flow { emit(database()) }.flatMapLatest(query)
