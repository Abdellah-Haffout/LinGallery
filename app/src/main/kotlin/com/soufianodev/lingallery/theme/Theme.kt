package com.soufianodev.lingallery.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Convert Color to HSL
fun Color.toHsl(): FloatArray {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    var h: Float
    val s: Float
    val l = (max + min) / 2f

    if (max == min) {
        h = 0f
        s = 0f
    } else {
        val d = max - min
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        h /= 6f
    }
    return floatArrayOf(h * 360f, s, l)
}

// Convert HSL back to Color
fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    fun hueToRgb(t: Float): Float {
        var r = t
        if (r < 0f) r += 1f
        if (r > 1f) r -= 1f
        if (r < 1f/6f) return p + (q - p) * 6f * r
        if (r < 1f/2f) return q
        if (r < 2f/3f) return p + (q - p) * (2f/3f - r) * 6f
        return p
    }
    val r = hueToRgb(h / 360f + 1f/3f).coerceIn(0f, 1f)
    val g = hueToRgb(h / 360f).coerceIn(0f, 1f)
    val b = hueToRgb(h / 360f - 1f/3f).coerceIn(0f, 1f)
    return Color(r, g, b, alpha)
}

// Generate dynamic light or dark color scheme
fun generateColorScheme(seed: Color, isDark: Boolean): ColorScheme {
    val hsl = seed.toHsl()
    val h = hsl[0]
    val s = hsl[1]

    if (isDark) {
        val primary = hslToColor(h, s.coerceIn(0.4f, 0.8f), 0.75f)
        val onPrimary = Color(0xFF0F1718)
        val primaryContainer = hslToColor(h, s.coerceIn(0.3f, 0.6f), 0.25f)
        val onPrimaryContainer = hslToColor(h, s.coerceIn(0.4f, 0.8f), 0.9f)

        val secondary = hslToColor(h, s.coerceIn(0.1f, 0.3f), 0.6f)
        val onSecondary = Color(0xFF1E2730)
        val secondaryContainer = hslToColor(h, s.coerceIn(0.1f, 0.3f), 0.3f)
        val onSecondaryContainer = hslToColor(h, s.coerceIn(0.1f, 0.3f), 0.8f)

        val background = hslToColor(h, s.coerceIn(0.04f, 0.10f), 0.07f)
        val surface = hslToColor(h, s.coerceIn(0.05f, 0.12f), 0.10f)
        val surfaceVariant = hslToColor(h, s.coerceIn(0.08f, 0.15f), 0.14f)
        val onSurface = hslToColor(h, s.coerceIn(0.05f, 0.15f), 0.90f)
        val onSurfaceVariant = hslToColor(h, s.coerceIn(0.1f, 0.25f), 0.75f)
        val outline = hslToColor(h, s.coerceIn(0.1f, 0.25f), 0.35f)
        val outlineVariant = hslToColor(h, s.coerceIn(0.08f, 0.2f), 0.18f)

        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = primary,
            onTertiary = onPrimary,
            tertiaryContainer = primaryContainer,
            onTertiaryContainer = onPrimaryContainer,
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = outlineVariant
        )
    } else {
        val primary = hslToColor(h, s.coerceIn(0.5f, 0.9f), 0.35f)
        val onPrimary = Color.White
        val primaryContainer = hslToColor(h, s.coerceIn(0.3f, 0.7f), 0.88f)
        val onPrimaryContainer = hslToColor(h, s.coerceIn(0.5f, 0.9f), 0.2f)

        val secondary = hslToColor(h, s.coerceIn(0.1f, 0.4f), 0.45f)
        val onSecondary = Color.White
        val secondaryContainer = hslToColor(h, s.coerceIn(0.1f, 0.4f), 0.85f)
        val onSecondaryContainer = hslToColor(h, s.coerceIn(0.1f, 0.4f), 0.15f)

        val background = hslToColor(h, s.coerceIn(0.02f, 0.06f), 0.97f)
        val surface = hslToColor(h, s.coerceIn(0.02f, 0.05f), 0.99f)
        val surfaceVariant = hslToColor(h, s.coerceIn(0.04f, 0.10f), 0.91f)
        val onSurface = hslToColor(h, s.coerceIn(0.15f, 0.40f), 0.12f)
        val onSurfaceVariant = hslToColor(h, s.coerceIn(0.12f, 0.30f), 0.35f)
        val outline = hslToColor(h, s.coerceIn(0.15f, 0.35f), 0.50f)
        val outlineVariant = hslToColor(h, s.coerceIn(0.08f, 0.20f), 0.85f)

        return lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = primary,
            onTertiary = onPrimary,
            tertiaryContainer = primaryContainer,
            onTertiaryContainer = onPrimaryContainer,
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = outlineVariant
        )
    }
}

@Composable
fun LinGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    seedColor: Color = Color(0xFF4FC3C3),
    content: @Composable () -> Unit
) {
    val colorScheme = generateColorScheme(seedColor, darkTheme)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LinGalleryTypography
    ) {
        content()
    }
}
