package dev.mayankmkh.basekmpproject.shared.app.parallax

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import base_kmp_project.shared.app.generated.resources.Res
import base_kmp_project.shared.app.generated.resources.img_movie_logo
import base_kmp_project.shared.app.generated.resources.img_movie_poster
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI

@Composable
fun ParallaxMovieCard(
    orientation: Orientation,
    modifier: Modifier = Modifier,
) {
    // Configuration
    val maxRotation = 20f     
    val maxParallax = 50f     
    
    // Normalize input
    val pitch = orientation.pitch.toFloat() 
    val roll = orientation.roll.toFloat() 
    
    // Convert to degrees for rotation logic
    val pitchDeg = -pitch * (180f / PI.toFloat())
    val rollDeg = roll * (180f / PI.toFloat())
    
    // Clamp rotation
    val rotationX = pitchDeg.coerceIn(-maxRotation, maxRotation)
    val rotationY = rollDeg.coerceIn(-maxRotation, maxRotation)
    
    // ------------------------------------------------------------------------
    // Calculated Offsets
    // ------------------------------------------------------------------------
    
    // 1. Logo Offset (Parallax) - "Pop Out"
    val logoOffsetX = (roll * maxParallax * 2f).coerceIn(-maxParallax, maxParallax)
    val logoOffsetY = (-pitch * maxParallax * 2f).coerceIn(-maxParallax, maxParallax)
    
    // 2. Glow / Ambilight Offset - Directional
    // Slides out to reveal colored glow on the tilted side
    val glowOffsetX = (roll * 220f).coerceIn(-220f, 220f)
    val glowOffsetY = (-pitch * 220f).coerceIn(-220f, 220f)
    
    // 3. Dynamic Glow Intensity
    val tiltMagnitude = (kotlin.math.abs(roll) + kotlin.math.abs(pitch))
    val glowAlpha = (tiltMagnitude * 3f).coerceIn(0f, 0.6f) 

    Box(
        modifier = modifier
            .size(width = 300.dp, height = 480.dp)
            .graphicsLayer {
                this.rotationX = rotationX
                this.rotationY = rotationY
                cameraDistance = 16f * density 
            },
        contentAlignment = Alignment.Center
    ) {
        // --- Layer 1: Glow / Ambilight ---
        // A blurred copy of the poster that acts as a colored backlight.
        // Scale 1.02f keeps it hidden at rest; large offset reveals it directionally.
        Image(
            painter = painterResource(Res.drawable.img_movie_poster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(glowOffsetX.toInt(), glowOffsetY.toInt()) }
                .graphicsLayer {
//                    alpha = glowAlpha
//                    scaleX = 1.2f
//                    scaleY = 1.2f
                    renderEffect = androidx.compose.ui.graphics.BlurEffect(
                        radiusX = 150f,
                        radiusY = 150f,
                        edgeTreatment = androidx.compose.ui.graphics.TileMode.Decal
                    )
                }
                .blur(radius = 60.dp)
        )
        
        // --- Layer 2: Card Container ---
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(Modifier.fillMaxSize()) {
                // A. Poster Image (Deep Parallax)
                val posterOffsetX = (-roll * 25f).coerceIn(-25f, 25f)
                val posterOffsetY = (pitch * 25f).coerceIn(-25f, 25f)

                Image(
                    painter = painterResource(Res.drawable.img_movie_poster),
                    contentDescription = "Movie Poster",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.25f
                            scaleY = 1.25f
                            translationX = posterOffsetX.dp.toPx()
                            translationY = posterOffsetY.dp.toPx()
                        }
                )
                
                // B. Glossy Sheen (Radial/Spotlight)
                val density = LocalDensity.current
                Box(
                   modifier = Modifier
                       .fillMaxSize()
                       .clip(RoundedCornerShape(24.dp))
                       .drawWithContent {
                           val contentSize = this.size
                           val centerX = contentSize.width / 2f
                           val centerY = contentSize.height / 2f
                           
                           val offsetX = roll * contentSize.width * 2.5f
                           val offsetY = -pitch * contentSize.height * 2.5f
                           
                           val radius = maxOf(contentSize.width, contentSize.height) * 0.7f
                           
                           drawRect(
                               brush = Brush.radialGradient(
                                   0.0f to Color.White.copy(alpha = 0.4f),
                                   0.5f to Color.White.copy(alpha = 0.1f),
                                   1.0f to Color.Transparent,
                                   center = Offset(centerX + offsetX, centerY + offsetY),
                                   radius = radius
                               )
                           )
                       }
                )
                
                // C. Subtle Vignette
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                                radius = 600f
                            )
                        )
                )
            }
        }
        
        // --- Layer 3: Pop-out Logo ---
        Image(
            painter = painterResource(Res.drawable.img_movie_logo),
            contentDescription = "Movie Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .size(width = 260.dp, height = 120.dp)
                .offset { IntOffset(logoOffsetX.toInt(), logoOffsetY.toInt()) }
        )
    }
}
