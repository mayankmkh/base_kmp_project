package dev.mayankmkh.basekmpproject.feature.posts.api

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import base_kmp_project.feature.posts.generated.resources.Res
import base_kmp_project.feature.posts.generated.resources.arrow_back_24px
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PostDetailScreen(
    postId: PostId,
    instanceKey: FeatureInstanceKey,
    onOutput: (PostDetailOutput) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Post ${postId.value}") },
                navigationIcon = {
                    IconButton(onClick = { onOutput(PostDetailOutput.Back) }) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        PostDetailCell(
            postId = postId,
            instanceKey = instanceKey,
            onOutput = onOutput,
            contentPadding = padding,
        )
    }
}
