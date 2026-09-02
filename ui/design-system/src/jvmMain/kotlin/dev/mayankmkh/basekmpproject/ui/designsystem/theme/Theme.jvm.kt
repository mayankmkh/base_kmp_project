package dev.mayankmkh.basekmpproject.ui.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

internal actual fun supportsDynamicTheming() = false

@Composable
internal actual fun getDynamicColorScheme(darkTheme: Boolean): ColorScheme {
    error("Dynamic theming is unsupported")
}
