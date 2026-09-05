package dev.mayankmkh.basekmpproject.app.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.mayankmkh.basekmpproject.app.shared.nav.PostDetailRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.PostFeedRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.TodoDetailRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.TodoEditorRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.TodoListRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.rememberAppNavigationState
import dev.mayankmkh.basekmpproject.app.shared.ui.RootContent
import dev.mayankmkh.basekmpproject.ui.designsystem.theme.BaseKmpProjectTheme
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Composable
fun App(modifier: Modifier = Modifier) {
    App(backStack = rememberAppBackStack(), modifier = modifier)
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun App(backStack: NavBackStack<NavKey>, modifier: Modifier = Modifier) {
    val navigationState = rememberAppNavigationState(backStack, navigationSavedStateConfiguration)
    BaseKmpProjectTheme {
        // Edge to edge: the app draws through the whole window and each screen's `Scaffold` turns
        // the system bar insets into content padding, so content scrolls under the bars instead of
        // stopping at them. Padding the insets away here would defeat that.
        Surface(modifier = modifier.fillMaxSize()) {
            RootContent(
                navigationState = navigationState,
                navigationSuiteType =
                    NavigationSuiteScaffoldDefaults.navigationSuiteType(
                        currentWindowAdaptiveInfoV2()
                    ),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun rememberAppBackStack(): NavBackStack<NavKey> =
    rememberNavBackStack(
        configuration = navigationSavedStateConfiguration,
        elements = arrayOf(PostFeedRoute),
    )

internal val navigationSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(PostFeedRoute.serializer())
            subclass(PostDetailRoute.serializer())
            subclass(TodoListRoute.serializer())
            subclass(TodoDetailRoute.serializer())
            subclass(TodoEditorRoute.serializer())
        }
    }
}

/*
@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
*/
