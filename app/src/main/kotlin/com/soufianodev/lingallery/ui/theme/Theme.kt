package com.soufianodev.lingallery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = DarkPalette.PRIMARY, onPrimary = DarkPalette.ON_PRIMARY,
    primaryContainer = DarkPalette.PRIMARY_CONTAINER,
    onPrimaryContainer = DarkPalette.ON_PRIMARY_CONTAINER,
    secondary = DarkPalette.SECONDARY, onSecondary = DarkPalette.ON_SECONDARY,
    secondaryContainer = DarkPalette.SECONDARY_CONTAINER,
    onSecondaryContainer = DarkPalette.ON_SECONDARY_CONTAINER,
    tertiary = DarkPalette.PRIMARY, onTertiary = DarkPalette.ON_PRIMARY,
    tertiaryContainer = DarkPalette.PRIMARY_CONTAINER,
    onTertiaryContainer = DarkPalette.ON_PRIMARY_CONTAINER,
    error = DarkPalette.ERROR, onError = DarkPalette.ON_ERROR,
    errorContainer = DarkPalette.ERROR_CONTAINER,
    onErrorContainer = DarkPalette.ON_ERROR_CONTAINER,
    background = DarkPalette.BACKGROUND, onBackground = DarkPalette.ON_SURFACE,
    surface = DarkPalette.SURFACE, onSurface = DarkPalette.ON_SURFACE,
    surfaceVariant = DarkPalette.SURFACE_VARIANT,
    onSurfaceVariant = DarkPalette.ON_SURFACE_VARIANT,
    outline = DarkPalette.OUTLINE, outlineVariant = DarkPalette.OUTLINE_VARIANT,
    scrim = DarkPalette.SCRIM,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPalette.PRIMARY, onPrimary = LightPalette.ON_PRIMARY,
    primaryContainer = LightPalette.PRIMARY_CONTAINER,
    onPrimaryContainer = LightPalette.ON_PRIMARY_CONTAINER,
    secondary = LightPalette.SECONDARY, onSecondary = LightPalette.ON_SECONDARY,
    secondaryContainer = LightPalette.SECONDARY_CONTAINER,
    onSecondaryContainer = LightPalette.ON_SECONDARY_CONTAINER,
    tertiary = LightPalette.PRIMARY, onTertiary = LightPalette.ON_PRIMARY,
    tertiaryContainer = LightPalette.PRIMARY_CONTAINER,
    onTertiaryContainer = LightPalette.ON_PRIMARY_CONTAINER,
    error = LightPalette.ERROR, onError = LightPalette.ON_ERROR,
    errorContainer = LightPalette.ERROR_CONTAINER,
    onErrorContainer = LightPalette.ON_ERROR_CONTAINER,
    background = LightPalette.BACKGROUND, onBackground = LightPalette.ON_SURFACE,
    surface = LightPalette.SURFACE, onSurface = LightPalette.ON_SURFACE,
    surfaceVariant = LightPalette.SURFACE_VARIANT,
    onSurfaceVariant = LightPalette.ON_SURFACE_VARIANT,
    outline = LightPalette.OUTLINE, outlineVariant = LightPalette.OUTLINE_VARIANT,
    scrim = LightPalette.SCRIM,
)

@Composable
fun LinGalleryTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme) { content() }
}
