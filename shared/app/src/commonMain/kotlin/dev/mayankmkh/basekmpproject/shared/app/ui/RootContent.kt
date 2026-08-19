package dev.mayankmkh.basekmpproject.shared.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import dev.mayankmkh.basekmpproject.shared.app.nav.RootComponent
import dev.mayankmkh.basekmpproject.shared.app.nav.RootComponent.Child.DetailsChild
import dev.mayankmkh.basekmpproject.shared.app.nav.RootComponent.Child.ListChild
import dev.mayankmkh.basekmpproject.shared.features.details.ui.DetailsContent
import dev.mayankmkh.basekmpproject.shared.features.list.ui.ListContent

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun RootContent(component: RootComponent, modifier: Modifier = Modifier) {
    Children(
        stack = component.stack,
        modifier = modifier,
        // Follows the back gesture frame by frame where the platform reports one (Android 14+, and
        // iOS through the overlay in `MainViewController`); falls back to the plain fade elsewhere.
        animation =
            predictiveBackAnimation(
                backHandler = component.backHandler,
                fallbackAnimation = stackAnimation(fade()),
                onBack = component::onBackClicked,
            ),
    ) {
        when (val child = it.instance) {
            is ListChild -> ListContent(component = child.component)
            is DetailsChild -> DetailsContent(component = child.component)
        }
    }
}
