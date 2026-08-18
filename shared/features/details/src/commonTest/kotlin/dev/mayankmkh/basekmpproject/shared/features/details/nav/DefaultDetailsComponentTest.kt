package dev.mayankmkh.basekmpproject.shared.features.details.nav

import app.cash.turbine.test
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import dev.mayankmkh.basekmpproject.shared.features.details.testing.detailsViewModel
import dev.mayankmkh.basekmpproject.shared.features.details.testing.items
import dev.mayankmkh.basekmpproject.shared.libs.arch.core.presentation.UiState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class DefaultDetailsComponentTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // The component resolves its view model through the global Koin instance, so the test has
        // to stand one up rather than pass the view model in. The destructured parameter is the
        // item id the component forwards with `parametersOf`.
        startKoin {
            modules(module { factory { (itemId: String) -> detailsViewModel(dispatcher, itemId) } })
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `hands the item id it was built with to the view model`() =
        runTest(dispatcher) {
            val component = component(itemId = "2")

            component.viewModel.uiStateFlow.test {
                assertEquals(UiState.Initial, awaitItem())
                assertEquals(UiState.InProgress, awaitItem())
                assertEquals(UiState.Success(items[1]), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `forwards the close event to the navigation callback`() =
        runTest(dispatcher) {
            var finished = false
            val component = component(onFinished = { finished = true })

            component.viewModel.onCloseClicked()
            advanceUntilIdle()

            assertTrue(finished)
        }

    private fun component(itemId: String = "1", onFinished: () -> Unit = {}) =
        DefaultDetailsComponent(
            DefaultComponentContext(LifecycleRegistry().apply { resume() }),
            itemId,
            onFinished,
        )
}
