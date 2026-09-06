package dev.mayankmkh.basekmpproject.app.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.mayankmkh.basekmpproject.app.shared.App
import dev.mayankmkh.basekmpproject.app.shared.di.initKoin
import dev.mayankmkh.basekmpproject.app.shared.di.processSurfaceOverrides
import dev.mayankmkh.basekmpproject.app.shared.di.shutdownKoin
import dev.mayankmkh.basekmpproject.app.shared.nav.AppNavigationState
import dev.mayankmkh.basekmpproject.app.shared.nav.PostDetailRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.PostFeedRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.TodoDetailRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.rememberAppNavigationState
import dev.mayankmkh.basekmpproject.app.shared.navigationSavedStateConfiguration
import dev.mayankmkh.basekmpproject.ui.designsystem.theme.BaseKmpProjectTheme
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The whole app, over its real object graph.
 *
 * Four process-level surfaces are swapped out and nothing else: `user.home` redirects the desktop
 * database, the HTTP engine serves canned responses, and both stored-data factories use memory.
 * Everything between -- Koin, the repositories, SQLDelight, Navigation 3 -- is what ships.
 */
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
        initKoin(isDebug = true) {
            modules(
                processSurfaceOverrides(
                    MockEngine { request -> respondApi(request.url.encodedPath) }
                )
            )
        }
    }

    @AfterTest
    fun tearDown() {
        shutdownKoin()
        Dispatchers.resetMain()
        System.setProperty("user.home", originalUserHome)
        testUserHome.toFile().deleteRecursively()
    }

    @Test
    fun `selecting an item navigates to its details`() = runComposeUiTest {
        setContent { App() }

        onAllNodesWithText("Posts").assertCountEquals(2)
        waitUntilAtLeastOneExists(hasText("First post"), timeoutMillis = 5_000)
        onNodeWithText("First post").performClick()

        // The details screen finds the post the list already cached, so this arrives without a
        // second request.
        waitUntilAtLeastOneExists(hasText("Post 1"), timeoutMillis = 5_000)
        onNodeWithText("First body").assertIsDisplayed()
    }

    /**
     * The web build restores its back stack from the URL, so a shared link to a post starts the
     * session with the detail route as the only entry. Back on that stack must not empty it --
     * `NavDisplay` has nothing to render without an entry.
     */
    @Test
    fun `back from a restored detail session lands on the feed`() = runComposeUiTest {
        setContent {
            val backStack = remember { NavBackStack<NavKey>(PostDetailRoute(id = 1)) }
            App(backStack = backStack)
        }

        waitUntilAtLeastOneExists(hasText("Post 1"), timeoutMillis = 5_000)
        onNodeWithContentDescription("Back").performClick()

        waitUntilAtLeastOneExists(hasText("Posts"), timeoutMillis = 5_000)
        onAllNodesWithText("Posts").assertCountEquals(2)
    }

    @Test
    fun `top level destination switch opens todos and its detail`() = runComposeUiTest {
        setContent { App() }

        onNode(hasText("Todos") and hasClickAction()).performClick()
        waitUntilAtLeastOneExists(hasText("Buy milk"), timeoutMillis = 5_000)
        onNodeWithText("Buy milk").performClick()

        waitUntilAtLeastOneExists(hasText("Todo 1"), timeoutMillis = 5_000)
        onNodeWithText("Todo 1").assertIsDisplayed()
    }

    @Test
    fun `switching destinations restores each destination history`() = runComposeUiTest {
        setContent { App() }

        waitUntilAtLeastOneExists(hasText("First post"), timeoutMillis = 5_000)
        onNodeWithText("First post").performClick()
        waitUntilAtLeastOneExists(hasText("Post 1"), timeoutMillis = 5_000)

        onNode(hasText("Todos") and hasClickAction()).performClick()
        waitUntilAtLeastOneExists(hasText("Buy milk"), timeoutMillis = 5_000)
        onAllNodesWithText("Buy milk")[0].performClick()
        waitUntilAtLeastOneExists(hasText("Todo 1"), timeoutMillis = 5_000)

        onNode(hasText("Posts") and hasClickAction()).performClick()
        onNodeWithText("Post 1").assertIsDisplayed()

        onNode(hasText("Todos") and hasClickAction()).performClick()
        onNodeWithText("Todo 1").assertIsDisplayed()
    }

    @Test
    fun `back from a non-start destination root returns to posts`() = runComposeUiTest {
        lateinit var navigationState: AppNavigationState
        setContent {
            NavigationTestContent(
                backStack = remember { NavBackStack(PostFeedRoute) },
                onState = { navigationState = it },
            )
        }

        onNode(hasText("Todos") and hasClickAction()).performClick()
        waitUntilAtLeastOneExists(hasText("Buy milk"), timeoutMillis = 5_000)

        runOnIdle { navigationState.goBack() }

        waitUntilAtLeastOneExists(hasText("First post"), timeoutMillis = 5_000)
        onNodeWithText("First post").assertIsDisplayed()
    }

    @Test
    fun `back from a restored todo detail session lands on the todo list`() = runComposeUiTest {
        setContent {
            val backStack = remember { NavBackStack<NavKey>(TodoDetailRoute(id = 1)) }
            App(backStack = backStack)
        }

        waitUntilAtLeastOneExists(hasText("Todo 1"), timeoutMillis = 5_000)
        onNodeWithText("Back").performClick()

        waitUntilAtLeastOneExists(hasText("Buy milk"), timeoutMillis = 5_000)
        onNodeWithText("Buy milk").assertIsDisplayed()
    }

    /**
     * The shell swaps Material's bottom navigation for its rail. The `NavDisplay` and everything it
     * owns must survive that swap, otherwise a desktop window resize resets or breaks every screen.
     */
    @Test
    fun `resizing across the layout breakpoint keeps navigation working`() = runComposeUiTest {
        var width by mutableStateOf(600.dp)
        setContent {
            Box(Modifier.width(width)) {
                NavigationTestContent(
                    backStack = remember { NavBackStack(PostFeedRoute) },
                    navigationSuiteType =
                        if (width < 840.dp) {
                            NavigationSuiteType.ShortNavigationBarCompact
                        } else {
                            NavigationSuiteType.WideNavigationRailCollapsed
                        },
                )
            }
        }

        onNode(hasText("Todos") and hasClickAction()).performClick()
        waitUntilAtLeastOneExists(hasText("Buy milk"), timeoutMillis = 5_000)

        width = 1_000.dp
        waitForIdle()
        onNodeWithText("Buy milk").assertIsDisplayed()
        onNodeWithText("Buy milk").performClick()
        waitUntilAtLeastOneExists(hasText("Todo 1"), timeoutMillis = 5_000)

        width = 600.dp
        waitForIdle()
        onNodeWithText("Todo 1").assertIsDisplayed()
        onNodeWithText("Back").performClick()
        waitUntilAtLeastOneExists(hasText("Buy milk"), timeoutMillis = 5_000)
    }

    @Test
    fun `list detail renders both panes at expanded width`() =
        runDesktopComposeUiTest(width = 1_200, height = 800) {
            setContent { App() }

            waitUntilAtLeastOneExists(hasText("First post"), timeoutMillis = 5_000)
            onNodeWithText("First post").performClick()

            waitUntilAtLeastOneExists(hasText("Post 1"), timeoutMillis = 5_000)
            onNodeWithText("Second post").assertIsDisplayed()
            onNodeWithText("First body").assertIsDisplayed()
        }

    @Test
    fun `list detail renders one pane at compact width`() =
        runDesktopComposeUiTest(width = 500, height = 800) {
            setContent { App() }

            waitUntilAtLeastOneExists(hasText("First post"), timeoutMillis = 5_000)
            onNodeWithText("First post").performClick()

            waitUntilAtLeastOneExists(hasText("Post 1"), timeoutMillis = 5_000)
            onNodeWithText("Second post").assertDoesNotExist()
            onNodeWithText("First body").assertIsDisplayed()
        }

    private companion object {
        val FEED_JSON =
            """
            [
              {"userId": 1, "id": 1, "title": "First post", "body": "First body"},
              {"userId": 1, "id": 2, "title": "Second post", "body": "Second body"}
            ]
            """
                .trimIndent()

        val TODOS_JSON =
            """
            [
              {"userId": 1, "id": 1, "title": "Buy milk", "completed": false},
              {"userId": 1, "id": 2, "title": "Ship demo", "completed": true}
            ]
            """
                .trimIndent()

        const val TODO_JSON = """{"userId":1,"id":1,"title":"Buy milk","completed":false}"""

        fun io.ktor.client.engine.mock.MockRequestHandleScope.respondFeed() =
            respond(
                content = FEED_JSON,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )

        fun io.ktor.client.engine.mock.MockRequestHandleScope.respondApi(path: String) =
            when {
                path == "/todos/1" -> respondJson(TODO_JSON)
                path == "/todos" -> respondJson(TODOS_JSON)
                else -> respondFeed()
            }

        fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(body: String) =
            respond(
                content = body,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
    }
}

@Composable
private fun NavigationTestContent(
    backStack: NavBackStack<NavKey>,
    navigationSuiteType: NavigationSuiteType = NavigationSuiteType.ShortNavigationBarCompact,
    onState: (AppNavigationState) -> Unit = {},
) {
    val navigationState = rememberAppNavigationState(backStack, navigationSavedStateConfiguration)
    SideEffect { onState(navigationState) }
    BaseKmpProjectTheme {
        Surface(Modifier.fillMaxSize()) {
            RootContent(
                navigationState = navigationState,
                navigationSuiteType = navigationSuiteType,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
