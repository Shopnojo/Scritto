package com.internship.scritto.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.internship.scritto.components.ScrittoDock
import com.internship.scritto.components.ScrittoMesh
import com.internship.scritto.data.repository.ScrittoStore
import com.internship.scritto.screens.HomeScreen
import com.internship.scritto.screens.NoteEditorScreen
import com.internship.scritto.screens.NotesScreen

private const val HOME_ROUTE = "home"
private const val NOTES_ROUTE = "notes"
private const val NOTE_EDITOR_ROUTE = "note/{noteId}"

private data class CreateAction(
    val label: String,
    val symbol: String
)

private val createActions = listOf(
    CreateAction("New Note", "▤"),
    CreateAction("New Task", "✓"),
    CreateAction("Add Event", "◷"),
    CreateAction("Add Class", "□"),
    CreateAction("Import File", "↓")
)

@Composable
fun ScrittoNavigation() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val isEditingNote = currentRoute == NOTE_EDITOR_ROUTE
    val density = LocalDensity.current

    var selectedDockIndex by remember {
        mutableStateOf(0)
    }

    var createMenuExpanded by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScrittoMesh(
            modifier = Modifier.fillMaxSize()
        )

        NavHost(
            navController = navController,
            startDestination = HOME_ROUTE,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(HOME_ROUTE) {
                HomeScreen(
                    onNoteSelected = { noteId ->
                        navController.navigate("note/$noteId")
                    }
                )
            }

            composable(NOTES_ROUTE) {
                NotesScreen(
                    onNoteSelected = { noteId ->
                        navController.navigate("note/$noteId")
                    }
                )
            }

            composable(
                route = NOTE_EDITOR_ROUTE,
                arguments = listOf(
                    navArgument("noteId") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val noteId =
                    backStackEntry.arguments?.getString("noteId")

                if (noteId != null) {
                    NoteEditorScreen(
                        noteId = noteId,
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }

        if (!isEditingNote) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .requiredWidth(170.dp)
                        .padding(bottom = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    createActions
                        .asReversed()
                        .forEachIndexed { reversedIndex, action ->
                            val originalIndex =
                                createActions.lastIndex - reversedIndex

                            val transition = updateTransition(
                                targetState = createMenuExpanded,
                                label = "action_${action.label}"
                            )

                            val delay = originalIndex * 45

                            val alpha by transition.animateFloat(
                                transitionSpec = {
                                    tween(
                                        durationMillis = 220,
                                        delayMillis = delay,
                                        easing = FastOutSlowInEasing
                                    )
                                },
                                label = "alpha_${action.label}"
                            ) { expanded ->
                                if (expanded) 1f else 0f
                            }

                            val scale by transition.animateFloat(
                                transitionSpec = {
                                    tween(
                                        durationMillis = 240,
                                        delayMillis = delay,
                                        easing = FastOutSlowInEasing
                                    )
                                },
                                label = "scale_${action.label}"
                            ) { expanded ->
                                if (expanded) 1f else 0.82f
                            }

                            val translationY by transition.animateFloat(
                                transitionSpec = {
                                    tween(
                                        durationMillis = 280,
                                        delayMillis = delay,
                                        easing = FastOutSlowInEasing
                                    )
                                },
                                label = "translation_${action.label}"
                            ) { expanded ->
                                if (expanded) 0f else 45f
                            }

                            Row(
                                modifier = Modifier
                                    .graphicsLayer {
                                        this.alpha = alpha
                                        scaleX = scale
                                        scaleY = scale
                                        this.translationY =
                                            with(density) {
                                                translationY.dp.toPx()
                                            }
                                    }
                                    .requiredWidth(150.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surface
                                            .copy(alpha = 0.92f)
                                    )
                                    .clickable(
                                        enabled = createMenuExpanded
                                    ) {
                                        when (action.label) {
                                            "New Note" -> {
                                                val note =
                                                    ScrittoStore.createNote()

                                                createMenuExpanded = false

                                                navController.navigate(
                                                    "note/${note.id}"
                                                )
                                            }
                                        }
                                    }
                                    .padding(
                                        horizontal = 14.dp,
                                        vertical = 9.dp
                                    ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(9.dp),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Text(
                                    text = action.symbol,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Text(
                                    text = action.label,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                }

                ScrittoDock(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    selectedIndex = selectedDockIndex,
                    onItemSelected = { index ->
                        selectedDockIndex = index

                        when (index) {
                            0 -> {
                                createMenuExpanded = false
                                navController.navigate(HOME_ROUTE) {
                                    popUpTo(HOME_ROUTE) {
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                }
                            }

                            1 -> {
                                createMenuExpanded = false
                                navController.navigate(NOTES_ROUTE) {
                                    launchSingleTop = true
                                }
                            }

                            2 -> {
                                createMenuExpanded = false
                                // Tasks will be added next.
                            }

                            3 -> {
                                createMenuExpanded = false
                                // Files will be added next.
                            }
                        }
                    },
                    onPlusClicked = {
                        createMenuExpanded = !createMenuExpanded
                    },
                    createMenuExpanded = createMenuExpanded
                )
            }
        }
    }
}
