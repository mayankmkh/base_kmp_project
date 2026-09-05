package dev.mayankmkh.basekmpproject.feature.todos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import base_kmp_project.feature.todos.generated.resources.Res
import base_kmp_project.feature.todos.generated.resources.could_not_update_todo
import base_kmp_project.feature.todos.generated.resources.input_invalid
import base_kmp_project.feature.todos.generated.resources.owner_invalid
import base_kmp_project.feature.todos.generated.resources.summary
import base_kmp_project.feature.todos.generated.resources.title_required
import base_kmp_project.feature.todos.generated.resources.title_too_long
import base_kmp_project.feature.todos.generated.resources.todo_unavailable
import base_kmp_project.feature.todos.generated.resources.todos_temporarily_unavailable
import base_kmp_project.feature.todos.generated.resources.you_are_offline
import base_kmp_project.feature.todos.generated.resources.you_do_not_have_access
import dev.mayankmkh.basekmpproject.foundation.resource.ResourceProblemCategory
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TodoSummaryContent(
    state: TodoSummaryState,
    onAction: (TodoSummaryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        CircularProgressIndicator(modifier.padding(16.dp))
    } else {
        Text(
            text = stringResource(Res.string.summary, state.open, state.completed),
            modifier =
                modifier
                    .fillMaxWidth()
                    .clickable { onAction(TodoSummaryAction.OpenTodos) }
                    .padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

internal fun ResourceProblemCategory.messageResource(): StringResource =
    when (this) {
        ResourceProblemCategory.OFFLINE -> Res.string.you_are_offline
        ResourceProblemCategory.TEMPORARY -> Res.string.todos_temporarily_unavailable
        ResourceProblemCategory.ACCESS -> Res.string.you_do_not_have_access
        ResourceProblemCategory.PERMANENT -> Res.string.todo_unavailable
        ResourceProblemCategory.UNKNOWN -> Res.string.could_not_update_todo
    }

internal fun String.messageResource(): StringResource =
    when (this) {
        "blank" -> Res.string.title_required
        "too_long" -> Res.string.title_too_long
        "invalid_owner" -> Res.string.owner_invalid
        else -> Res.string.input_invalid
    }
