package com.internship.scritto.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset

data class DockItem(
    val label: String,
    val symbol: String
)

private val dockItems = listOf(
    DockItem("Home", "⌂"),
    DockItem("Notes", "▤"),
    DockItem("Tasks", "✓"),
    DockItem("Schedule", "◷"),
    DockItem("Files", "□")
)

@Composable
fun ScrittoDock(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    val itemSize = 48.dp
    val indicatorSize = 42.dp
    val itemSpacing = 2.dp

    val indicatorOffset by animateDpAsState(
        targetValue = (itemSize + itemSpacing) * selectedIndex,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessLow
        ),
        label = "dockIndicatorOffset"
    )

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(
                color = colorScheme.surface.copy(alpha = 0.72f)
            )
            .border(
                width = 1.dp,
                color = colorScheme.onSurface.copy(alpha = 0.12f),
                shape = CircleShape
            ),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(
                    width = (itemSize * dockItems.size) +
                            (itemSpacing * (dockItems.size - 1)),
                    height = itemSize
                )
        ) {

            // One continuous animated selection indicator
            Box(
                modifier = Modifier
                    .size(indicatorSize)
                    .align(Alignment.CenterStart)
                    .offset(x = indicatorOffset + 3.dp)
                    .clip(CircleShape)
                    .background(
                        colorScheme.primary.copy(alpha = 0.18f)
                    )
            )

            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {

                dockItems.forEachIndexed { index, item ->

                    val selected = selectedIndex == index

                    Box(
                        modifier = Modifier
                            .size(itemSize)
                            .clickable {
                                if (!selected) {
                                    view.performHapticFeedback(
                                        HapticFeedbackConstants.KEYBOARD_TAP
                                    )
                                }

                                onItemSelected(index)
                            },
                        contentAlignment = Alignment.Center
                    ) {

                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.symbol,
                                color = if (selected) {
                                    colorScheme.primary
                                } else {
                                    colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.85f
                                    )
                                },
                                fontSize = 20.sp,
                                fontWeight = if (selected) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}