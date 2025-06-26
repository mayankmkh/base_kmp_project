package dev.mayankmkh.basekmpproject.shared.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mayankmkh.basekmpproject.shared.app.nav.RootComponent
import dev.mayankmkh.basekmpproject.shared.app.ui.RootContent
import dev.mayankmkh.basekmpproject.shared.libs.designsystem.theme.BaseKmpProjectTheme

@Composable
fun App(root: RootComponent, modifier: Modifier = Modifier) {
    BaseKmpProjectTheme {
        Surface(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
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
