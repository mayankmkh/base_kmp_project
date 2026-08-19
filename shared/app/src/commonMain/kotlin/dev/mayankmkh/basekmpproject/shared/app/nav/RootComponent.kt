package dev.mayankmkh.basekmpproject.shared.app.nav

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.childStackWebNavigation
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.router.webhistory.WebNavigation
import com.arkivanov.decompose.router.webhistory.WebNavigationOwner
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import dev.mayankmkh.basekmpproject.shared.app.nav.RootComponent.Child
import dev.mayankmkh.basekmpproject.shared.app.nav.RootComponent.Child.DetailsChild
import dev.mayankmkh.basekmpproject.shared.app.nav.RootComponent.Child.ListChild
import dev.mayankmkh.basekmpproject.shared.features.details.nav.DetailsComponent
import dev.mayankmkh.basekmpproject.shared.features.list.nav.ListComponent
import kotlinx.serialization.Serializable

// `BackHandlerOwner` so the UI can hand the component's `BackHandler` to the predictive back
// animation, which drives the transition from the gesture instead of running it after the fact.
interface RootComponent : BackHandlerOwner {

    val stack: Value<ChildStack<*, Child>>

    fun onBackClicked()

    // Defines all possible child components
    sealed interface Child {
        class ListChild(val component: ListComponent) : Child

        class DetailsChild(val component: DetailsComponent) : Child
    }
}

/**
 * @param deepLinkUrl the URL the app was opened with, on platforms that have one. Only the Web
 *   entry point passes it; everywhere else the app always starts on the list.
 */
@OptIn(ExperimentalDecomposeApi::class)
class DefaultRootComponent(componentContext: ComponentContext, deepLinkUrl: String? = null) :
    RootComponent, WebNavigationOwner, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    private val childStack: Value<ChildStack<Config, Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialStack = { initialStack(deepLinkUrl) },
            // No `handleBackButton`: the predictive back animation registers its own callback and
            // two of them would pop twice per press.
            childFactory = ::child,
        )

    override val stack: Value<ChildStack<*, Child>> = childStack

    // Mirrors the stack into the browser's history, so back and forward buttons, the URL bar and
    // shared links all navigate. Inert off the Web -- nothing subscribes to it there.
    override val webNavigation: WebNavigation<*> =
        childStackWebNavigation(
            navigator = navigation,
            stack = childStack,
            serializer = Config.serializer(),
            pathMapper = { path(it.configuration) },
        )

    private fun child(config: Config, componentContext: ComponentContext): Child =
        when (config) {
            is Config.List -> ListChild(listComponent(componentContext))
            is Config.Details -> DetailsChild(detailsComponent(componentContext, config))
        }

    private fun listComponent(componentContext: ComponentContext): ListComponent =
        ListComponent(
            componentContext = componentContext,
            onItemSelected = { id: String -> // Supply dependencies and callbacks
                // `pushNew` over `push`: it ignores the call when the same configuration is
                // already on top, so a double tap cannot stack two identical screens.
                navigation.pushNew(Config.Details(itemId = id)) // Push the details component
            },
        )

    private fun detailsComponent(
        componentContext: ComponentContext,
        config: Config.Details,
    ): DetailsComponent =
        DetailsComponent(
            componentContext = componentContext,
            itemId = config.itemId, // Supply arguments from the configuration
            onFinished = navigation::pop, // Pop the details component
        )

    override fun onBackClicked() {
        navigation.pop()
    }

    private fun initialStack(deepLinkUrl: String?): List<Config> {
        val segments = deepLinkUrl?.urlPathSegments().orEmpty()

        // The list always stays underneath, so back from a deep-linked details screen lands
        // somewhere rather than closing the app.
        return if (segments.size == 2 && segments[0] == DETAILS_PATH) {
            listOf(Config.List, Config.Details(itemId = segments[1]))
        } else {
            listOf(Config.List)
        }
    }

    private fun path(config: Config): String? =
        when (config) {
            is Config.List -> null // The list is the root, so it contributes no path segment
            is Config.Details -> "$DETAILS_PATH/${config.itemId}"
        }

    @Serializable
    private sealed interface Config {
        @Serializable data object List : Config

        @Serializable data class Details(val itemId: String) : Config
    }

    private companion object {
        const val DETAILS_PATH = "details"
    }
}

private fun String.urlPathSegments(): List<String> =
    substringBefore('?')
        .substringBefore('#')
        .substringAfter("://")
        .substringAfter('/', missingDelimiterValue = "")
        .split('/')
        .filter(String::isNotEmpty)
