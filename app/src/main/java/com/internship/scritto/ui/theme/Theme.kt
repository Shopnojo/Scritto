package com.internship.scritto.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorErrorContainer = Color(0xFF3A1D19)

private val ScrittoDarkColorScheme = darkColorScheme(
    primary = ScrittoAmber,
    onPrimary = ScrittoBlack,
    primaryContainer = ScrittoAmberContainer,
    onPrimaryContainer = ScrittoCreamBright,

    secondary = ScrittoBronze,
    onSecondary = ScrittoCreamBright,
    secondaryContainer = ScrittoBronzeContainer,
    onSecondaryContainer = ScrittoCream,

    tertiary = ScrittoOrange,
    onTertiary = ScrittoCreamBright,
    tertiaryContainer = ScrittoBronzeContainer,
    onTertiaryContainer = ScrittoCream,

    background = ScrittoBackground,
    onBackground = ScrittoCream,

    surface = ScrittoSurface,
    onSurface = ScrittoCream,

    surfaceVariant = ScrittoSurfaceVariant,
    onSurfaceVariant = ScrittoTextSecondary,

    outline = ScrittoBorder,
    outlineVariant = ScrittoBorderSubtle,

    error = ScrittoError,
    onError = ScrittoBlack,
    errorContainer = ColorErrorContainer,
    onErrorContainer = ScrittoCreamBright
)

@Composable
fun ScrittoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ScrittoDarkColorScheme,
        typography = Typography,
        content = content
    )
}