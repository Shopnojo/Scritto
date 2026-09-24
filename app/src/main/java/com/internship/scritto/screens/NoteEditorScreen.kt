package com.internship.scritto.screens

import android.view.HapticFeedbackConstants
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
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import com.internship.scritto.data.model.NoteSpan
import com.internship.scritto.data.repository.ScrittoStore
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
            textFieldValueFromNote(note)
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
        mutableStateOf(TextAlign.Left)
    }

    val contentScrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    // --------------------------------------------------------------------
    // AUTOSAVE
    // --------------------------------------------------------------------

    LaunchedEffect(title, content, textAlign) {
        saveState = SaveState.SAVING

        delay(350)

        ScrittoStore.updateNote(
            id = noteId,
            title = title,
            content = content.text,
            spans = noteSpansFromAnnotatedString(
                content.annotatedString
            ),
            textAlign = textAlignToStorage(textAlign)
        )

        saveState = SaveState.SAVED
    }

    // --------------------------------------------------------------------
    // CARET / CONTENT SCROLL
    // --------------------------------------------------------------------

    LaunchedEffect(content.text, content.selection) {
        if (isFocused) {
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

                        onBack()
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
                onValueChange = {
                    // BasicTextField can emit a plain TextFieldValue when the
                    // editor regains/loses focus around a toolbar tap. If the
                    // text itself has not changed, never let that plain value
                    // erase the AnnotatedString spans we just applied.
                    val updatedValue = if (
                        it.text == content.text &&
                        it.annotatedString.spanStyles.isEmpty() &&
                        content.annotatedString.spanStyles.isNotEmpty()
                    ) {
                        content.copy(
                            selection = it.selection,
                            composition = it.composition
                        )
                    } else {
                        it
                    }

                    content = updatedValue

                    // Selection changes are delivered through TextFieldValue.
                    // Remember non-collapsed selections for toolbar actions.
                    if (!updatedValue.selection.collapsed) {
                        savedSelection = updatedValue.selection
                    } else if (isFocused) {
                        savedSelection = updatedValue.selection
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
                textAlign = textAlign,
                onValueChanged = { updatedValue ->
                    content = updatedValue
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
// RICH TEXT PERSISTENCE
// ========================================================================

private fun textFieldValueFromNote(
    note: com.internship.scritto.data.model.Note
): TextFieldValue {
    val builder = AnnotatedString.Builder(note.content)

    note.spans.forEach { span ->
        val start = span.start.coerceIn(0, note.content.length)
        val end = span.end.coerceIn(start, note.content.length)

        if (start >= end) return@forEach

        builder.addStyle(
            SpanStyle(
                fontWeight = if (span.bold) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
                fontStyle = if (span.italic) {
                    FontStyle.Italic
                } else {
                    FontStyle.Normal
                },
                textDecoration = when {
                    span.underline && span.strike -> {
                        TextDecoration.combine(
                            listOf(
                                TextDecoration.Underline,
                                TextDecoration.LineThrough
                            )
                        )
                    }

                    span.underline -> TextDecoration.Underline
                    span.strike -> TextDecoration.LineThrough
                    else -> TextDecoration.None
                }
            ),
            start,
            end
        )
    }

    return TextFieldValue(
        annotatedString = builder.toAnnotatedString(),
        selection = TextRange(note.content.length)
    )
}

private fun noteSpansFromAnnotatedString(
    value: AnnotatedString
): List<NoteSpan> {
    return value.spanStyles.mapNotNull { range ->
        val bold = range.item.fontWeight == FontWeight.Bold
        val italic = range.item.fontStyle == FontStyle.Italic
        val decoration = range.item.textDecoration

        val underline = decoration?.contains(TextDecoration.Underline) == true
        val strike = decoration?.contains(TextDecoration.LineThrough) == true

        if (!bold && !italic && !underline && !strike) {
            null
        } else {
            NoteSpan(
                start = range.start,
                end = range.end,
                bold = bold,
                italic = italic,
                underline = underline,
                strike = strike
            )
        }
    }
}

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
    textAlign: TextAlign,
    onValueChanged: (TextFieldValue) -> Unit,
    onTextAlignChanged: (TextAlign) -> Unit
) {
    val view = LocalView.current
    val scrollState = rememberScrollState()

    // Restore the last editor selection before every formatting operation.
    val formattingValue = remember(value, savedSelection) {
        val selection = savedSelection ?: value.selection

        value.copy(
            selection = selection
        )
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
            selected = hasBold(formattingValue),
            onClick = {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )

                onValueChanged(
                    toggleBold(formattingValue)
                )
            }
        )

        // ITALIC
        FormattingButton(
            text = "I",
            selected = hasItalic(formattingValue),
            italic = true,
            onClick = {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )

                onValueChanged(
                    toggleItalic(formattingValue)
                )
            }
        )

        // UNDERLINE
        FormattingButton(
            text = "U",
            selected = hasUnderline(formattingValue),
            underline = true,
            onClick = {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )

                onValueChanged(
                    toggleUnderline(formattingValue)
                )
            }
        )

        // STRIKETHROUGH
        FormattingButton(
            text = "S",
            selected = hasStrike(formattingValue),
            strike = true,
            onClick = {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )

                onValueChanged(
                    toggleStrike(formattingValue)
                )
            }
        )

        ToolbarDivider()

        // BULLET
        FormattingButton(
            text = "•",
            onClick = {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )

                onValueChanged(
                    insertBullet(formattingValue)
                )
            }
        )

        ToolbarDivider()

        // LEFT
        FormattingButton(
            text = "≡",
            selected = textAlign == TextAlign.Left,
            onClick = {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )

                onTextAlignChanged(
                    TextAlign.Left
                )
            }
        )

        // CENTER
        FormattingButton(
            text = "≡",
            selected = textAlign == TextAlign.Center,
            onClick = {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )

                onTextAlignChanged(
                    TextAlign.Center
                )
            }
        )

        // RIGHT
        FormattingButton(
            text = "≡",
            selected = textAlign == TextAlign.Right,
            onClick = {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )

                onTextAlignChanged(
                    TextAlign.Right
                )
            }
        )

        ToolbarDivider()

        // MORE
        FormattingButton(
            text = "•••",
            onClick = {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP
                )
            }
        )
    }
}

