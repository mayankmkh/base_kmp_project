package dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class UiStateTest {
    @Test
    fun `toUiState wraps Ok in Success`() {
        assertEquals(UiState.Success(42), Ok(42).toUiState())
    }

    @Test
    fun `toUiState wraps Err in Failure`() {
        val error = IllegalStateException("boom")

        val uiState = Err(error).toUiState()

        assertSame(error, (uiState as UiState.Failure).error)
    }

    @Test
    fun `map transforms Success data`() {
        val uiState: UiState<Int> = UiState.Success(21)

        assertEquals(UiState.Success(42), uiState.map { it * 2 })
    }

    @Test
    fun `map leaves the non-Success states untouched`() {
        val failure = UiState.Failure(IllegalStateException("boom"))

        assertSame(UiState.Initial, UiState.Initial.map { error("must not run") })
        assertSame(UiState.InProgress, UiState.InProgress.map { error("must not run") })
        assertSame(failure, failure.map { error("must not run") })
    }
}
