package dev.mayankmkh.basekmpproject.app.shared.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.mayankmkh.basekmpproject.app.shared.App
import dev.mayankmkh.basekmpproject.app.shared.di.initKoin
import dev.mayankmkh.basekmpproject.app.shared.nav.PostDetailRoute
import dev.mayankmkh.basekmpproject.foundation.network.createHttpClient
import io.ktor.client.HttpClient
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
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * The whole app, over its real object graph.
 *
 * Two things are swapped out and nothing else: `user.home`, so the desktop driver writes its
 * database into a temp directory instead of the developer's, and the `HttpClient`'s engine, so the
 * feed is canned rather than fetched. Everything between -- Koin, the repositories, SQLDelight,
 * Navigation 3 -- is what ships.
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
        initKoin {
            modules(
                module {
                    single<HttpClient> {
                        createHttpClient(
                            MockEngine { respondFeed() },
                            config = get(),
                            headers = get(),
                            json = get(),
                        )
                    }
                }
            )
        }
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

        onNodeWithText("Posts").assertIsDisplayed()
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
        onNodeWithText("Posts").assertIsDisplayed()
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

        fun io.ktor.client.engine.mock.MockRequestHandleScope.respondFeed() =
            respond(
                content = FEED_JSON,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
    }
}
