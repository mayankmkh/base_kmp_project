package dev.mayankmkh.basekmpproject.app.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.mayankmkh.basekmpproject.app.shared.nav.PostDetailRoute
import dev.mayankmkh.basekmpproject.app.shared.nav.PostFeedRoute
import dev.mayankmkh.basekmpproject.app.shared.ui.RootContent
import dev.mayankmkh.basekmpproject.ui.designsystem.theme.BaseKmpProjectTheme
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Composable
fun App(modifier: Modifier = Modifier) {
    App(backStack = rememberAppBackStack(), modifier = modifier)
}

@Composable
fun App(backStack: NavBackStack<NavKey>, modifier: Modifier = Modifier) {
    BaseKmpProjectTheme {
        // Edge to edge: the app draws through the whole window and each screen's `Scaffold` turns
        // the system bar insets into content padding, so content scrolls under the bars instead of
        // stopping at them. Padding the insets away here would defeat that.
        Surface(modifier = modifier.fillMaxSize()) {
            RootContent(backStack = backStack, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun rememberAppBackStack(): NavBackStack<NavKey> =
    rememberNavBackStack(
        configuration = navigationSavedStateConfiguration,
        elements = arrayOf(PostFeedRoute),
    )

private val navigationSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(PostFeedRoute.serializer())
            subclass(PostDetailRoute.serializer())
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
