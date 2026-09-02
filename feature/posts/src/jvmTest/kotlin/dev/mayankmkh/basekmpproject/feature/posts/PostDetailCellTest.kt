package dev.mayankmkh.basekmpproject.feature.posts

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsCommands
import dev.mayankmkh.basekmpproject.capability.posts.api.PostsQueries
import dev.mayankmkh.basekmpproject.feature.posts.api.PostDetailCell
import dev.mayankmkh.basekmpproject.feature.posts.api.postsFeatureModule
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.testkit.FakePostsCommands
import dev.mayankmkh.basekmpproject.testkit.FakePostsQueries
import dev.mayankmkh.basekmpproject.testkit.MainDispatcherRule
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@OptIn(ExperimentalTestApi::class)
class PostDetailCellTest {
    private val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())
    private val queries = FakePostsQueries()

    @BeforeTest
    fun setUp() {
        mainDispatcherRule.starting()
        startKoin {
            modules(
                postsFeatureModule,
                module {
                    single<PostsQueries> { queries }
                    single<PostsCommands> { FakePostsCommands() }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        mainDispatcherRule.finished()
    }

    @Test
    fun `different instance keys create independent keyed view models`() = runComposeUiTest {
        val postId = PostId(1)
        setContent {
            Column {
                PostDetailCell(
                    postId,
                    FeatureInstanceKey.forScreen("host/a", "post-detail"),
                    {},
                )
                PostDetailCell(
                    postId,
                    FeatureInstanceKey.forScreen("host/b", "post-detail"),
                    {},
                )
            }
        }

        waitUntil(timeoutMillis = 5_000) { queries.observedPostIds.size == 2 }
        assertEquals(listOf(postId, postId), queries.observedPostIds)
    }
}
