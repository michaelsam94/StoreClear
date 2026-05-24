package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = NeonPurple,
    secondary = PurpleGrey80,
    tertiary = VividRed,
    background = ObsidianBlack,
    surface = DeepCharcoal,
    onBackground = PureWhite,
    onSurface = PureWhite,
    surfaceVariant = SlateCard,
    onSurfaceVariant = MutedText,
    outline = Color(0xFF49454F)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF6750A4),
    secondary = PurpleGrey40,
    tertiary = VividRed,
    background = Color(0xFFF4F3F6),
    surface = Color(0xFFE6E1E5),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE3E0E8),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFFCAC4D0)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Set default to false to apply our custom Clean Utility / Minimal theme colors
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
