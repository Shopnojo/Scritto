package com.internship.scritto.navigation

import android.app.Activity
import android.net.Uri

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.internship.scritto.ai.AssistantSession
import com.internship.scritto.components.ScrittoDock
import com.internship.scritto.components.ScrittoMesh
import com.internship.scritto.data.repository.ScrittoStore
import com.internship.scritto.notifications.NotificationRouter
import com.internship.scritto.notifications.NotificationTarget
import com.internship.scritto.screens.AiChatScreen
import com.internship.scritto.screens.HomeScreen
import com.internship.scritto.screens.NoteEditorScreen
import com.internship.scritto.screens.FilesScreen
import com.internship.scritto.screens.NotesScreen
import com.internship.scritto.screens.ScheduleScreen
import com.internship.scritto.screens.TaskScreen
import com.internship.scritto.screens.DocumentPreviewScreen
import com.internship.scritto.screens.DocumentEditorScreen
import com.internship.scritto.ui.splash.ScrittoSplashScreen

private const val HOME_ROUTE = "home"
private const val NOTES_ROUTE = "notes"
private const val TASK_ROUTE = "tasks"
private const val SCHEDULE_ROUTE = "schedule/{openComposer}"
private const val AI_ROUTE = "ai"
private const val FILES_ROUTE = "files"
private const val NOTE_EDITOR_ROUTE = "note/{noteId}"
private const val DOCUMENT_ROUTE = "document/{name}/{uri}"
private const val DOCUMENT_EDITOR_ROUTE = "document-editor/{name}/{uri}/{convertMode}"

private fun scheduleRoute(openComposer: Boolean = false) = "schedule/$openComposer"

private data class CreateAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val createActions = listOf(
    CreateAction("New Note", Icons.Outlined.Description),
    CreateAction("New Task", Icons.Outlined.CheckCircle),
    CreateAction("Add Event", Icons.Outlined.Event),
    CreateAction("Add Class", Icons.Outlined.CalendarMonth),
    CreateAction("Import File", Icons.AutoMirrored.Outlined.NoteAdd)
)

