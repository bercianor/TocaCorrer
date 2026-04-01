package es.bercianor.tocacorrer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Purple80 = Color(0xFFD0BCFF)
private val PurpleGrey80 = Color(0xFFCCC2DC)
private val Pink80 = Color(0xFFEFB8C8)

private val Purple40 = Color(0xFF6650a4)
private val PurpleGrey40 = Color(0xFF625b71)
private val Pink40 = Color(0xFF7D5260)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * Available primary colors palette.
 * Index 0 is the default purple — matches DarkColorScheme/LightColorScheme.
 */
val availableColors = listOf(
    Color(0xFF6650A4), // 0 - Default Purple
    Color(0xFFE53935), // 1 - Running Red
    Color(0xFFFF9800), // 2 - Safety Orange
    Color(0xFFFDD835), // 3 - Solar Yellow
    Color(0xFF4CAF50), // 4 - Forest Green
    Color(0xFF2196F3), // 5 - Electric Blue
    Color(0xFF009688), // 6 - Teal Active
    Color(0xFFE91E63), // 7 - Hot Pink
)

/**
 * Calculates the relative luminance of a color using the WCAG formula.
 * Returns a value in [0.0, 1.0] where 0 = black and 1 = white.
 */
private fun luminance(color: Color): Double {
    fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/**
 * Blends a color toward white by [fraction] (0.0 = original, 1.0 = white).
 */
private fun blendWithWhite(color: Color, fraction: Float): Color {
    return Color(
        red = color.red + (1f - color.red) * fraction,
        green = color.green + (1f - color.green) * fraction,
        blue = color.blue + (1f - color.blue) * fraction,
        alpha = 1f
    )
}

/**
 * Blends a color toward black by [fraction] (0.0 = original, 1.0 = black).
 */
private fun blendWithBlack(color: Color, fraction: Float): Color {
    return Color(
        red = color.red * (1f - fraction),
        green = color.green * (1f - fraction),
        blue = color.blue * (1f - fraction),
        alpha = 1f
    )
}

@Composable
fun TocaCorrerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    primaryColorIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        primaryColorIndex > 0 -> {
            // Custom color selected — generate a full color scheme with adaptive on/off colors
            val customPrimary = availableColors.getOrElse(primaryColorIndex) { availableColors[0] }

            // Adaptive text color: dark text on light colors, white on dark colors
            val onCustomColor = if (luminance(customPrimary) > 0.35) Color(0xFF1A1A1A) else Color.White

            if (darkTheme) {
                val secondaryDark = blendWithWhite(customPrimary, 0.40f)
                val onSecondaryColor = if (luminance(secondaryDark) > 0.35) Color(0xFF1A1A1A) else Color.White
                darkColorScheme(
                    primary = customPrimary,
                    onPrimary = onCustomColor,
                    primaryContainer = customPrimary,
                    onPrimaryContainer = onCustomColor,
                    secondary = secondaryDark,
                    onSecondary = onSecondaryColor,
                    secondaryContainer = blendWithWhite(customPrimary, 0.20f),
                    onSecondaryContainer = onCustomColor,
                    tertiary = Pink80,
                    onTertiary = Color(0xFF1A1A1A),
                )
            } else {
                val secondaryLight = blendWithBlack(customPrimary, 0.25f)
                val onSecondaryColor = if (luminance(secondaryLight) > 0.35) Color(0xFF1A1A1A) else Color.White
                lightColorScheme(
                    primary = customPrimary,
                    onPrimary = onCustomColor,
                    primaryContainer = customPrimary,
                    onPrimaryContainer = onCustomColor,
                    secondary = secondaryLight,
                    onSecondary = onSecondaryColor,
                    secondaryContainer = blendWithWhite(customPrimary, 0.60f),
                    onSecondaryContainer = Color(0xFF1A1A1A),
                    tertiary = Pink40,
                    onTertiary = Color.White,
                )
            }
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
