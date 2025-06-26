package dev.mayankmkh.basekmpproject.shared.app.nav

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.popTo
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import dev.mayankmkh.basekmpproject.shared.app.nav.RootComponent.Child
import dev.mayankmkh.basekmpproject.shared.app.nav.RootComponent.Child.DetailsChild
import dev.mayankmkh.basekmpproject.shared.app.nav.RootComponent.Child.ListChild
import dev.mayankmkh.basekmpproject.shared.features.details.nav.DetailsComponent
import dev.mayankmkh.basekmpproject.shared.features.list.nav.ListComponent
import kotlinx.serialization.Serializable

interface RootComponent {

    val stack: Value<ChildStack<*, Child>>

    // It's possible to pop multiple screens at a time on iOS
    fun onBackClicked(toIndex: Int)

    // Defines all possible child components
    sealed class Child {
        class ListChild(val component: ListComponent) : Child()

        class DetailsChild(val component: DetailsComponent) : Child()
    }
}

class DefaultRootComponent(componentContext: ComponentContext) :
    RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    override val stack: Value<ChildStack<*, Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config.List, // The initial child component is List
            handleBackButton = true, // Automatically pop from the stack on back button presses
            childFactory = ::child,
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
                navigation.push(Config.Details(itemId = id)) // Push the details component
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

    override fun onBackClicked(toIndex: Int) {
        navigation.popTo(index = toIndex)
    }

    @Serializable
    private sealed interface Config {
        @Serializable data object List : Config

        @Serializable data class Details(val itemId: String) : Config
    }
}
