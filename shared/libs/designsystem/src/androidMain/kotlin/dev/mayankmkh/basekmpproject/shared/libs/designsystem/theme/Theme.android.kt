package dev.mayankmkh.basekmpproject.shared.libs.designsystem.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
internal actual fun supportsDynamicTheming() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
internal actual fun getDynamicColorScheme(darkTheme: Boolean): ColorScheme {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        error("Dynamic theming is unsupported")
    }
    val context = LocalContext.current
    return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