// ========================================================================
// FORMATTING BUTTON
// ========================================================================

@Composable
private fun FormattingButton(
    text: String,
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

        Text(
            text = text,
            color = if (selected) {
                ScrittoAmber
            } else {
                ScrittoCream
            },
            fontSize = if (text == "•••") {
                13.sp
            } else {
                17.sp
            },
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

// ========================================================================
// FORMATTING HELPERS
// ========================================================================

private fun hasBold(
    value: TextFieldValue
): Boolean {
    return selectedStyleExists(value) {
        it.fontWeight == FontWeight.Bold
    }
}

private fun hasItalic(
    value: TextFieldValue
): Boolean {
    return selectedStyleExists(value) {
        it.fontStyle == FontStyle.Italic
    }
}

private fun hasUnderline(
    value: TextFieldValue
): Boolean {
    return selectedStyleExists(value) {
        it.textDecoration == TextDecoration.Underline
    }
}

private fun hasStrike(
    value: TextFieldValue
): Boolean {
    return selectedStyleExists(value) {
        it.textDecoration == TextDecoration.LineThrough
    }
}

private fun selectedStyleExists(
    value: TextFieldValue,
    predicate: (SpanStyle) -> Boolean
): Boolean {

    val selection = value.selection

    if (selection.collapsed) {
        return false
    }

    return value.annotatedString.spanStyles.any { range ->
        range.start <= selection.start &&
                range.end >= selection.end &&
                predicate(range.item)
    }
}

// ========================================================================
// BOLD
// ========================================================================

private fun toggleBold(
    value: TextFieldValue
): TextFieldValue {
    val selection = value.selection
    if (selection.collapsed) return value

    val active = hasBold(value)

    val builder = AnnotatedString.Builder(value.annotatedString)

    if (active) {
        builder.addStyle(
            SpanStyle(fontWeight = FontWeight.Normal),
            selection.start,
            selection.end
        )
    } else {
        builder.addStyle(
            SpanStyle(fontWeight = FontWeight.Bold),
            selection.start,
            selection.end
        )
    }

    return value.copy(
        annotatedString = builder.toAnnotatedString(),
        selection = selection
    )
}

// ========================================================================
// ITALIC
// ========================================================================

private fun toggleItalic(
    value: TextFieldValue
): TextFieldValue {
    val selection = value.selection
    if (selection.collapsed) return value

    val active = hasItalic(value)

    val builder = AnnotatedString.Builder(value.annotatedString)

    builder.addStyle(
        SpanStyle(
            fontStyle = if (active) FontStyle.Normal else FontStyle.Italic
        ),
        selection.start,
        selection.end
    )

    return value.copy(
        annotatedString = builder.toAnnotatedString(),
        selection = selection
    )
}

// ========================================================================
// UNDERLINE
// ========================================================================

private fun toggleUnderline(
    value: TextFieldValue
): TextFieldValue {
    val selection = value.selection
    if (selection.collapsed) return value

    val active = hasUnderline(value)

    val builder = AnnotatedString.Builder(value.annotatedString)

    builder.addStyle(
        SpanStyle(
            textDecoration = if (active) {
                TextDecoration.None
            } else {
                TextDecoration.Underline
            }
        ),
        selection.start,
        selection.end
    )

    return value.copy(
        annotatedString = builder.toAnnotatedString(),
        selection = selection
    )
}

// ========================================================================
// STRIKETHROUGH
// ========================================================================

private fun toggleStrike(
    value: TextFieldValue
): TextFieldValue {
    val selection = value.selection
    if (selection.collapsed) return value

    val active = hasStrike(value)

    val builder = AnnotatedString.Builder(value.annotatedString)

    builder.addStyle(
        SpanStyle(
            textDecoration = if (active) {
                TextDecoration.None
            } else {
                TextDecoration.LineThrough
            }
        ),
        selection.start,
        selection.end
    )

    return value.copy(
        annotatedString = builder.toAnnotatedString(),
        selection = selection
    )
}

// ========================================================================
// BULLET
// ========================================================================

private fun insertBullet(
    value: TextFieldValue
): TextFieldValue {
    val cursor = value.selection.start
    val text = value.text

    val lineStart = text.lastIndexOf(
        '\n',
        startIndex = (cursor - 1).coerceAtLeast(0)
    ) + 1

    val bullet = "• "

    val builder = AnnotatedString.Builder()

    builder.append(
        value.annotatedString.subSequence(
            0,
            lineStart
        )
    )

    builder.append(bullet)

    builder.append(
        value.annotatedString.subSequence(
            TextRange(
                lineStart,
                value.text.length
            )
        )
    )

    return TextFieldValue(
        annotatedString = builder.toAnnotatedString(),
        selection = TextRange(
            cursor + bullet.length
        )
    )
}
