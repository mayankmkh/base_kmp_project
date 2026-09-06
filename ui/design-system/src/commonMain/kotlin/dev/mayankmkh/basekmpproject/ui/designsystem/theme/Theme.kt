package dev.mayankmkh.basekmpproject.ui.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
    lightColorScheme(
        primary = Purple40,
        secondary = PurpleGrey40,
        tertiary = Pink40,
    )

@Composable
public fun BaseKmpProjectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    enableDynamicTheming: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            enableDynamicTheming && supportsDynamicTheming() -> getDynamicColorScheme(darkTheme)
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

internal expect fun supportsDynamicTheming(): Boolean

@Composable internal expect fun getDynamicColorScheme(darkTheme: Boolean): ColorScheme
