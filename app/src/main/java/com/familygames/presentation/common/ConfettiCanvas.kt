package com.familygames.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.familygames.presentation.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** True when the system "Remove animations" option is on: no confetti, shortened motion (docs/08 §5). */
val LocalReducedMotion = compositionLocalOf { false }

private class Particle(
    val start: Offset, // fraction of the canvas, or absolute px when [absolute]
    val velocity: Offset, // px per second
    val spin: Float,
    val size: Size,
    val color: Color,
    val rotation: Float,
)

/**
 * Gentle confetti (docs/08 §5): muted palette colours, opacity 0.75, gravity, fading out at the end.
 * - [origins] empty: falls from the top across the whole width (S04).
 * - [origins] given: small bursts from those points in px (a matched pair, docs/04 §5).
 * A new [key] restarts the animation.
 */
@Composable
fun ConfettiCanvas(
    key: Any,
    modifier: Modifier = Modifier,
    particleCount: Int = 40,
    durationMs: Int = 2000,
    origins: List<Offset> = emptyList(),
    density: Float = 1f,
) {
    if (LocalReducedMotion.current) return
    val progress = remember(key) { Animatable(0f) }
    val particles = remember(key) {
        val random = Random(key.hashCode())
        List(particleCount) { i ->
            val burst = origins.isNotEmpty()
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val speed = (220 + random.nextFloat() * 260) * density
            Particle(
                start = if (burst) origins[i % origins.size] else Offset(random.nextFloat(), -0.05f - random.nextFloat() * 0.25f),
                velocity = if (burst) {
                    Offset(cos(angle) * speed, sin(angle) * speed - 180 * density)
                } else {
                    Offset((random.nextFloat() - 0.5f) * 120 * density, (60 + random.nextFloat() * 120) * density)
                },
                spin = (random.nextFloat() - 0.5f) * 540f,
                size = Size((8 + random.nextFloat() * 8) * density, (3.5f + random.nextFloat() * 2.5f) * density),
                color = Palette.Confetti[random.nextInt(Palette.Confetti.size)],
                rotation = random.nextFloat() * 360f,
            )
        }
    }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    Canvas(modifier) {
        val p = progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        val t = p * durationMs / 1000f
        val gravity = 420f * density
        val alpha = 0.75f * (if (p > 0.7f) (1f - p) / 0.3f else 1f)
        particles.forEach { particle ->
            val origin = if (origins.isEmpty()) Offset(particle.start.x * size.width, particle.start.y * size.height) else particle.start
            val x = origin.x + particle.velocity.x * t
            val y = origin.y + particle.velocity.y * t + 0.5f * gravity * t * t
            rotate(particle.rotation + particle.spin * t, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = particle.color,
                    topLeft = Offset(x - particle.size.width / 2, y - particle.size.height / 2),
                    size = particle.size,
                    cornerRadius = CornerRadius(particle.size.height / 2),
                    alpha = alpha,
                )
            }
        }
    }
}
