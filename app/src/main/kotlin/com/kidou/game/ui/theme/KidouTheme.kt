package com.kidou.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kidou.game.model.AppLanguage
import com.kidou.game.model.WordColorTag

enum class ThemeMode {
  DARK,
  LIGHT,
}

enum class ColorTheme(val labelJa: String, val labelEn: String, val swatchHex: Long) {
  AMBER_RUIN("琥珀遺跡", "Amber Ruin", 0xFFF2B84B),
  EMERALD_CRT("翠走査線", "Emerald CRT", 0xFF5EEB8F),
  COBALT_SIGNAL("蒼信号", "Cobalt Signal", 0xFF60A5FA),
  CRIMSON_FORGE("紅鍛冶", "Crimson Forge", 0xFFF87171);

  fun label(language: AppLanguage): String =
    when (language) {
      AppLanguage.JA -> labelJa
      AppLanguage.EN -> labelEn
    }
}

@Immutable
data class KidouPalette(
  val isDark: Boolean,
  val background: Color,
  val panel: Color,
  val panelSunken: Color,
  val tile: Color,
  val border: Color,
  val borderBright: Color,
  val glyph: Color,
  val muted: Color,
  val lampOff: Color,
  val accentPrimary: Color,
  val accentSecondary: Color,
  val statusSuccess: Color,
  val statusWarning: Color,
  val statusDanger: Color,
  val bevelHighlight: Color,
  val bevelShadow: Color,
  val scanline: Color,
  val debugPanelBg: Color,
  val tagUnclassified: Color,
  val tagHypothesis: Color,
  val tagConfirmed: Color,
  val tagObject: Color,
  val tagAction: Color,
  val tagModifier: Color,
) {
  fun tagColor(tag: WordColorTag): Color =
    when (tag) {
      WordColorTag.UNCLASSIFIED -> tagUnclassified
      WordColorTag.HYPOTHESIS -> tagHypothesis
      WordColorTag.CONFIRMED -> tagConfirmed
      WordColorTag.OBJECT -> tagObject
      WordColorTag.ACTION -> tagAction
      WordColorTag.MODIFIER -> tagModifier
    }
}

fun buildKidouPalette(themeMode: ThemeMode, colorTheme: ColorTheme): KidouPalette {
  return when (themeMode) {
    ThemeMode.DARK -> {
      val (primary, secondary) =
        when (colorTheme) {
          ColorTheme.AMBER_RUIN -> Color(0xFFF2B84B) to Color(0xFF58C4DD)
          ColorTheme.EMERALD_CRT -> Color(0xFF5EEB8F) to Color(0xFFA3E635)
          ColorTheme.COBALT_SIGNAL -> Color(0xFF60A5FA) to Color(0xFF38BDF8)
          ColorTheme.CRIMSON_FORGE -> Color(0xFFF87171) to Color(0xFFFBBF24)
        }
      KidouPalette(
        isDark = true,
        background = Color(0xFF0B0D0E),
        panel = Color(0xFF14181B),
        panelSunken = Color(0xFF090C0D),
        tile = Color(0xFF1D2328),
        border = Color(0xFF313B42),
        borderBright = Color(0xFF4C5B66),
        glyph = Color(0xFFEAE6DC),
        muted = Color(0xFF8B969E),
        lampOff = Color(0xFF2B3238),
        accentPrimary = primary,
        accentSecondary = secondary,
        statusSuccess = Color(0xFF5EEB8F),
        statusWarning = Color(0xFFF2B84B),
        statusDanger = Color(0xFFE8564A),
        bevelHighlight = Color(0x14FFFFFF),
        bevelShadow = Color(0x73000000),
        scanline = Color(0xFF161D22),
        debugPanelBg = Color(0xFF16121A),
        tagUnclassified = Color(0xFF8B969E),
        tagHypothesis = Color(0xFFF2B84B),
        tagConfirmed = Color(0xFF5EEB8F),
        tagObject = Color(0xFF58C4DD),
        tagAction = Color(0xFFB892FF),
        tagModifier = Color(0xFFE87E6B),
      )
    }

    ThemeMode.LIGHT -> {
      val (primary, secondary) =
        when (colorTheme) {
          ColorTheme.AMBER_RUIN -> Color(0xFFA15C07) to Color(0xFF0E7490)
          ColorTheme.EMERALD_CRT -> Color(0xFF157F3E) to Color(0xFF4D7C0F)
          ColorTheme.COBALT_SIGNAL -> Color(0xFF1D4ED8) to Color(0xFF0369A1)
          ColorTheme.CRIMSON_FORGE -> Color(0xFFB91C1C) to Color(0xFFB45309)
        }
      KidouPalette(
        isDark = false,
        background = Color(0xFFE8E4DA),
        panel = Color(0xFFF4F1EA),
        panelSunken = Color(0xFFDBD5C8),
        tile = Color(0xFFFFFFFF),
        border = Color(0xFFB5ADA0),
        borderBright = Color(0xFF70685C),
        glyph = Color(0xFF1B1F23),
        muted = Color(0xFF596068),
        lampOff = Color(0xFFC4BEB0),
        accentPrimary = primary,
        accentSecondary = secondary,
        statusSuccess = Color(0xFF137A3D),
        statusWarning = Color(0xFFB4690E),
        statusDanger = Color(0xFFC62828),
        bevelHighlight = Color(0xA6FFFFFF),
        bevelShadow = Color(0x28000000),
        scanline = Color(0xFFCCC5B5),
        debugPanelBg = Color(0xFFECE4F0),
        tagUnclassified = Color(0xFF596068),
        tagHypothesis = Color(0xFFA15C07),
        tagConfirmed = Color(0xFF137A3D),
        tagObject = Color(0xFF0E7490),
        tagAction = Color(0xFF6D28D9),
        tagModifier = Color(0xFFC62828),
      )
    }
  }
}

private val DefaultPalette = buildKidouPalette(ThemeMode.DARK, ColorTheme.AMBER_RUIN)

val LocalKidouPalette = staticCompositionLocalOf { DefaultPalette }

object KidouTheme {
  val palette: KidouPalette
    @Composable get() = LocalKidouPalette.current
}

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
fun KIDOUTheme(
  themeMode: ThemeMode = ThemeMode.DARK,
  colorTheme: ColorTheme = ColorTheme.AMBER_RUIN,
  content: @Composable () -> Unit,
) {
  val palette = buildKidouPalette(themeMode, colorTheme)
  val scheme =
    if (palette.isDark) {
      darkColorScheme(
        primary = palette.accentPrimary,
        onPrimary = palette.background,
        secondary = palette.accentSecondary,
        onSecondary = palette.background,
        tertiary = palette.statusSuccess,
        background = palette.background,
        onBackground = palette.glyph,
        surface = palette.panel,
        onSurface = palette.glyph,
        surfaceVariant = palette.tile,
        onSurfaceVariant = palette.muted,
        outline = palette.border,
        error = palette.statusDanger,
      )
    } else {
      lightColorScheme(
        primary = palette.accentPrimary,
        onPrimary = palette.tile,
        secondary = palette.accentSecondary,
        onSecondary = palette.tile,
        tertiary = palette.statusSuccess,
        background = palette.background,
        onBackground = palette.glyph,
        surface = palette.panel,
        onSurface = palette.glyph,
        surfaceVariant = palette.tile,
        onSurfaceVariant = palette.muted,
        outline = palette.border,
        error = palette.statusDanger,
      )
    }

  CompositionLocalProvider(LocalKidouPalette provides palette) {
    MaterialTheme(
      colorScheme = scheme,
      typography = KidouTypography,
      content = content,
    )
  }
}
