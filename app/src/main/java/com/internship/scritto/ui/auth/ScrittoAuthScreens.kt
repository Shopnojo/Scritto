package com.internship.scritto.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.internship.scritto.components.ScrittoMesh
import com.internship.scritto.ui.theme.ScrittoAmber
import com.internship.scritto.ui.theme.ScrittoBackground
import com.internship.scritto.ui.theme.ScrittoBorder
import com.internship.scritto.ui.theme.ScrittoBronze
import com.internship.scritto.ui.theme.ScrittoCream
import com.internship.scritto.ui.theme.ScrittoCreamBright
import com.internship.scritto.ui.theme.ScrittoSurface
import com.internship.scritto.ui.theme.ScrittoSurfaceElevated
import com.internship.scritto.ui.theme.ScrittoTextMuted
import com.internship.scritto.ui.theme.ScrittoTextSecondary
import com.internship.scritto.ui.theme.ScrittoTheme

/**
 * Presentation-only login screen. The caller owns all actions; this composable
 * deliberately contains no authentication, networking, or persistence logic.
 */
@Composable
fun ScrittoLoginScreen(
    onContinueWithGoogle: () -> Unit,
    modifier: Modifier = Modifier,
    showPreviewLabel: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScrittoBackground)
    ) {
        ScrittoMesh(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(34.dp))
            ScrittoWordmark()
            Spacer(Modifier.height(58.dp))

            Text(
                text = "A little more clarity.\nA lot more you.",
                color = ScrittoCreamBright,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 42.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Your notes, plans and ideas —\ngathered in one quiet space.",
                color = ScrittoTextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 25.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(38.dp))
            WelcomeNoteCard()
            Spacer(Modifier.height(34.dp))

            Button(
                onClick = onContinueWithGoogle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScrittoCreamBright,
                    contentColor = Color(0xFF171613)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 1.dp
                )
            ) {
                GoogleGlyph()
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Continue with Google",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "A simpler space for everything on your mind.",
                color = ScrittoTextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))
            if (showPreviewLabel) {
                Text(
                    text = "GOOGLE SIGN-IN · PREVIEW ONLY",
                    color = ScrittoTextMuted.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.4.sp
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/**
 * Presentation-only account confirmation screen. Pass real account details
 * once Google authentication is connected; no account is inferred here.
 */
@Composable
fun ScrittoAccountConfirmationScreen(
    displayName: String,
    email: String,
    onContinueWithAccount: () -> Unit,
    onChangeAccount: () -> Unit,
    modifier: Modifier = Modifier,
    photoUrl: String? = null,
    showPreviewLabel: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScrittoBackground)
    ) {
        ScrittoMesh(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            ScrittoWordmark()
            Spacer(Modifier.height(42.dp))

            Text(
                text = "ACCOUNT CONFIRMATION",
                color = ScrittoAmber,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Is this your\naccount?",
                color = ScrittoCreamBright,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 40.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Make sure you’re continuing with the right Google account.",
                color = ScrittoTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 23.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = ScrittoSurface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, ScrittoBorder)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(ScrittoSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Google profile picture",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = displayName.trim().firstOrNull()?.uppercase() ?: "?",
                                color = ScrittoCreamBright,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = displayName,
                        color = ScrittoCreamBright,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = email,
                        color = ScrittoTextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    if (showPreviewLabel) {
                        Spacer(Modifier.height(18.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = ScrittoSurfaceElevated
                        ) {
                            Text(
                                text = "SAMPLE ACCOUNT · PREVIEW ONLY",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                color = ScrittoTextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onContinueWithAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScrittoAmber,
                    contentColor = Color(0xFF17120B)
                )
            ) {
                Text(
                    text = "Continue with this account",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onChangeAccount),
                shape = RoundedCornerShape(18.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, ScrittoBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Change account",
                        color = ScrittoCream,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "You can change this choice before signing in.",
                color = ScrittoTextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ScrittoWordmark() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        ScrittoPageMark()
        Spacer(Modifier.width(11.dp))
        Text(
            text = "scritto",
            color = ScrittoCreamBright,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp
        )
    }
}

@Composable
private fun ScrittoPageMark() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(38.dp)) {
        val sx = size.width / 38f
        val sy = size.height / 38f
        drawRoundRect(
            color = ScrittoBronze,
            topLeft = androidx.compose.ui.geometry.Offset(3f * sx, 10f * sy),
            size = androidx.compose.ui.geometry.Size(22f * sx, 25f * sy),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * sx, 4f * sy)
        )
        drawRoundRect(
            color = ScrittoAmber,
            topLeft = androidx.compose.ui.geometry.Offset(8f * sx, 5f * sy),
            size = androidx.compose.ui.geometry.Size(23f * sx, 28f * sy),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * sx, 4f * sy)
        )
        drawRoundRect(
            color = ScrittoCream,
            topLeft = androidx.compose.ui.geometry.Offset(12f * sx, 2f * sy),
            size = androidx.compose.ui.geometry.Size(23f * sx, 29f * sy),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * sx, 4f * sy)
        )
        val fold = Path().apply {
            moveTo(27f * sx, 2f * sy)
            lineTo(35f * sx, 10f * sy)
            lineTo(27f * sx, 10f * sy)
            close()
        }
        drawPath(fold, color = ScrittoAmber)
        drawRoundRect(
            color = ScrittoBackground.copy(alpha = 0.72f),
            topLeft = androidx.compose.ui.geometry.Offset(17f * sx, 16f * sy),
            size = androidx.compose.ui.geometry.Size(13f * sx, 1.5f * sy),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * sx, 1f * sy)
        )
        drawRoundRect(
            color = ScrittoBackground.copy(alpha = 0.55f),
            topLeft = androidx.compose.ui.geometry.Offset(17f * sx, 21f * sy),
            size = androidx.compose.ui.geometry.Size(10f * sx, 1.5f * sy),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * sx, 1f * sy)
        )
    }
}

