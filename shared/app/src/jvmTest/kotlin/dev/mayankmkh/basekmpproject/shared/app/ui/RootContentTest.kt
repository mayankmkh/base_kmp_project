package dev.mayankmkh.basekmpproject.shared.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dev.mayankmkh.basekmpproject.shared.app.App
import dev.mayankmkh.basekmpproject.shared.app.di.initKoin
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.stopKoin

@OptIn(ExperimentalTestApi::class)
class RootContentTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var originalUserHome: String
    private lateinit var testUserHome: Path

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        originalUserHome = System.getProperty("user.home")
        testUserHome = Files.createTempDirectory("base-kmp-navigation-test")
        System.setProperty("user.home", testUserHome.toString())
        initKoin()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        System.setProperty("user.home", originalUserHome)
        testUserHome.toFile().deleteRecursively()
    }

    @Test
    fun `selecting an item navigates to its details`() = runComposeUiTest {
        setContent { App() }

        onNodeWithText("Navigation 3 Sample").assertIsDisplayed()
        waitUntilAtLeastOneExists(hasText("Item 1"), timeoutMillis = 5_000)
        onNodeWithText("Item 1").performClick()
        waitUntilAtLeastOneExists(hasText("Detail Screen Item 1"), timeoutMillis = 5_000)
        onNodeWithText("Detail Screen Item 1").assertIsDisplayed()
    }
}
