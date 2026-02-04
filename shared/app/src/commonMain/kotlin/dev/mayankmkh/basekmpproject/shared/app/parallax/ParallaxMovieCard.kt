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
import androidx.compose.ui.graphics.TileMode
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
    // Moves with the tilt direction to appear closer to camera
    val logoOffsetX = (roll * maxParallax * 2f).coerceIn(-maxParallax, maxParallax)
    val logoOffsetY = (-pitch * maxParallax * 2f).coerceIn(-maxParallax, maxParallax)
    
    // 2. Shadow Logic - "Ground Distance"
    // As we tilt, the edge lifts, distance increases -> Blur increases, Shadow moves away.
    // Shadow moves OPPOSITE to the tilt to simulate the object moving above a fixed ground.
    val shadowOffsetX = (roll * 30f).coerceIn(-30f, 30f)
    val shadowOffsetY = (-pitch * 30f).coerceIn(-30f, 30f)
    
    // Dynamic Blur: More tilt = more average distance/apparent depth
    val shadowBlur = 20.dp + (kotlin.math.abs(roll) * 10).dp + (kotlin.math.abs(pitch) * 10).dp

    // 3. Natural Sheen / Reflection
    // Simulating a Light Source at Top-Center (or slightly moving).
    // Now calculated inside the drawWithContent block for perfect alignment.

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
        // --- Layer 1: Shadow ---
        // Soft, diffused shadow that reacts to tilt
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(shadowOffsetX.toInt(), shadowOffsetY.toInt()) }
                .graphicsLayer {
                    alpha = 0.5f
                    scaleX = 0.95f
                    scaleY = 0.95f // Shadow is slightly smaller
                }
                .blur(radius = shadowBlur)
                .background(Color.Black, RoundedCornerShape(24.dp))
        )
        
        // --- Layer 2: Card Container ---
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(Modifier.fillMaxSize()) {
                // A. Poster Image
                // A. Poster Image
                // Parallax Effect: The poster is "deep" inside the card.
                // It moves opposite to the Logo (which is "popping out").
                // To do this, we scale it up (to avoid gaps) and translate it slightly.
                val posterOffsetX = (-roll * 15f).coerceIn(-15f, 15f)
                val posterOffsetY = (pitch * 15f).coerceIn(-15f, 15f)

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
                // A large radial gradient acting as a light spot
                // We draw it directly with a calculated center to ensure perfect alignment
                val density = LocalDensity.current
                Box(
                   modifier = Modifier
                       .fillMaxSize()
                       .clip(RoundedCornerShape(24.dp))
                       .drawWithContent {
                           val contentSize = this.size
                           val centerX = contentSize.width / 2f
                           val centerY = contentSize.height / 2f
                           
                           // Move the center opposite to tilt (Light source reflection logic)
                           // Max offset in pixels matching the visual range we want
                           // Video Match: Speed increased (1.5 -> 2.5) to make it race across the surface
                           val offsetX = -roll * contentSize.width * 2.5f 
                           val offsetY = pitch * contentSize.height * 2.5f
                           
                           // Use a tighter radius to simulate a spotlight, not a wash
                           // Previously roughly 400-500px. On 300dp width (~600px+), that's roughly 0.6-0.8 of width?
                           // Let's make it density aware: 300.dp magnitude.
                           val radius =  maxOf(contentSize.width, contentSize.height) * 0.7f
                           
                           drawRect(
                               brush = Brush.radialGradient(
                                   0.0f to Color.White.copy(alpha = 0.4f), // Slightly brighter than original but much softer than 0.6
                                   0.5f to Color.White.copy(alpha = 0.1f), // Smooth falloff
                                   1.0f to Color.Transparent,
                                   center = Offset(centerX + offsetX, centerY + offsetY),
                                   radius = radius
                               )
                           )
                       }
                )
                
                // C. Subtle Vignette (Static)
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
