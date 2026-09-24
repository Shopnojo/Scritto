package com.internship.scritto.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun ScrittoMesh(
    modifier: Modifier = Modifier,
    aiReactive: Boolean = false
) {
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary

    val spacing = with(density) { 28.dp.toPx() }
    val baseRadius = with(density) { 1.1.dp.toPx() }
    val interactionRadius = with(density) { 120.dp.toPx() }

    var touchPosition by remember {
        mutableStateOf(Offset.Unspecified)
    }

    val aiTransition = rememberInfiniteTransition(label = "aiMeshRipple")
    val rippleProgress by aiTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleProgress"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        touchPosition = offset
                    },
                    onDrag = { change, _ ->
                        touchPosition = change.position
                    },
                    onDragEnd = {
                        touchPosition = Offset.Unspecified
                    },
                    onDragCancel = {
                        touchPosition = Offset.Unspecified
                    }
                )
            }
    ) {
        var y = spacing / 2f

        while (y < size.height) {
            var x = spacing / 2f

            while (x < size.width) {
                val point = Offset(x, y)

                var radius = baseRadius
                var alpha = 0.28f
                var drawPosition = point

                if (aiReactive) {
                    val focus = Offset(
                        size.width * 0.48f,
                        size.height * 0.46f
                    )
                    val dx = point.x - focus.x
                    val dy = point.y - focus.y
                    val distance = sqrt(dx * dx + dy * dy)
                    val maxDistance = sqrt(
                        size.width * size.width +
                            size.height * size.height
                    ) * 0.72f

                    // A broad wave travels outward through the mesh. The
                    // dots brighten as the wavefront passes them, then fade.
                    val waveRadius = rippleProgress * maxDistance
                    val waveWidth = with(density) { 150.dp.toPx() }
                    val waveDistance = abs(distance - waveRadius)
                    val waveInfluence =
                        (1f - waveDistance / waveWidth).coerceIn(0f, 1f)
                    val centerInfluence =
                        (1f - distance / with(density) { 210.dp.toPx() })
                            .coerceIn(0f, 1f)

                    radius += baseRadius *
                        (waveInfluence * 1.15f + centerInfluence * 0.45f)
                    alpha +=
                        waveInfluence * 0.34f +
                            centerInfluence * 0.10f
                }

                if (touchPosition != Offset.Unspecified) {
                    val dx = touchPosition.x - point.x
                    val dy = touchPosition.y - point.y
                    val distance = sqrt(dx * dx + dy * dy)

                    if (distance < interactionRadius) {
                        val influence =
                            1f - (distance / interactionRadius)

                        drawPosition = Offset(
                            x + dx * influence * 0.08f,
                            y + dy * influence * 0.08f
                        )

                        radius += baseRadius * influence * 0.7f
                        alpha += 0.30f * influence
                    }
                }

                drawCircle(
                    color = accent.copy(alpha = alpha),
                    radius = radius,
                    center = drawPosition
                )

                x += spacing
            }

            y += spacing
        }
    }
}