@Composable
private fun GoogleGlyph() {
    Surface(
        modifier = Modifier.size(23.dp),
        shape = CircleShape,
        color = Color.White
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "G",
                color = Color(0xFF4285F4),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WelcomeNoteCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ScrittoSurface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, ScrittoBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ScrittoAmber.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✳",
                    color = ScrittoAmber,
                    fontSize = 24.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "A space to think clearly",
                    color = ScrittoCream,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Capture it now. Find it when you need it.",
                    color = ScrittoTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * Interactive Android Studio preview for reviewing both screens without
 * adding an auth gate to the real app or pretending that sign-in succeeded.
 */
@Composable
fun ScrittoAuthDesignPreview() {
    var showConfirmation by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(ScrittoBackground)) {
        if (showConfirmation) {
            ScrittoAccountConfirmationScreen(
                displayName = "Alex Morgan",
                email = "alex.morgan@example.com",
                showPreviewLabel = true,
                onContinueWithAccount = {
                    notice = "Preview only — authentication is not connected."
                },
                onChangeAccount = {
                    showConfirmation = false
                    notice = null
                }
            )
        } else {
            ScrittoLoginScreen(
                showPreviewLabel = true,
                onContinueWithGoogle = {
                    // Preview navigation only; this does not launch Google OAuth.
                    showConfirmation = true
                    notice = null
                }
            )
        }

        if (notice != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                shape = RoundedCornerShape(16.dp),
                color = ScrittoSurfaceElevated,
                border = BorderStroke(1.dp, ScrittoBorder)
            ) {
                Text(
                    text = notice.orEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = ScrittoCream,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Preview(
    name = "Scritto Auth — Login",
    showBackground = true,
    backgroundColor = 0xFF0B0B0A,
    widthDp = 393,
    heightDp = 852
)
@Composable
private fun ScrittoAuthPreview() {
    ScrittoTheme {
        ScrittoAuthDesignPreview()
    }
}
