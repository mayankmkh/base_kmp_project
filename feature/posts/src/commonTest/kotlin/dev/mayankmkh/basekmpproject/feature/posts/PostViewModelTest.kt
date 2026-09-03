package dev.mayankmkh.basekmpproject.feature.posts

import app.cash.turbine.test
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.feature.posts.api.PostDetailOutput
import dev.mayankmkh.basekmpproject.feature.posts.api.PostFeedOutput
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import dev.mayankmkh.basekmpproject.foundation.resource.RefreshOutcome
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblem
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import dev.mayankmkh.basekmpproject.testkit.FakePostsCommands
import dev.mayankmkh.basekmpproject.testkit.FakePostsQueries
import dev.mayankmkh.basekmpproject.testkit.PostsFixtures
import dev.mayankmkh.basekmpproject.testkit.ResourceObservationFixtures
import dev.mayankmkh.basekmpproject.testkit.runMainTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PostViewModelTest {
    @Test
    fun `feed observation becomes render state and open action emits output`() = runMainTest {
        val queries = FakePostsQueries()
        val viewModel = PostFeedViewModel(feedKey(), queries, FakePostsCommands())

        viewModel.state.test {
            assertEquals(PostFeedState(), awaitItem())
            assertEquals(PostsFixtures.feed().posts, awaitItem().posts)
            viewModel.onAction(PostFeedAction.OpenPost(PostId(2)))
            viewModel.outputs.test {
                assertEquals(PostFeedOutput.OpenPost(PostId(2)), awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `feed refresh failure emits ui command while stream failure only updates state`() =
        runMainTest {
            val queries = FakePostsQueries()
            val problem = ResourceProblem(ResourceProblemCategory.OFFLINE, retryable = true)
            val commands =
                FakePostsCommands().apply {
                    onRefreshFeed = { RefreshOutcome.Failed(problem) }
                }
            val viewModel = PostFeedViewModel(feedKey(), queries, commands)

            viewModel.state.test state@{
                awaitItem()
                awaitItem()
                viewModel.uiCommands.test {
                    viewModel.onAction(PostFeedAction.Refresh)
                    assertEquals(
                        ResourceProblemCategory.OFFLINE,
                        assertIs<PostFeedUiCommand.ShowRefreshFailed>(awaitItem()).category,
                    )
                    assertEquals(1, commands.feedRefreshCount)

                    queries.feed.value =
                        ResourceObservationFixtures.failed(
                            value = PostsFixtures.feed(),
                            category = ResourceProblemCategory.OFFLINE,
                            retryable = true,
                        )
                    val failed = this@state.awaitItem()
                    assertEquals(true, failed.isStale)
                    assertEquals(PostsFixtures.feed().posts, failed.posts)
                    expectNoEvents()
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `detail maps its resource and handles retry and back`() = runMainTest {
        val queries = FakePostsQueries()
        val commands =
            FakePostsCommands().apply {
                onRefreshPost = { _, _ ->
                    RefreshOutcome.Failed(
                        ResourceProblem(ResourceProblemCategory.TEMPORARY, retryable = true)
                    )
                }
            }
        val post = PostsFixtures.post(2)
        val viewModel = PostDetailViewModel(post.id, detailKey(), queries, commands)

        viewModel.state.test {
            assertEquals(PostDetailState(), awaitItem())
            assertEquals(post, awaitItem().post)
            viewModel.onAction(PostDetailAction.Retry)
            viewModel.uiCommands.test {
                assertEquals(
                    ResourceProblemCategory.TEMPORARY,
                    assertIs<PostDetailUiCommand.ShowRefreshFailed>(awaitItem()).category,
                )
            }
            assertEquals(listOf(post.id), commands.postRefreshes)
            viewModel.onAction(PostDetailAction.Back)
            viewModel.outputs.test { assertEquals(PostDetailOutput.Back, awaitItem()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun feedKey() = FeatureInstanceKey.forScreen("posts/feed", "post-feed")

    private fun detailKey() = FeatureInstanceKey.forScreen("posts/detail/2", "post-detail")
}
