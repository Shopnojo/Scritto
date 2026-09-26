package com.internship.scritto.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.internship.scritto.ai.AssistantAction
import com.internship.scritto.ai.AssistantSounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.internship.scritto.ai.VoiceInput
import com.internship.scritto.ai.VoiceSpeaker
import com.internship.scritto.notes.NoteRichText
import com.internship.scritto.ui.theme.ScrittoAmber
import com.internship.scritto.ui.theme.ScrittoAmberBright
import com.internship.scritto.ui.theme.ScrittoBackground
import com.internship.scritto.ui.theme.ScrittoBorder
import com.internship.scritto.ui.theme.ScrittoCream
import com.internship.scritto.ui.theme.ScrittoCreamBright
import com.internship.scritto.ui.theme.ScrittoOrange
import com.internship.scritto.ui.theme.ScrittoSurface
import com.internship.scritto.ui.theme.ScrittoTextMuted
import com.internship.scritto.ui.theme.ScrittoTextSecondary

private enum class AssistantPhase { IDLE, LISTENING, THINKING, SPEAKING }

private val suggestions = listOf(
    "What's on my schedule today?",
    "Remind me to call mom tomorrow at 6pm",
    "Add a class on Monday at 10am",
    "Move my task to tomorrow at 9am"
)

/**
 * Full-screen voice assistant: tap the orb, say what you want, and Scritto AI does it
 * inside the app (notes, tasks, schedule, files) and answers out loud.
 */
