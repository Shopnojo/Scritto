package com.internship.scritto.screens

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatClear
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.internship.scritto.data.repository.ScrittoStore
import com.internship.scritto.notes.NoteRichText
import com.internship.scritto.ui.theme.ScrittoAmber
import com.internship.scritto.ui.theme.ScrittoBackground
import com.internship.scritto.ui.theme.ScrittoBackgroundElevated
import com.internship.scritto.ui.theme.ScrittoBorder
import com.internship.scritto.ui.theme.ScrittoCream
import com.internship.scritto.ui.theme.ScrittoCreamBright
import com.internship.scritto.ui.theme.ScrittoTextMuted
import com.internship.scritto.ui.theme.ScrittoTextSecondary
import kotlinx.coroutines.delay

@Composable
fun NoteEditorScreen(
    noteId: String,
    onBack: () -> Unit
) {
    val view = LocalView.current

    val note = remember(noteId) {
        ScrittoStore.getNote(noteId)
    }

    if (note == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrittoBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Note not found",
                color = ScrittoTextSecondary,
                fontSize = 16.sp
            )
        }

        return
    }

    var title by rememberSaveable(noteId) {
        mutableStateOf(note.title)
    }

    var content by remember(noteId) {
        mutableStateOf(
            NoteRichText.valueFrom(note.content, note.spans)
        )
    }

    var isPinned by rememberSaveable(noteId) {
        mutableStateOf(note.isPinned)
    }

    // The toolbar can temporarily take the pointer interaction away from
    // the editor. Keep the last real editor selection so formatting is
    // always applied to the text the user selected.
    var savedSelection by remember(noteId) {
        mutableStateOf<TextRange?>(null)
    }

    // Style armed with the caret only (e.g. tap Bold, then type). It is applied
    // to the next characters typed and dropped as soon as the caret moves.
    var pendingStyle by remember(noteId) {
        mutableStateOf<Int?>(null)
    }

    var isFocused by remember(noteId) {
        mutableStateOf(false)
    }

    /*
     * Keep the toolbar visible once editing has started.
     *
     * This is intentionally separate from isFocused because tapping
     * a toolbar button should not make the toolbar disappear.
     */
    var formattingVisible by remember(noteId) {
        mutableStateOf(false)
    }

    var saveState by remember(noteId) {
        mutableStateOf(SaveState.SAVED)
    }

    var textAlign by remember(noteId) {
        mutableStateOf(
            textAlignFromStorage(note.textAlign)
        )
    }

    val contentScrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    // --------------------------------------------------------------------
    // AUTOSAVE
    // --------------------------------------------------------------------

    /** True when what is on screen differs from what is stored. */
    fun hasUnsavedChanges(): Boolean {
        val stored = ScrittoStore.getNote(noteId) ?: return false

        return stored.title != title ||
            stored.content != content.text ||
            stored.textAlign != textAlignToStorage(textAlign) ||
            stored.spans != NoteRichText.toSpans(content.annotatedString)
    }

    fun saveNow() {
        if (!hasUnsavedChanges()) return

        ScrittoStore.updateNote(
            id = noteId,
            title = title,
            content = content.text,
            spans = NoteRichText.toSpans(content.annotatedString),
            textAlign = textAlignToStorage(textAlign)
        )
    }

    LaunchedEffect(title, content, textAlign) {
        // Caret moves and the initial composition change nothing worth saving.
        if (!hasUnsavedChanges()) {
            saveState = SaveState.SAVED
            return@LaunchedEffect
        }

        saveState = SaveState.SAVING

        delay(350)

        saveNow()

        saveState = SaveState.SAVED
    }

    // The debounce above is cancelled when the screen is left, so flush
    // whatever is pending. This also covers rotation and process teardown.
    DisposableEffect(noteId) {
        onDispose {
            saveNow()
        }
    }

    // Leaving the editor: save, and tidy away a note that was never written in.
    fun leave() {
        saveNow()

        if (title.isBlank() && content.text.isBlank()) {
            ScrittoStore.deleteNote(noteId)
        }

        onBack()
    }

    BackHandler {
        leave()
    }

    // --------------------------------------------------------------------
    // CARET / CONTENT SCROLL
    // --------------------------------------------------------------------

    // Follow the caret only while typing at the end of the note. Tapping or
    // dragging a selection elsewhere must not yank the page to the bottom.
    LaunchedEffect(content.text) {
        if (
            isFocused &&
            content.selection.collapsed &&
            content.selection.end == content.text.length
        ) {
            delay(40)

            contentScrollState.animateScrollTo(
                contentScrollState.maxValue
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrittoBackground)
            .imePadding()
    ) {

        // =================================================================
        // HEADER
        // =================================================================

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = 18.dp,
                    end = 18.dp,
                    top = 4.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable {
                        view.performHapticFeedback(
                            HapticFeedbackConstants.KEYBOARD_TAP
                        )

                        leave()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "‹",
                    color = ScrittoCreamBright,
                    fontSize = 38.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Crossfade(
                    targetState = saveState,
                    animationSpec = tween(
                        durationMillis = 180,
                        easing = FastOutSlowInEasing
                    ),
                    label = "save-state"
                ) { state ->

                    Text(
                        text = when (state) {
                            SaveState.SAVING -> "Saving…"
                            SaveState.SAVED -> "Saved"
                        },
                        color = when (state) {
                            SaveState.SAVING -> ScrittoTextMuted
                            SaveState.SAVED -> ScrittoAmber
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPinned) {
                                ScrittoAmber.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable {
                            isPinned = !isPinned
                            ScrittoStore.setPinned(
                                id = noteId,
                                pinned = isPinned
                            )

                            view.performHapticFeedback(
                                HapticFeedbackConstants.KEYBOARD_TAP
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) {
                            "Unpin note"
                        } else {
                            "Pin note"
                        },
                        tint = if (isPinned) {
                            ScrittoAmber
                        } else {
                            ScrittoCream
                        },
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        // =================================================================
        // WRITING AREA
        // =================================================================

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(contentScrollState)
                .padding(
                    start = 42.dp,
                    end = 32.dp,
                    top = 36.dp,
                    bottom = 36.dp
                )
        ) {

            // ----------------------------------------------------------------
            // TITLE
            // ----------------------------------------------------------------

            BasicTextField(
                value = title,
                onValueChange = {
                    title = it
                },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = ScrittoCreamBright,
                    fontSize = 31.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp
                ),
                cursorBrush = SolidColor(
                    ScrittoCreamBright
                ),
                singleLine = false,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        focusRequester.requestFocus()
                    }
                ),
                decorationBox = { innerTextField ->

                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        if (title.isEmpty()) {
                            Text(
                                text = "Untitled",
                                color = ScrittoTextMuted,
                                fontSize = 31.sp,
                                lineHeight = 38.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        innerTextField()
                    }
                }
            )

            Spacer(
                modifier = Modifier.height(26.dp)
            )

            // ----------------------------------------------------------------
            // BODY
            // ----------------------------------------------------------------

            BasicTextField(
                value = content,
                onValueChange = { emitted ->
                    // The text field hands back plain text (no spans) after any
                    // edit. Re-map the existing styles onto the new text so
                    // formatting stays put while typing, deleting or pasting.
                    val reconciled = NoteRichText.reconcile(
                        old = content,
                        new = emitted,
                        pending = pendingStyle
                    )

                    content = reconciled.value
                    pendingStyle = reconciled.pending

                    // Selection changes are delivered through TextFieldValue.
                    // Remember non-collapsed selections for toolbar actions.
                    if (!reconciled.value.selection.collapsed) {
                        savedSelection = reconciled.value.selection
                    } else if (isFocused) {
                        savedSelection = reconciled.value.selection
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->

                        isFocused = state.isFocused

                        if (state.isFocused) {
                            formattingVisible = true
                        }
                    },
                textStyle = TextStyle(
                    color = ScrittoCream,
                    fontSize = 18.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = textAlign
                ),
                cursorBrush = SolidColor(
                    ScrittoCreamBright
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default
                ),
                decorationBox = { innerTextField ->

                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        if (content.text.isEmpty()) {
                            Text(
                                text = "Start writing…",
                                color = ScrittoTextMuted,
                                fontSize = 18.sp,
                                lineHeight = 30.sp
                            )
                        }

                        innerTextField()
                    }
                }
            )
        }

        // =================================================================
        // FORMATTING TOOLBAR
        // =================================================================

        AnimatedVisibility(
            visible = formattingVisible,
            enter =
                fadeIn(
                    animationSpec = tween(180)
                ) +
                        expandVertically(
                            animationSpec = tween(
                                220,
                                easing = FastOutSlowInEasing
                            )
                        ),
            exit =
                fadeOut(
                    animationSpec = tween(120)
                ) +
                        shrinkVertically(
                            animationSpec = tween(
                                180,
                                easing = FastOutSlowInEasing
                            )
                        )
        ) {

            FormattingToolbar(
                value = content,
                savedSelection = savedSelection,
                pendingStyle = pendingStyle,
                textAlign = textAlign,
                onValueChanged = { updatedValue, pending ->
                    content = updatedValue
                    pendingStyle = pending
                    savedSelection = updatedValue.selection
                },
                onTextAlignChanged = {
                    textAlign = it
                }
            )
        }
    }
}

// ========================================================================
// ALIGNMENT STORAGE
// ========================================================================

private fun textAlignToStorage(
    alignment: TextAlign
): String {
    return when (alignment) {
        TextAlign.Center -> "center"
        TextAlign.Right -> "right"
        else -> "left"
    }
}

private fun textAlignFromStorage(
    alignment: String
): TextAlign {
    return when (alignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> TextAlign.Left
    }
}

// ========================================================================
// SAVE STATE
// ========================================================================

private enum class SaveState {
    SAVING,
    SAVED
}

// ========================================================================
// FORMATTING TOOLBAR
// ========================================================================

@Composable
private fun FormattingToolbar(
    value: TextFieldValue,
    savedSelection: TextRange?,
    pendingStyle: Int?,
    textAlign: TextAlign,
    onValueChanged: (TextFieldValue, Int?) -> Unit,
    onTextAlignChanged: (TextAlign) -> Unit
) {
    val view = LocalView.current
    val scrollState = rememberScrollState()

    // Restore the last editor selection before every formatting operation.
    val formattingValue = remember(value, savedSelection) {
        val length = value.text.length
        val selection = savedSelection?.let {
            TextRange(
                it.start.coerceIn(0, length),
                it.end.coerceIn(0, length)
            )
        } ?: value.selection

        value.copy(
            selection = selection
        )
    }

    fun tap() {
        view.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP
        )
    }

    fun applyStyle(style: Int) {
        tap()

        val result = NoteRichText.toggle(
            formattingValue,
            style,
            pendingStyle
        )

        onValueChanged(result.value, result.pending)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                end = 12.dp,
                bottom = 8.dp
            )
            .clip(
                RoundedCornerShape(18.dp)
            )
            .background(
                ScrittoBackgroundElevated
            )
            .border(
                1.dp,
                ScrittoBorder.copy(alpha = 0.85f),
                RoundedCornerShape(18.dp)
            )
            .horizontalScroll(scrollState)
            .padding(
                horizontal = 5.dp,
                vertical = 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // BOLD
        FormattingButton(
            text = "B",
            selected = NoteRichText.isActive(
                formattingValue,
                NoteRichText.BOLD,
                pendingStyle
            ),
            onClick = { applyStyle(NoteRichText.BOLD) }
        )

        // ITALIC
        FormattingButton(
            text = "I",
            selected = NoteRichText.isActive(
                formattingValue,
                NoteRichText.ITALIC,
                pendingStyle
            ),
            italic = true,
            onClick = { applyStyle(NoteRichText.ITALIC) }
        )

        // UNDERLINE
        FormattingButton(
            text = "U",
            selected = NoteRichText.isActive(
                formattingValue,
                NoteRichText.UNDERLINE,
                pendingStyle
            ),
            underline = true,
            onClick = { applyStyle(NoteRichText.UNDERLINE) }
        )

        // STRIKETHROUGH
        FormattingButton(
            text = "S",
            selected = NoteRichText.isActive(
                formattingValue,
                NoteRichText.STRIKE,
                pendingStyle
            ),
            strike = true,
            onClick = { applyStyle(NoteRichText.STRIKE) }
        )

        ToolbarDivider()

        // BULLET
        FormattingButton(
            text = "•",
            onClick = {
                tap()

                onValueChanged(
                    NoteRichText.toggleBullet(formattingValue),
                    null
                )
            }
        )

        ToolbarDivider()

        // LEFT
        FormattingButton(
            icon = Icons.AutoMirrored.Outlined.FormatAlignLeft,
            description = "Align left",
            selected = textAlign == TextAlign.Left,
            onClick = {
                tap()

                onTextAlignChanged(
                    TextAlign.Left
                )
            }
        )

        // CENTER
        FormattingButton(
            icon = Icons.Outlined.FormatAlignCenter,
            description = "Align center",
            selected = textAlign == TextAlign.Center,
            onClick = {
                tap()

                onTextAlignChanged(
                    TextAlign.Center
                )
            }
        )

        // RIGHT
        FormattingButton(
            icon = Icons.AutoMirrored.Outlined.FormatAlignRight,
            description = "Align right",
            selected = textAlign == TextAlign.Right,
            onClick = {
                tap()

                onTextAlignChanged(
                    TextAlign.Right
                )
            }
        )

        ToolbarDivider()

        // CLEAR FORMATTING
        FormattingButton(
            icon = Icons.Outlined.FormatClear,
            description = "Clear formatting",
            onClick = {
                tap()

                val result = NoteRichText.clearFormatting(formattingValue)

                onValueChanged(result.value, result.pending)
            }
        )
    }
}

// ========================================================================
// FORMATTING BUTTON
// ========================================================================

@Composable
private fun FormattingButton(
    text: String = "",
    icon: ImageVector? = null,
    description: String? = null,
    selected: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    strike: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (selected) {
                    ScrittoAmber.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {

        val tint = if (selected) {
            ScrittoAmber
        } else {
            ScrittoCream
        }

        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                text = text,
                color = tint,
                fontSize = 17.sp,
                fontWeight = if (italic) {
                    FontWeight.Normal
                } else {
                    FontWeight.Bold
                },
                fontStyle = if (italic) {
                    FontStyle.Italic
                } else {
                    FontStyle.Normal
                },
                textDecoration = when {
                    underline -> TextDecoration.Underline
                    strike -> TextDecoration.LineThrough
                    else -> null
                }
            )
        }
    }
}

// ========================================================================
// DIVIDER
// ========================================================================

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(24.dp)
            .background(
                ScrittoBorder.copy(alpha = 0.8f)
            )
    )
}
