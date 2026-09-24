package com.internship.scritto.ui.splash

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOGO_SIZE = 300f

@Composable
fun ScrittoSplashScreen(
    onFinished: () -> Unit
) {
    val view = LocalView.current
    val density = LocalDensity.current.density

    val bottomY = remember { Animatable(-420f) }
    val middleY = remember { Animatable(-420f) }
    val topY = remember { Animatable(-420f) }

    val bottomRotation = remember { Animatable(-5.5f) }
    val middleRotation = remember { Animatable(4.5f) }
    val topRotation = remember { Animatable(-3.5f) }

    val bottomScale = remember { Animatable(0.96f) }
    val middleScale = remember { Animatable(0.96f) }
    val topScale = remember { Animatable(0.96f) }

    val overlayAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                delay(80)
                landPage(bottomY, bottomRotation, bottomScale)
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }

            launch {
                delay(400)
                landPage(middleY, middleRotation, middleScale)
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }

            launch {
                delay(720)
                landPage(topY, topRotation, topScale)
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                delay(170)
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }

        delay(260)
        overlayAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(220)
        )
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF0B0B0A))
            .alpha(overlayAlpha.value),
        contentAlignment = Alignment.Center
    ) {
        SplashPage(
            modifier = Modifier.graphicsLayer {
                translationY = bottomY.value * density
                rotationZ = bottomRotation.value
                scaleX = bottomScale.value
                scaleY = bottomScale.value
            },
            page = SplashPageType.Bottom
        )

        SplashPage(
            modifier = Modifier.graphicsLayer {
                translationY = middleY.value * density
                rotationZ = middleRotation.value
                scaleX = middleScale.value
                scaleY = middleScale.value
            },
            page = SplashPageType.Middle
        )

        SplashPage(
            modifier = Modifier.graphicsLayer {
                translationY = topY.value * density
                rotationZ = topRotation.value
                scaleX = topScale.value
                scaleY = topScale.value
            },
            page = SplashPageType.Top
        )
    }
}

private suspend fun landPage(
    y: Animatable<Float, AnimationVector1D>,
    rotation: Animatable<Float, AnimationVector1D>,
    scale: Animatable<Float, AnimationVector1D>
) {
    coroutineScope {
        launch {
            y.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 260,
                    easing = FastOutLinearInEasing
                )
            )
            y.animateTo(
                targetValue = -6f,
                animationSpec = tween(65)
            )
            y.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.68f,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }

        launch {
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }

        launch {
            scale.animateTo(
                targetValue = 1.015f,
                animationSpec = tween(250)
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }
}

private enum class SplashPageType { Bottom, Middle, Top }

@Composable
private fun SplashPage(
    modifier: Modifier,
    page: SplashPageType
) {
    Canvas(
        modifier = modifier.size(LOGO_SIZE.dp)
    ) {
        when (page) {
            SplashPageType.Bottom -> drawBasePage(0.43f, 0.98f)
            SplashPageType.Middle -> drawBasePage(0.29f, 0.82f)
            SplashPageType.Top -> drawTopPage()
        }
    }
}

private fun DrawScope.drawBasePage(
    topFraction: Float,
    bottomFraction: Float
) {
    val path = roundedDiamondPath(
        width = size.width,
        height = size.height,
        top = size.height * topFraction,
        right = size.width * 0.93f,
        bottom = size.height * bottomFraction,
        left = size.width * 0.07f
    )

    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color(0xFF171411),
                androidx.compose.ui.graphics.Color(0xFF33261E),
                androidx.compose.ui.graphics.Color(0xFFC56D1E)
            ),
            start = androidx.compose.ui.geometry.Offset(30f, 70f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.72f)
        )
    )
}

private fun DrawScope.drawTopPage() {
    val page = roundedDiamondPath(
        width = size.width,
        height = size.height,
        top = size.height * 0.08f,
        right = size.width * 0.93f,
        bottom = size.height * 0.65f,
        left = size.width * 0.07f
    )

    drawPath(
        path = page,
        brush = Brush.linearGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color(0xFFE9DDCD),
                androidx.compose.ui.graphics.Color(0xFF8C7560),
                androidx.compose.ui.graphics.Color(0xFF3B2D24)
            ),
            start = androidx.compose.ui.geometry.Offset(20f, 80f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.68f)
        )
    )

    val fold = Path().apply {
        moveTo(size.width * 0.52f, size.height * 0.16f)
        cubicTo(
            size.width * 0.68f, size.height * 0.20f,
            size.width * 0.48f, size.height * 0.32f,
            size.width * 0.49f, size.height * 0.46f
        )
        cubicTo(
            size.width * 0.49f, size.height * 0.57f,
            size.width * 0.53f, size.height * 0.62f,
            size.width * 0.49f, size.height * 0.65f
        )
        lineTo(size.width * 0.93f, size.height * 0.37f)
        close()
    }

    drawPath(
        path = fold,
        brush = Brush.linearGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color(0xFFFFF5E7),
                androidx.compose.ui.graphics.Color(0xFFFFB844),
                androidx.compose.ui.graphics.Color(0xFFE28B22)
            ),
            start = androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.20f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.48f)
        )
    )
}

private fun roundedDiamondPath(
    width: Float,
    height: Float,
    top: Float,
    right: Float,
    bottom: Float,
    left: Float
): Path {
    val cx = width / 2f
    val cy = (top + bottom) / 2f
    val halfX = (right - left) / 2f
    val halfY = (bottom - top) / 2f
    val r = width * 0.045f

    return Path().apply {
        moveTo(cx, top + r)
        cubicTo(
            cx + r,
            top,
            cx + halfX - r,
            cy - halfY * 0.1f,
            right - r,
            cy - r
        )
        cubicTo(right, cy, right, cy + r, right - r, cy + r)
        cubicTo(
            cx + halfX * 0.25f,
            cy + halfY - r,
            cx + r,
            bottom,
            cx,
            bottom - r
        )
        cubicTo(
            cx - r,
            bottom,
            cx - halfX + r,
            cy + halfY * 0.1f,
            left + r,
            cy + r
        )
        cubicTo(left, cy, left, cy - r, left + r, cy - r)
        cubicTo(
            cx - halfX * 0.25f,
            cy - halfY + r,
            cx - r,
            top,
            cx,
            top + r
        )
        close()
    }
}
