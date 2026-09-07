package dev.mayankmkh.basekmpproject.storage.database

import androidx.test.platform.app.InstrumentationRegistry
import dev.mayankmkh.basekmpproject.foundation.runtime.PlatformContext
import dev.mayankmkh.basekmpproject.foundation.runtime.dispatchers.AppDispatchers
import dev.mayankmkh.basekmpproject.foundation.sqldelight.observeList
import dev.mayankmkh.basekmpproject.foundation.sqldelight.observeOne
import dev.mayankmkh.basekmpproject.storage.database.db.AppDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher

class AndroidFreshDatabaseTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun providerOpensFreshDatabaseBeforeConcurrentCollectors(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val databaseDispatcher = UnconfinedTestDispatcher()
        context.deleteDatabase(DatabaseName)

        try {
            val provider =
                AppDatabaseDriverProvider(
                    context =
                        PlatformContext(
                            context = context,
                            applicationId = context.packageName,
                        ),
                    dispatchers =
                        AppDispatchers(
                            disk = databaseDispatcher,
                            network = databaseDispatcher,
                            main = databaseDispatcher,
                            cpu = databaseDispatcher,
                            unconfined = databaseDispatcher,
                            mainImmediate = databaseDispatcher,
                        ),
                )
            val result =
                try {
                    val database = AppDatabase(provider.driver())
                    combine(
                            database.postsSchemaQueries
                                .selectFeed { id, _, _, _ -> id }
                                .observeList(),
                            database.postsSchemaQueries.feedInitializationCount().observeOne(),
                        ) { posts, initializationCount ->
                            posts to initializationCount
                        }
                        .first()
                } finally {
                    provider.close()
                }

            assertEquals(emptyList<String>() to 0L, result)
        } finally {
            context.deleteDatabase(DatabaseName)
        }
    }
}
