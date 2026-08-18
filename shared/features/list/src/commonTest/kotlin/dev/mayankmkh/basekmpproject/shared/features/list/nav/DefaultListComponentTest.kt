package dev.mayankmkh.basekmpproject.shared.features.list.nav

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import dev.mayankmkh.basekmpproject.shared.features.list.testing.listViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class DefaultListComponentTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `forwards a clicked item to the navigation callback`() =
        runTest(dispatcher) {
            // The component resolves its view model through the global Koin instance, so the test
            // has
            // to stand one up rather than pass the view model in.
            startKoin { modules(module { factory { listViewModel(dispatcher) } }) }
            val lifecycle = LifecycleRegistry()
            val selected = mutableListOf<String>()
            val component =
                DefaultListComponent(
                    DefaultComponentContext(lifecycle),
                    onItemSelected = selected::add,
                )
            lifecycle.resume()

            component.viewModel.onItemClicked("2")
            advanceUntilIdle()

            assertEquals(listOf("2"), selected)
        }
}
