package com.kidou.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object KidouColors {
  val Background = Color(0xFF0B0D0E)
  val Panel = Color(0xFF14181B)
  val PanelSunken = Color(0xFF090C0D)
  val Tile = Color(0xFF1D2328)
  val Border = Color(0xFF313B42)
  val BorderBright = Color(0xFF4C5B66)
  val Glyph = Color(0xFFEAE6DC)
  val Green = Color(0xFF5EEB8F)
  val Amber = Color(0xFFF2B84B)
  val Red = Color(0xFFE8564A)
  val Cyan = Color(0xFF58C4DD)
  val Muted = Color(0xFF8B969E)
  val LampOff = Color(0xFF2B3238)
}

private val KidouDarkScheme =
  darkColorScheme(
    primary = KidouColors.Amber,
    onPrimary = KidouColors.Background,
    secondary = KidouColors.Cyan,
    onSecondary = KidouColors.Background,
    tertiary = KidouColors.Green,
    background = KidouColors.Background,
    onBackground = KidouColors.Glyph,
    surface = KidouColors.Panel,
    onSurface = KidouColors.Glyph,
    surfaceVariant = KidouColors.Tile,
    onSurfaceVariant = KidouColors.Muted,
    outline = KidouColors.Border,
    error = KidouColors.Red,
  )

private val KidouTypography =
  Typography(
    titleLarge =
      TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 0.8.sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 0.5.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.3.sp,
      ),
  )

@Composable
fun KIDOUTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = KidouDarkScheme,
    typography = KidouTypography,
    content = content,
  )
}
