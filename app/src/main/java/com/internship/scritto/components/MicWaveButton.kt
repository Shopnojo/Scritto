package com.internship.scritto.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.internship.scritto.ui.theme.ScrittoAmber
import com.internship.scritto.ui.theme.ScrittoAmberBright
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private const val OPEN_DELAY_MS = 520L

/**
 * A plain amber microphone icon, no background. At rest it only "breathes" — a slow, soft
 * swell and glow — so it never looks like the mic is already live. Pressing it plays a short
 * "opening the mic" sequence: the microphone morphs into live level bars and a thin ripple
 * spreads out, then [onClick] is called.
 *
 * [size] is the touch target; the icon itself is drawn smaller inside it.
 */
@Composable
fun MicWaveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }

    // 0 = microphone, 1 = level bars
    val morph by animateFloatAsState(
        targetValue = if (opening) 1f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "micMorph"
    )

    val ripple = remember { Animatable(0f) }

    LaunchedEffect(opening) {
        if (opening) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(OPEN_DELAY_MS.toInt() + 160, easing = LinearEasing))
        }
    }

    val transition = rememberInfiniteTransition(label = "micIdle")

    // Very slow, very small: enough to feel alive, not enough to feel "listening".
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micBreath"
    )

    // Only used while opening.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "micBarsPhase"
    )

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Talk to Scritto" }
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !opening
            ) {
                scope.launch {
                    opening = true
                    delay(OPEN_DELAY_MS)
                    onClick()
                    opening = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(size * 0.66f)
                .graphicsLayer {
                    val s = if (opening) 1.1f else 0.96f + breath * 0.08f
                    scaleX = s
                    scaleY = s
                    alpha = if (opening) 1f else 0.78f + breath * 0.22f
                }
        ) {
            val radius = this.size.minDimension / 2f

            if (opening) {
                // ripple: a thin ring spreading past the icon
                drawCircle(
                    color = ScrittoAmberBright.copy(alpha = (1f - ripple.value) * 0.6f),
                    radius = radius * (1.0f + ripple.value * 0.9f),
                    center = center,
                    style = Stroke(width = 1.6.dp.toPx())
                )
            }

            drawMicrophone(color = ScrittoAmber.copy(alpha = 1f - morph))

            if (morph > 0.01f) {
                drawLevelBars(phase = phase, morph = morph)
            }
        }
    }
}

/** A classic microphone: capsule, cradle, stem and base. */
private fun DrawScope.drawMicrophone(color: Color) {
    if (color.alpha <= 0.01f) return

    val r = size.minDimension / 2f
    val stroke = r * 0.13f

    val capsuleWidth = r * 0.50f
    val capsuleHeight = r * 0.92f
    val capsuleTop = center.y - r * 0.92f

    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - capsuleWidth / 2f, capsuleTop),
        size = Size(capsuleWidth, capsuleHeight),
        cornerRadius = CornerRadius(capsuleWidth / 2f)
    )

    // cradle around the lower half of the capsule
    val cradleRadius = r * 0.56f
    val cradleCenterY = capsuleTop + capsuleHeight - capsuleWidth / 2f
    drawArc(
        color = color,
        startAngle = 12f,
        sweepAngle = 156f,
        useCenter = false,
        topLeft = Offset(center.x - cradleRadius, cradleCenterY - cradleRadius + r * 0.14f),
        size = Size(cradleRadius * 2f, cradleRadius * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )

    // stem and base
    val stemTop = cradleCenterY + cradleRadius + r * 0.14f - stroke / 2f
    val stemBottom = stemTop + r * 0.26f
    drawLine(color, Offset(center.x, stemTop), Offset(center.x, stemBottom), stroke, StrokeCap.Round)
    drawLine(
        color,
        Offset(center.x - r * 0.30f, stemBottom),
        Offset(center.x + r * 0.30f, stemBottom),
        stroke,
        StrokeCap.Round
    )
}

private fun DrawScope.drawLevelBars(phase: Float, morph: Float) {
    val r = size.minDimension / 2f
    val bars = 5
    val barWidth = r * 0.20f
    val gap = r * 0.16f
    val totalWidth = bars * barWidth + (bars - 1) * gap
    val maxHeight = r * 1.5f
    val minHeight = r * 0.34f

    for (i in 0 until bars) {
        val wave = 0.5f + 0.5f * sin(phase + i * 0.95f)
        val envelope = 1f - abs(i - (bars - 1) / 2f) / bars
        val height = (minHeight + (maxHeight - minHeight) * (wave * envelope).coerceIn(0f, 1f)) * morph
        val x = center.x - totalWidth / 2f + i * (barWidth + gap)

        drawRoundRect(
            color = ScrittoAmber.copy(alpha = morph),
            topLeft = Offset(x, center.y - height / 2f),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(barWidth / 2f)
        )
    }
}
