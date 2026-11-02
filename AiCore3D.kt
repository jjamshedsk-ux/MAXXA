package com.maxxa.ai

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight, responsive 3D-style MAXXA core rendered with Compose Canvas.
 * This is a projected/shaded 3D illusion, not a Filament/OpenGL 3D scene.
 * The core reacts to the active voice stage with a lightweight energy envelope.
 * voiceLevel can optionally supply a normalized external audio level (0f..1f).
 * SpeechRecognizer does not expose microphone PCM amplitude, so Listening uses
 * a state-reactive visual pulse rather than claiming to measure raw mic audio.
 */
@Composable
fun AiCore3D(
    voiceStage: String,
    modifier: Modifier = Modifier,
    corePrimary: Color? = null,
    coreSecondary: Color? = null,
    voiceLevel: Float = 0f,
    onActivate: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val primary = when (voiceStage) {
        "Listening" -> Color(0xFF42E6FF)
        "Processing" -> Color(0xFFFFC45C)
        "Speaking" -> Color(0xFFB78CFF)
        else -> corePrimary ?: colors.primary
    }
    val secondary = when (voiceStage) {
        "Listening" -> Color(0xFF168CFF)
        "Processing" -> Color(0xFFFF6D8C)
        "Speaking" -> Color(0xFF6D5BFF)
        else -> coreSecondary ?: colors.secondary
    }
    val active = voiceStage == "Listening" || voiceStage == "Processing" || voiceStage == "Speaking"
    val transition = rememberInfiniteTransition(label = "maxxa-core")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18000, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "core-rotation"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.975f, targetValue = if (active) 1.045f else 1.015f,
        animationSpec = infiniteRepeatable(tween(if (voiceStage == "Processing") 720 else 1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "core-breath"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.965f else 1f, tween(130), label = "core-press")
    val energy by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = when (voiceStage) {
            "Listening" -> 0.62f
            "Processing" -> 0.34f
            "Speaking" -> 0.78f
            else -> 0.08f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (voiceStage) { "Listening" -> 560; "Processing" -> 900; "Speaking" -> 420; else -> 1500 },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice-energy"
    )
    val level = maxOf(voiceLevel.coerceIn(0f, 1f), energy * if (active) 1f else 0.22f)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onActivate)
            .semantics { contentDescription = "MAXXA animated AI core. State: $voiceStage. Tap to activate voice." },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val minSide = size.minDimension
            val radius = minSide * 0.335f * breathe * pressScale * (1f + level * 0.035f)
            val stroke = (minSide * 0.004f).coerceAtLeast(1f)

            // Layered energy field and depth halos.
            drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.30f), secondary.copy(alpha = 0.12f), Color.Transparent), c, minSide * 0.49f), minSide * 0.49f)
            drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.18f), Color.Transparent), c, radius * 1.72f), radius * 1.72f)
            drawCircle(primary.copy(alpha = 0.08f + level * 0.10f), radius * 1.30f)

            // Three tilted orbital rings with counter-rotation for parallax.
            listOf(0f to 0.28f, 57f to 0.19f, -61f to 0.15f).forEachIndexed { index, pair ->
                rotate(rotation * (if (index == 1) -0.68f else if (index == 2) 0.52f else 1f) + pair.first, c) {
                    drawOval(
                        brush = Brush.sweepGradient(listOf(Color.Transparent, primary.copy(alpha = pair.second), Color.White.copy(alpha = 0.42f), secondary.copy(alpha = pair.second), Color.Transparent), c),
                        topLeft = androidx.compose.ui.geometry.Offset(c.x - radius * (1.38f + index * 0.035f), c.y - radius * (0.43f + index * 0.025f)),
                        size = androidx.compose.ui.geometry.Size(radius * (2.76f + index * 0.07f), radius * (0.86f + index * 0.05f)),
                        style = Stroke(width = stroke * (1.25f - index * 0.18f), cap = StrokeCap.Round)
                    )
                }
            }

            // Orbiting energy nodes.
            val nodeCount = if (minSide < 240f) 8 else 12
            repeat(nodeCount) { i ->
                val angle = Math.toRadians(rotation * (if (i % 2 == 0) 1.0 else -0.72) + i * (360.0 / nodeCount))
                val orbitX = radius * (1.43f + (i % 3) * 0.035f)
                val orbitY = radius * (0.57f + (i % 2) * 0.045f)
                val point = androidx.compose.ui.geometry.Offset(c.x + cos(angle).toFloat() * orbitX, c.y + sin(angle).toFloat() * orbitY)
                val dot = minSide * if (i % 4 == 0) 0.014f else 0.007f
                drawCircle(if (i % 3 == 0) Color.White.copy(alpha = 0.82f) else primary.copy(alpha = 0.55f + level * 0.35f), dot, point)
            }

            // Spherical body with a dark terminator and specular lighting.
            drawCircle(Brush.radialGradient(listOf(Color(0xFF344B78), secondary.copy(alpha = 0.96f), Color(0xFF090B20)), c, radius * 1.12f), radius * 0.94f)
            drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.24f), Color.Transparent), androidx.compose.ui.geometry.Offset(c.x + radius * 0.35f, c.y + radius * 0.28f), radius * 1.08f), radius * 0.94f)

            // Rotating low-poly latitude/longitude mesh with front-face depth shading.
            val spin = Math.toRadians(rotation.toDouble())
            val cs = cos(spin).toFloat()
            val sn = sin(spin).toFloat()
            fun project(latitude: Double, longitude: Double): Pair<androidx.compose.ui.geometry.Offset, Float> {
                val x = (cos(latitude) * sin(longitude)).toFloat()
                val y = sin(latitude).toFloat()
                val z = (cos(latitude) * cos(longitude)).toFloat()
                val xr = x * cs + z * sn
                val zr = -x * sn + z * cs
                val perspective = 1f / (1f + zr * 0.16f)
                return androidx.compose.ui.geometry.Offset(c.x + xr * radius * 0.77f * perspective, c.y + y * radius * 0.77f * perspective) to zr
            }
            val latitudeBands = if (minSide < 240f) 5 else 7
            val longitudeSegments = if (minSide < 240f) 10 else 14
            for (latIndex in 0 until latitudeBands) {
                val lat = -Math.PI / 2.0 + Math.PI * (latIndex + 0.5) / latitudeBands
                for (lonIndex in 0 until longitudeSegments) {
                    val lon1 = 2.0 * Math.PI * lonIndex / longitudeSegments
                    val lon2 = 2.0 * Math.PI * (lonIndex + 1) / longitudeSegments
                    val (p1, z1) = project(lat, lon1)
                    val (p2, z2) = project(lat, lon2)
                    val depth = ((z1 + z2) / 2f).coerceIn(0f, 1f)
                    if (depth > 0.015f) drawLine(primary.copy(alpha = 0.10f + depth * 0.30f), p1, p2, strokeWidth = stroke * 0.72f)
                }
            }
            for (lonIndex in 0 until longitudeSegments) {
                val lon = 2.0 * Math.PI * lonIndex / longitudeSegments
                for (latIndex in 0 until latitudeBands) {
                    val lat1 = -Math.PI / 2.0 + Math.PI * latIndex / latitudeBands
                    val lat2 = -Math.PI / 2.0 + Math.PI * (latIndex + 1) / latitudeBands
                    val (p1, z1) = project(lat1, lon)
                    val (p2, z2) = project(lat2, lon)
                    val depth = ((z1 + z2) / 2f).coerceIn(0f, 1f)
                    if (depth > 0.015f) drawLine(primary.copy(alpha = 0.09f + depth * 0.25f), p1, p2, strokeWidth = stroke * 0.72f)
                }
            }

            // Rim and moving specular highlight complete the glass-like sphere.
            drawCircle(primary.copy(alpha = 0.74f), radius * 0.94f, style = Stroke(width = stroke * 1.7f))
            val highlight = androidx.compose.ui.geometry.Offset(c.x - radius * 0.34f, c.y - radius * 0.43f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.72f), primary.copy(alpha = 0.22f), Color.Transparent), highlight, radius * 0.62f), radius * 0.38f, highlight)
        }
    }
}
