package dev.mayankmkh.basekmpproject.shared.app.parallax

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun ParallaxScreen(component: ParallaxComponent) {
    val tiltController = rememberTiltController()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
            .then(tiltController.modifier), // Apply platform-specific tilt modifier (Pointer on Desktop, No-op on Mobile)
        contentAlignment = Alignment.Center
    ) {
        ParallaxMovieCard(
            orientation = tiltController.orientation.value
        )
    }
}
