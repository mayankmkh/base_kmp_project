package dev.mayankmkh.basekmpproject.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import base_kmp_project.ui.design_system.generated.resources.Res
import base_kmp_project.ui.design_system.generated.resources.retry
import base_kmp_project.ui.design_system.generated.resources.something_went_wrong
import base_kmp_project.ui.design_system.generated.resources.temporarily_unavailable
import base_kmp_project.ui.design_system.generated.resources.took_too_long
import base_kmp_project.ui.design_system.generated.resources.you_are_offline
import base_kmp_project.ui.design_system.generated.resources.you_do_not_have_access
import dev.mayankmkh.basekmpproject.foundation.resource.Problem
import dev.mayankmkh.basekmpproject.foundation.resource.ProblemKind
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

public fun ProblemKind.messageResource(): StringResource =
    when (this) {
        ProblemKind.OFFLINE -> Res.string.you_are_offline
        ProblemKind.TIMEOUT -> Res.string.took_too_long
        ProblemKind.SERVER -> Res.string.temporarily_unavailable
        ProblemKind.FORBIDDEN -> Res.string.you_do_not_have_access
        ProblemKind.UNEXPECTED -> Res.string.something_went_wrong
    }

@Composable
public fun Failure(
    problem: Problem,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Centred(modifier) {
        Text(
            text = stringResource(problem.kind.messageResource()),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}

@Composable
public fun Centred(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        content()
    }
}
