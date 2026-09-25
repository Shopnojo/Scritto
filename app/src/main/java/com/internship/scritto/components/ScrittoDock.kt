package com.internship.scritto.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

data class DockItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val dockItems = listOf(
    DockItem("Home", Icons.Outlined.Home),
    DockItem("Notes", Icons.Outlined.Description),
    DockItem("Tasks", Icons.Outlined.CheckCircle),
    DockItem("AI", Icons.Outlined.AutoAwesome)
)

@Composable
fun ScrittoDock(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    onPlusClicked: () -> Unit,
    createMenuExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    val transition = updateTransition(
        targetState = createMenuExpanded,
        label = "dockPlusTransition"
    )

    val plusRotation by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = 320,
                easing = FastOutSlowInEasing
            )
        },
        label = "dockPlusRotation"
    ) { expanded ->
        if (expanded) 45f else 0f
    }

    val plusBackground by transition.animateColor(
        transitionSpec = {
            tween(
                durationMillis = 320,
                easing = FastOutSlowInEasing
            )
        },
        label = "dockPlusBackground"
    ) { expanded ->
        if (expanded) colorScheme.primary else colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                color = colorScheme.surface.copy(alpha = 0.78f)
            )
            .border(
                width = 1.dp,
                color = colorScheme.onSurface.copy(alpha = 0.10f),
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        Row(
            modifier = Modifier.size(
                width = 226.dp,
                height = 58.dp
            ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dockItems.take(2).forEachIndexed { index, item ->
                DockNavigationItem(
                    item = item,
                    selected = selectedIndex == index,
                    onClick = {
                        if (selectedIndex != index) {
                            view.performHapticFeedback(
                                HapticFeedbackConstants.KEYBOARD_TAP
                            )
                        }
                        onItemSelected(index)
                    }
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(plusBackground)
                    .clickable {
                        onPlusClicked()
                        view.performHapticFeedback(
                            HapticFeedbackConstants.LONG_PRESS
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = if (createMenuExpanded) {
                        "Close create menu"
                    } else {
                        "Create"
                    },
                    tint = colorScheme.background,
                    modifier = Modifier
                        .size(23.dp)
                        .graphicsLayer {
                            rotationZ = plusRotation
                        }
                )
            }

            dockItems.drop(2).forEachIndexed { offsetIndex, item ->
                val index = offsetIndex + 2

                DockNavigationItem(
                    item = item,
                    selected = selectedIndex == index,
                    onClick = {
                        if (selectedIndex != index) {
                            view.performHapticFeedback(
                                HapticFeedbackConstants.KEYBOARD_TAP
                            )
                        }
                        onItemSelected(index)
                    }
                )
            }
        }
    }
}

@Composable
private fun DockNavigationItem(
    item: DockItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (selected) {
                colorScheme.onSurface
            } else {
                colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            },
            modifier = Modifier.size(20.dp)
        )
    }
}
