package dev.mayankmkh.basekmpproject.feature.posts.api

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import base_kmp_project.feature.posts.generated.resources.Res
import base_kmp_project.feature.posts.generated.resources.arrow_back_24px
import base_kmp_project.feature.posts.generated.resources.post_detail_title
import base_kmp_project.ui.design_system.generated.resources.Res as DesignRes
import base_kmp_project.ui.design_system.generated.resources.back
import dev.mayankmkh.basekmpproject.capability.posts.api.PostId
import dev.mayankmkh.basekmpproject.foundation.presentation.FeatureInstanceKey
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PostDetailScreen(
    postId: PostId,
    instanceKey: FeatureInstanceKey,
    onOutput: (PostDetailOutput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.post_detail_title, postId.value)) },
                navigationIcon = {
                    IconButton(onClick = { onOutput(PostDetailOutput.Back) }) {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(DesignRes.string.back),
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
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            contentPadding = padding,
            snackbarHostState = snackbarHostState,
        )
    }
}
