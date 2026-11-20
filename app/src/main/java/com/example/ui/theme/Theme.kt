package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.ThemeMode

@Immutable
data class HearLinkSemanticColors(
    val excellent: Color,
    val excellentContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val isDark: Boolean,
)

val LocalHearLinkColors = staticCompositionLocalOf {
    HearLinkSemanticColors(
        excellent = StatusExcellentLight,
        excellentContainer = StatusExcellentContainerLight,
        warning = StatusWarningLight,
        warningContainer = StatusWarningContainerLight,
        error = StatusErrorLight,
        errorContainer = StatusErrorContainerLight,
        isDark = false,
    )
}

private val LightColorScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = TealOnPrimaryLight,
    primaryContainer = TealPrimaryContainerLight,
    onPrimaryContainer = TealOnPrimaryContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = SlateOnSecondaryLight,
    secondaryContainer = SlateSecondaryContainerLight,
    onSecondaryContainer = SlateOnSecondaryContainerLight,
    tertiary = ClinicalBlueTertiaryLight,
    onTertiary = ClinicalBlueOnTertiaryLight,
    tertiaryContainer = ClinicalBlueContainerLight,
    onTertiaryContainer = ClinicalBlueOnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = StatusErrorLight,
    onError = Color.White,
    errorContainer = StatusErrorContainerLight,
    onErrorContainer = Color(0xFF410002),
)

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealPrimaryContainerDark,
    onPrimaryContainer = TealOnPrimaryContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = SlateOnSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = SlateOnSecondaryContainerDark,
    tertiary = ClinicalBlueTertiaryDark,
    onTertiary = ClinicalBlueOnTertiaryDark,
    tertiaryContainer = ClinicalBlueContainerDark,
    onTertiaryContainer = ClinicalBlueOnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = StatusErrorDark,
    onError = Color(0xFF690005),
    errorContainer = StatusErrorContainerDark,
    onErrorContainer = Color(0xFFFFDAD6),
)

val HearLinkShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun HearLinkTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    val semanticColors = if (useDark) {
        HearLinkSemanticColors(
            excellent = StatusExcellentDark,
            excellentContainer = StatusExcellentContainerDark,
            warning = StatusWarningDark,
            warningContainer = StatusWarningContainerDark,
            error = StatusErrorDark,
            errorContainer = StatusErrorContainerDark,
            isDark = true,
        )
    } else {
        HearLinkSemanticColors(
            excellent = StatusExcellentLight,
            excellentContainer = StatusExcellentContainerLight,
            warning = StatusWarningLight,
            warningContainer = StatusWarningContainerLight,
            error = StatusErrorLight,
            errorContainer = StatusErrorContainerLight,
            isDark = false,
        )
    }

    CompositionLocalProvider(LocalHearLinkColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HearLinkTypography,
            shapes = HearLinkShapes,
            content = content,
        )
    }
}
