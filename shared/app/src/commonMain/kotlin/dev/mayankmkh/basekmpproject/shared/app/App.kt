package dev.mayankmkh.basekmpproject.shared.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mayankmkh.basekmpproject.shared.app.nav.RootComponent
import dev.mayankmkh.basekmpproject.shared.app.ui.RootContent
import dev.mayankmkh.basekmpproject.shared.libs.designsystem.theme.BaseKmpProjectTheme

@Composable
fun App(root: RootComponent, modifier: Modifier = Modifier) {
    BaseKmpProjectTheme {
        // Edge to edge: the app draws through the whole window and each screen's `Scaffold` turns
        // the system bar insets into content padding, so content scrolls under the bars instead of
        // stopping at them. Padding the insets away here would defeat that.
        Surface(modifier = modifier.fillMaxSize()) {
            RootContent(component = root, modifier = Modifier.fillMaxSize())
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
