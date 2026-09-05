package dev.mayankmkh.basekmpproject.feature.todos

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.mayankmkh.basekmpproject.ui.designsystem.theme.BaseKmpProjectTheme

@Preview
@Composable
internal fun TodoListPreview() {
    TodosPreview { TodoListContent(TodosFeatureFixtures.list, {}, summary = SummaryFixture) }
}

@Preview
@Composable
internal fun TodoListEmptyPreview() {
    TodosPreview { TodoListContent(TodosFeatureFixtures.emptyList, {}, summary = SummaryFixture) }
}

@Preview
@Composable
internal fun TodoListLoadingPreview() {
    TodosPreview { TodoListContent(TodosFeatureFixtures.loadingList, {}, summary = SummaryFixture) }
}

@Preview
@Composable
internal fun TodoListOfflinePreview() {
    TodosPreview { TodoListContent(TodosFeatureFixtures.offlineList, {}, summary = SummaryFixture) }
}

@Preview
@Composable
internal fun TodoListErrorPreview() {
    TodosPreview { TodoListContent(TodosFeatureFixtures.errorList, {}, summary = SummaryFixture) }
}

@Preview
@Composable
internal fun TodoDetailPreview() {
    TodosPreview { TodoDetailContent(TodosFeatureFixtures.detail, {}) }
}

@Preview
@Composable
internal fun TodoDetailNotFoundPreview() {
    TodosPreview { TodoDetailContent(TodosFeatureFixtures.detailNotFound, {}) }
}

@Preview
@Composable
internal fun TodoEditorFieldErrorPreview() {
    TodosPreview { TodoEditorContent(TodosFeatureFixtures.editorFieldError, {}) }
}

@Preview
@Composable
internal fun TodoEditorServerMessagePreview() {
    TodosPreview { TodoEditorContent(TodosFeatureFixtures.editorServerMessage, {}) }
}

private val SummaryFixture: @Composable () -> Unit = {
    TodoSummaryContent(TodosFeatureFixtures.summary, {})
}

@Composable
private fun TodosPreview(content: @Composable () -> Unit) {
    BaseKmpProjectTheme(content = content)
}
