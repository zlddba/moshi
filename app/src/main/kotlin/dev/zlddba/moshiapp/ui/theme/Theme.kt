package dev.zlddba.moshiapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = MoshiPrimaryDark,
    onPrimary = MoshiOnPrimaryDark,
    primaryContainer = MoshiPrimaryContainerDark,
    onPrimaryContainer = MoshiOnPrimaryContainerDark,
    secondary = MoshiSecondaryDark,
    onSecondary = MoshiOnSecondaryDark,
    secondaryContainer = MoshiSecondaryContainerDark,
    onSecondaryContainer = MoshiOnSecondaryContainerDark,
    tertiary = MoshiTertiaryDark,
    onTertiary = MoshiOnTertiaryDark,
    tertiaryContainer = MoshiTertiaryContainerDark,
    onTertiaryContainer = MoshiOnTertiaryContainerDark,
    background = MoshiBackgroundDark,
    onBackground = MoshiOnBackgroundDark,
    surface = MoshiSurfaceDark,
    onSurface = MoshiOnSurfaceDark,
    surfaceVariant = MoshiSurfaceVariantDark,
    onSurfaceVariant = MoshiOnSurfaceVariantDark,
    outline = MoshiOutlineDark,
    outlineVariant = MoshiOutlineVariantDark,
    error = MoshiErrorDark,
    onError = MoshiOnErrorDark,
    errorContainer = MoshiErrorContainerDark,
    onErrorContainer = MoshiOnErrorContainerDark,
    surfaceBright = MoshiSurfaceBrightDark,
    surfaceDim = MoshiSurfaceDimDark,
    surfaceContainerLowest = MoshiSurfaceContainerLowestDark,
    surfaceContainerLow = MoshiSurfaceContainerLowDark,
    surfaceContainer = MoshiSurfaceContainerDark,
    surfaceContainerHigh = MoshiSurfaceContainerHighDark,
    surfaceContainerHighest = MoshiSurfaceContainerHighestDark
)

private val LightColorScheme = lightColorScheme(
    primary = MoshiPrimaryLight,
    onPrimary = MoshiOnPrimaryLight,
    primaryContainer = MoshiPrimaryContainerLight,
    onPrimaryContainer = MoshiOnPrimaryContainerLight,
    secondary = MoshiSecondaryLight,
    onSecondary = MoshiOnSecondaryLight,
    secondaryContainer = MoshiSecondaryContainerLight,
    onSecondaryContainer = MoshiOnSecondaryContainerLight,
    tertiary = MoshiTertiaryLight,
    onTertiary = MoshiOnTertiaryLight,
    tertiaryContainer = MoshiTertiaryContainerLight,
    onTertiaryContainer = MoshiOnTertiaryContainerLight,
    background = MoshiBackgroundLight,
    onBackground = MoshiOnBackgroundLight,
    surface = MoshiSurfaceLight,
    onSurface = MoshiOnSurfaceLight,
    surfaceVariant = MoshiSurfaceVariantLight,
    onSurfaceVariant = MoshiOnSurfaceVariantLight,
    outline = MoshiOutlineLight,
    outlineVariant = MoshiOutlineVariantLight,
    error = MoshiErrorLight,
    onError = MoshiOnErrorLight,
    errorContainer = MoshiErrorContainerLight,
    onErrorContainer = MoshiOnErrorContainerLight,
    surfaceBright = MoshiSurfaceBrightLight,
    surfaceDim = MoshiSurfaceDimLight,
    surfaceContainerLowest = MoshiSurfaceContainerLowestLight,
    surfaceContainerLow = MoshiSurfaceContainerLowLight,
    surfaceContainer = MoshiSurfaceContainerLight,
    surfaceContainerHigh = MoshiSurfaceContainerHighLight,
    surfaceContainerHighest = MoshiSurfaceContainerHighestLight
)

@Composable
fun MoshiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
