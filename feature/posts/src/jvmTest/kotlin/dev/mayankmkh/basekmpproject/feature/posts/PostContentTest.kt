package dev.mayankmkh.basekmpproject.feature.posts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.mayankmkh.basekmpproject.foundation.resource.Problem
import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PostContentTest {
    @Test
    fun `feed renders rows and reports the selected post`() = runComposeUiTest {
        val actions = mutableListOf<PostFeedAction>()
        setContent { PostFeedContent(PostsFeatureFixtures.feed, actions::add) }

        onNodeWithText("First post").assertIsDisplayed()
        onNodeWithText("Second post").performClick()

        assertEquals(PostFeedAction.OpenPost(PostsFeatureFixtures.posts[1].id), actions.single())
    }

    @Test
    fun `feed shows progress empty failure and cached offline states`() = runComposeUiTest {
        var state by mutableStateOf(PostFeedState())
        setContent { PostFeedContent(state, {}) }
        onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()

        state = PostFeedState(isInitialLoading = false)
        waitForIdle()
        onNodeWithText("Nothing here yet.", substring = true).assertIsDisplayed()

        state =
            PostFeedState(
                isInitialLoading = false,
                problem = Problem(ProblemKind.UNEXPECTED),
            )
        waitForIdle()
        onNodeWithText("Retry").assertIsDisplayed()

        state = PostsFeatureFixtures.offlineFeed
        waitForIdle()
        onNodeWithText("First post").assertIsDisplayed()
    }

    @Test
    fun `detail renders body and retry action`() = runComposeUiTest {
        val actions = mutableListOf<PostDetailAction>()
        var state by mutableStateOf(PostsFeatureFixtures.detail)
        setContent { PostDetailContent(state, actions::add) }
        onNodeWithText("First body").assertIsDisplayed()

        state =
            PostDetailState(
                isInitialLoading = false,
                problem = Problem(ProblemKind.UNEXPECTED),
            )
        waitForIdle()
        onNodeWithText("Retry").performClick()
        assertEquals(PostDetailAction.Retry, actions.single())
    }
}
