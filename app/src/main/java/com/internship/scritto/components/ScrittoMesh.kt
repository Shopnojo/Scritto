package com.internship.scritto.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
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
import kotlin.math.sqrt

@Composable
fun ScrittoMesh(
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary

    val spacing = with(density) { 28.dp.toPx() }
    val baseRadius = with(density) { 1.1.dp.toPx() }
    val interactionRadius = with(density) { 120.dp.toPx() }

    var touchPosition by remember {
        mutableStateOf(Offset.Unspecified)
    }

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