@Composable
fun ScrittoNavigation() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val isEditingNote = currentRoute == NOTE_EDITOR_ROUTE
    val isDocumentScreen = currentRoute == DOCUMENT_ROUTE || currentRoute == DOCUMENT_EDITOR_ROUTE
    val density = LocalDensity.current
    val view = LocalView.current

    DisposableEffect(isDocumentScreen) {
        val activity = view.context as? Activity
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (isDocumentScreen) {
            controller?.hide(WindowInsetsCompat.Type.navigationBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.navigationBars())
        }

        onDispose {
            controller?.show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    var splashVisible by rememberSaveable { mutableStateOf(true) }

    val contentAlpha by animateFloatAsState(
        targetValue = if (splashVisible) 0f else 1f,
        animationSpec = tween(
            durationMillis = 360,
            easing = FastOutSlowInEasing
        ),
        label = "content_alpha"
    )

    val contentScale by animateFloatAsState(
        targetValue = if (splashVisible) 0.985f else 1f,
        animationSpec = tween(
            durationMillis = 420,
            easing = FastOutSlowInEasing
        ),
        label = "content_scale"
    )

    var selectedDockIndex by remember {
        mutableStateOf(0)
    }

    var createMenuExpanded by remember {
        mutableStateOf(false)
    }

    // A tapped notification asks for a specific screen. Open it, then clear the request.
    val notificationTarget = NotificationRouter.pending
    LaunchedEffect(notificationTarget) {
        val target = notificationTarget ?: return@LaunchedEffect
        NotificationRouter.consume()
        createMenuExpanded = false

        when (target) {
            NotificationTarget.HOME -> {
                selectedDockIndex = 0
                navController.navigate(HOME_ROUTE) {
                    popUpTo(HOME_ROUTE) { inclusive = false }
                    launchSingleTop = true
                }
            }

            NotificationTarget.TASKS -> {
                selectedDockIndex = 2
                navController.navigate(TASK_ROUTE) {
                    launchSingleTop = true
                }
            }

            NotificationTarget.SCHEDULE -> {
                selectedDockIndex = -1
                navController.navigate(scheduleRoute()) {
                    launchSingleTop = true
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    scaleX = contentScale
                    scaleY = contentScale
                }
        ) {
            ScrittoMesh(
            modifier = Modifier.fillMaxSize(),
            aiReactive = currentRoute == AI_ROUTE
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
                    },
                    onNewNote = {
                        val createdNote = ScrittoStore.createNote()
                        createMenuExpanded = false
                        navController.navigate("note/${createdNote.id}")
                    },
                    onSchedule = {
                        createMenuExpanded = false
                        selectedDockIndex = -1
                        navController.navigate(scheduleRoute()) {
                            launchSingleTop = true
                        }
                    },
                    onEvent = {
                        createMenuExpanded = false
                        selectedDockIndex = -1
                        navController.navigate(scheduleRoute(true))
                    },
                    onVoice = {
                        createMenuExpanded = false
                        AssistantSession.pendingVoice = true
                        selectedDockIndex = 3
                        navController.navigate(AI_ROUTE) {
                            launchSingleTop = true
                        }
                    },
                    onAskAssistant = { prompt ->
                        createMenuExpanded = false
                        AssistantSession.pendingPrompt = prompt
                        selectedDockIndex = 3
                        navController.navigate(AI_ROUTE) {
                            launchSingleTop = true
                        }
                    },
                    onFiles = {
                        createMenuExpanded = false
                        navController.navigate(FILES_ROUTE) {
                            launchSingleTop = true
                        }
                    },
                    onTasks = {
                        createMenuExpanded = false
                        selectedDockIndex = 2
                        navController.navigate(TASK_ROUTE) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = "pdf-editor/{name}/{uri}",
                arguments = listOf(
                    navArgument("name") { type = NavType.StringType },
                    navArgument("uri") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val name = backStackEntry.arguments?.getString("name")
                val uri = backStackEntry.arguments?.getString("uri")
                if (name != null && uri != null) {
                    PdfEditorWorkspaceScreen(
                        name = name,
                        uri = uri,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(
                route = DOCUMENT_EDITOR_ROUTE,
                arguments = listOf(
                    navArgument("name") { type = NavType.StringType },
                    navArgument("uri") { type = NavType.StringType },
                    navArgument("convertMode") { type = NavType.BoolType }
                )
            ) { backStackEntry ->
                val name = backStackEntry.arguments?.getString("name")
                val uri = backStackEntry.arguments?.getString("uri")
                val convertMode = backStackEntry.arguments?.getBoolean("convertMode") == true
                if (name != null && uri != null) {
                    DocumentEditorScreen(
                        name = name,
                        uri = uri,
                        convertMode = convertMode,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable(FILES_ROUTE) {
                FilesScreen(
                    onFileSelected = { file ->
                        navController.navigate(
                            "document/" + Uri.encode(file.name) + "/" + Uri.encode(file.uri)
                        )
                    }
                )
            }

            composable(
                route = DOCUMENT_ROUTE,
                arguments = listOf(
                    navArgument("name") { type = NavType.StringType },
                    navArgument("uri") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val name = backStackEntry.arguments?.getString("name")
                val uri = backStackEntry.arguments?.getString("uri")
                if (name != null && uri != null) {
                    DocumentPreviewScreen(
                        name = name,
                        uri = uri,
                        onBack = { navController.popBackStack() },
                        onEdit = {
                            if (com.internship.scritto.documents.DocumentTypes.describe(name, uri).kind == com.internship.scritto.documents.DocumentKind.PDF) {
                                navController.navigate(
                                    "pdf-editor/" + Uri.encode(name) + "/" + Uri.encode(uri)
                                )
                            } else {
                                navController.navigate(
                                    "document-editor/" + Uri.encode(name) + "/" + Uri.encode(uri) + "/false"
                                )
                            }
                        },
                        onConvert = {
                            navController.navigate(
                                "document-editor/" + Uri.encode(name) + "/" + Uri.encode(uri) + "/true"
                            )
                        },
                        onAskAssistant = {
                            // The chat picks the file up as an attachment and waits for the user's question.
                            AssistantSession.pendingFile = ScrittoStore.ImportedFile(name, uri)
                            selectedDockIndex = 3
                            navController.navigate(AI_ROUTE) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }

            composable(NOTES_ROUTE) {
                NotesScreen(
                    onNoteSelected = { noteId ->
                        navController.navigate("note/$noteId")
                    }
                )
            }

            composable(TASK_ROUTE) {
                TaskScreen()
            }

            composable(
                route = SCHEDULE_ROUTE,
                arguments = listOf(navArgument("openComposer") { type = NavType.BoolType })
            ) { backStackEntry ->
                ScheduleScreen(
                    initialOpen = backStackEntry.arguments?.getBoolean("openComposer") == true
                )
            }

            composable(AI_ROUTE) {
                AiChatScreen(
                    onHome = {
                        selectedDockIndex = 0
                        navController.navigate(HOME_ROUTE) {
                            popUpTo(HOME_ROUTE) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    },
                    onNotes = {
                        selectedDockIndex = 1
                        navController.navigate(NOTES_ROUTE) {
                            launchSingleTop = true
                        }
                    },
                    onCreateNote = {
                        val note = ScrittoStore.createNote()
                        navController.navigate("note/${note.id}")
                    },
                    onSchedule = {
                        selectedDockIndex = -1
                        navController.navigate(scheduleRoute()) {
                            launchSingleTop = true
                        }
                    },
                    onTasks = {
                        selectedDockIndex = 2
                        navController.navigate(TASK_ROUTE) {
                            launchSingleTop = true
                        }
                    },
                    onFiles = {
                        navController.navigate(FILES_ROUTE) {
                            launchSingleTop = true
                        }
                    },
                    onOpenNote = { noteId ->
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
                        },
                        onOpenFile = { file ->
                            navController.navigate(
                                "document/" + Uri.encode(file.name) + "/" + Uri.encode(file.uri)
                            )
                        }
                    )
                }
            }
        }

        if (!isEditingNote && currentRoute != AI_ROUTE && !isDocumentScreen) {
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
                                    // Fully hidden rows must not exist for touch purposes: even at
                                    // zero opacity they used to swallow taps on the bottom-centre of
                                    // every screen (e.g. the "Create task" / "Save" buttons).
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val hidden = !createMenuExpanded && alpha < 0.01f

                                        layout(
                                            if (hidden) 0 else placeable.width,
                                            if (hidden) 0 else placeable.height
                                        ) {
                                            placeable.place(0, 0)
                                        }
                                    }
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
                                            "New Task" -> {
                                                createMenuExpanded = false
                                                selectedDockIndex = 2
                                                navController.navigate(TASK_ROUTE) {
                                                    launchSingleTop = true
                                                }
                                            }
                                            "Add Event", "Add Class" -> {
                                                createMenuExpanded = false
                                                selectedDockIndex = -1
                                                navController.navigate(scheduleRoute(true))
                                            }
                                            "Import File" -> {
                                                createMenuExpanded = false
                                                navController.navigate(FILES_ROUTE) {
                                                    launchSingleTop = true
                                                }
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
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = action.label,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
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
                                navController.navigate(TASK_ROUTE) {
                                    launchSingleTop = true
                                }
                            }

                            3 -> {
                                createMenuExpanded = false
                                navController.navigate(AI_ROUTE) {
                                    launchSingleTop = true
                                }
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

        if (splashVisible) {
            ScrittoSplashScreen(
                onFinished = { splashVisible = false }
            )
        }
    }
}
@Composable
private fun PdfEditorWorkspaceScreen(
    name: String,
    uri: String,
    onBack: () -> Unit
) {
    val descriptor = com.internship.scritto.documents.DocumentTypes.describe(name, uri)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        "Back",
                        tint = com.internship.scritto.ui.theme.ScrittoCreamBright
                    )
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(name, color = com.internship.scritto.ui.theme.ScrittoCreamBright, style = MaterialTheme.typography.titleMedium)
                    Text("PDF editor", color = com.internship.scritto.ui.theme.ScrittoTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            com.internship.scritto.documents.PdfAnnotationWorkspace(
                modifier = Modifier.weight(1f),
                descriptor = descriptor
            )
        }
    }
}