@Composable
fun VoiceAssistantOverlay(
    thinking: Boolean,
    replyText: String?,
    replyId: Int,
    actions: List<AssistantAction>,
    onUtterance: (String) -> Unit,
    onActionClick: (AssistantAction) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var transcript by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var problem by remember { mutableStateOf<String?>(null) }
    var muted by rememberSaveable { mutableStateOf(false) }

    // Replies that existed before the overlay opened belong to the text chat.
    val firstReplyId = remember { replyId }
    val hasFreshReply = replyId != firstReplyId && !replyText.isNullOrBlank()

    val latestOnUtterance by rememberUpdatedState(onUtterance)
    val scope = rememberCoroutineScope()

    val input = remember {
        VoiceInput(
            context = context,
            onPartial = { transcript = it },
            onResult = {
                listening = false
                level = 0f
                transcript = it
                if (!muted) AssistantSounds.play(AssistantSounds.Cue.HEARD)
                latestOnUtterance(it)
            },
            onError = {
                listening = false
                level = 0f
                problem = it
                if (!muted) AssistantSounds.play(AssistantSounds.Cue.PROBLEM)
            },
            onLevel = { level = it }
        )
    }

    val speaker = remember { VoiceSpeaker(context) { speaking = it } }

    DisposableEffect(Unit) {
        onDispose {
            input.release()
            speaker.shutdown()
        }
    }

    fun startListening() {
        speaker.stop()
        problem = null
        transcript = ""
        listening = true

        // Let the chime finish first so the microphone doesn't hear it.
        scope.launch {
            if (!muted) {
                AssistantSounds.play(AssistantSounds.Cue.LISTEN)
                delay(AssistantSounds.durationMs(AssistantSounds.Cue.LISTEN) + 40L)
            }
            if (listening) input.start()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            problem = "I need microphone access to listen. Allow it in Settings, or just type to me."
        }
    }

    fun listenWithPermission() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Start listening as soon as the assistant opens.
    LaunchedEffect(Unit) { listenWithPermission() }

    // Once a request is on its way the microphone is done, however it was triggered.
    LaunchedEffect(thinking) {
        if (thinking) {
            input.release()
            listening = false
            level = 0f
        }
    }

    // Speak every new reply.
    LaunchedEffect(replyId) {
        if (hasFreshReply && !muted) {
            AssistantSounds.play(AssistantSounds.Cue.DONE)
            // Speak only once the chime has fully faded, so the two never overlap.
            delay(AssistantSounds.durationMs(AssistantSounds.Cue.DONE) + 60L)
            speaker.speak(NoteRichText.stripMarkup(replyText.orEmpty()))
        }
    }

    val phase = when {
        listening -> AssistantPhase.LISTENING
        thinking -> AssistantPhase.THINKING
        speaking -> AssistantPhase.SPEAKING
        else -> AssistantPhase.IDLE
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrittoBackground)
            // Swallow touches so nothing behind the overlay reacts.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- top bar ------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoundIconButton(
                    icon = if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                    description = if (muted) "Unmute voice replies" else "Mute voice replies",
                    onClick = {
                        muted = !muted
                        if (muted) speaker.stop()
                    }
                )

                Text(
                    text = "Scritto Assistant",
                    color = ScrittoTextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                RoundIconButton(
                    icon = Icons.Outlined.Close,
                    description = "Close assistant",
                    onClick = onDismiss
                )
            }

            Spacer(Modifier.height(20.dp))

            // ---- orb ----------------------------------------------------
            AssistantOrb(
                phase = phase,
                level = level,
                onClick = {
                    when (phase) {
                        AssistantPhase.LISTENING -> input.stop()
                        AssistantPhase.SPEAKING -> speaker.stop()
                        AssistantPhase.THINKING -> Unit
                        AssistantPhase.IDLE -> listenWithPermission()
                    }
                }
            )

            Text(
                text = when (phase) {
                    AssistantPhase.LISTENING -> "Listening…"
                    AssistantPhase.THINKING -> "Working on it…"
                    AssistantPhase.SPEAKING -> "Tap to stop"
                    AssistantPhase.IDLE -> "Tap the orb to talk"
                },
                color = ScrittoTextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(18.dp))

            // ---- conversation ------------------------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (transcript.isNotBlank()) {
                    Text(
                        text = "“$transcript”",
                        color = ScrittoCreamBright,
                        fontSize = 22.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                problem?.let {
                    Text(
                        text = it,
                        color = ScrittoAmberBright,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                }

                if (hasFreshReply && phase != AssistantPhase.LISTENING) {
                    Text(
                        text = NoteRichText.renderMarkup(replyText.orEmpty()),
                        color = ScrittoCream,
                        fontSize = 17.sp,
                        lineHeight = 25.sp,
                        textAlign = TextAlign.Center
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        actions.forEach { action ->
                            ActionChip(action = action, onClick = { onActionClick(action) })
                        }
                    }
                }

                if (transcript.isBlank() && !hasFreshReply && problem == null && phase != AssistantPhase.THINKING) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Try saying",
                            color = ScrittoTextMuted,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )

                        suggestions.forEach { suggestion ->
                            Text(
                                text = "“$suggestion”",
                                color = ScrittoTextSecondary,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        speaker.stop()
                                        input.release()
                                        listening = false
                                        problem = null
                                        transcript = suggestion
                                        latestOnUtterance(suggestion)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// ORB
// ============================================================================

@Composable
private fun AssistantOrb(
    phase: AssistantPhase,
    level: Float,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbBreath"
    )

    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                if (phase == AssistantPhase.SPEAKING) 1100 else 1900,
                easing = LinearEasing
            )
        ),
        label = "orbRing"
    )

    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing)
        ),
        label = "orbSpin"
    )

    val smoothLevel by animateFloatAsState(
        targetValue = if (phase == AssistantPhase.LISTENING) level else 0f,
        animationSpec = tween(120),
        label = "orbLevel"
    )

    Box(
        modifier = Modifier
            .size(230.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val base = size.minDimension * 0.24f

            val energy = when (phase) {
                AssistantPhase.LISTENING -> 0.10f + smoothLevel * 0.28f
                AssistantPhase.SPEAKING -> 0.08f + breath * 0.12f
                AssistantPhase.THINKING -> 0.04f
                AssistantPhase.IDLE -> 0.02f + breath * 0.03f
            }
            val coreRadius = base * (1f + energy)

            // soft outer glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ScrittoAmber.copy(alpha = 0.34f),
                        ScrittoAmber.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius * 2.6f
                ),
                radius = coreRadius * 2.6f,
                center = center
            )

            // expanding rings while listening / speaking
            if (phase == AssistantPhase.LISTENING || phase == AssistantPhase.SPEAKING) {
                repeat(2) { index ->
                    val progress = (ring + index * 0.5f) % 1f
                    drawCircle(
                        color = ScrittoAmberBright.copy(alpha = (1f - progress) * 0.32f),
                        radius = coreRadius * (1.05f + progress * 0.85f),
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ScrittoAmberBright, ScrittoAmber, ScrittoOrange),
                    center = Offset(center.x - coreRadius * 0.25f, center.y - coreRadius * 0.3f),
                    radius = coreRadius * 1.35f
                ),
                radius = coreRadius,
                center = center
            )

            // thinking: a light sweeping around the core
            if (phase == AssistantPhase.THINKING) {
                val arcRadius = coreRadius * 1.28f
                drawArc(
                    color = ScrittoCreamBright.copy(alpha = 0.85f),
                    startAngle = spin,
                    sweepAngle = 96f,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        if (phase == AssistantPhase.IDLE) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = "Talk to Scritto",
                tint = ScrittoBackground,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

// ============================================================================
// SMALL PARTS
// ============================================================================

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(ScrittoSurface.copy(alpha = 0.9f))
            .border(1.dp, ScrittoBorder.copy(alpha = 0.85f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = ScrittoCream,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun ActionChip(
    action: AssistantAction,
    onClick: () -> Unit
) {
    Text(
        text = "✓  ${action.label}",
        color = ScrittoAmberBright,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ScrittoAmber.copy(alpha = 0.12f))
            .border(1.dp, ScrittoAmber.copy(alpha = 0.32f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}
