package com.kidou.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.BuildConfig
import com.kidou.game.R
import com.kidou.game.model.AppLanguage
import com.kidou.game.model.GlyphGrid
import com.kidou.game.ui.components.LocalPadPressed
import com.kidou.game.ui.components.PixelActionButton
import com.kidou.game.ui.components.PixelBevelPad
import com.kidou.game.ui.components.PixelGlyph
import com.kidou.game.ui.components.PixelStonePanel
import com.kidou.game.ui.i18n.stringsFor
import com.kidou.game.ui.theme.DotGothic16FontFamily
import com.kidou.game.ui.theme.KidouPalette
import com.kidou.game.ui.theme.KidouTheme
import kotlin.math.floor
import kotlinx.coroutines.delay

@Composable
fun TitleScreen(
  glyphs: Map<String, GlyphGrid>,
  wordOrder: List<String>,
  subtitle: String,
  language: AppLanguage,
  bgmEnabled: Boolean,
  showRtaButton: Boolean,
  rtaBestTimeMs: Long?,
  playGamesLinked: Boolean,
  playGamesToast: String?,
  onStart: () -> Unit,
  onStartRta: () -> Unit,
  onPlayGamesClick: () -> Unit,
  onClearPlayGamesToast: () -> Unit,
  onToggleLanguage: (AppLanguage) -> Unit,
  onToggleBgm: (Boolean) -> Unit,
) {
  val palette = KidouTheme.palette
  val strings = stringsFor(language)

  LaunchedEffect(playGamesToast) {
    if (playGamesToast != null) {
      delay(3000L)
      onClearPlayGamesToast()
    }
  }

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(palette.background),
  ) {
    ScrollingGlyphBackdrop(
      glyphs = glyphs,
      wordOrder = wordOrder,
      palette = palette,
      modifier = Modifier.fillMaxSize(),
    )

    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(palette.background.copy(alpha = if (palette.isDark) 0.42f else 0.55f)),
    )

    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .safeDrawingPadding()
          .padding(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PixelActionButton(
          label = if (language == AppLanguage.JA) "日本語 / EN" else "EN / 日本語",
          accent = palette.accentSecondary,
          onClick = {
            onToggleLanguage(if (language == AppLanguage.JA) AppLanguage.EN else AppLanguage.JA)
          },
        )
        PixelActionButton(
          label = if (bgmEnabled) "BGM ON" else "BGM OFF",
          accent = if (bgmEnabled) palette.accentPrimary else palette.muted,
          onClick = { onToggleBgm(!bgmEnabled) },
        )
      }

      // Uniform 20.dp padding on all 4 sides so left/right/bottom margins around Start match identically (§2)
      PixelStonePanel(
        borderColor = palette.accentPrimary.copy(alpha = 0.88f),
        containerColor = palette.panel.copy(alpha = 0.95f),
        contentPadding = 20.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            PixelGlyph(grid = glyphs["n_machine"], size = 28.dp, color = palette.glyph)
            PixelGlyph(grid = glyphs["v_activate"], size = 28.dp, color = palette.accentPrimary)
            PixelGlyph(grid = glyphs["n_core"], size = 28.dp, color = palette.statusSuccess)
          }

          Text(
            text = "KIDOU",
            color = palette.glyph,
            fontFamily = DotGothic16FontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 38.sp,
            letterSpacing = 6.sp,
          )

          Text(
            text = subtitle,
            color = palette.accentPrimary,
            fontFamily = DotGothic16FontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            letterSpacing = 1.5.sp,
          )

          Spacer(modifier = Modifier.height(6.dp))

          PixelBevelPad(
            borderColor = palette.accentPrimary,
            fillColor = palette.accentPrimary.copy(alpha = 0.22f),
            onClick = onStart,
            modifier =
              Modifier
                .fillMaxWidth()
                .height(54.dp)
                .semantics { contentDescription = "title_start" },
          ) {
            Text(
              text = strings.startGame,
              color = if (LocalPadPressed.current) {
                if (palette.isDark) palette.background else palette.tile
              } else {
                palette.accentPrimary
              },
              fontFamily = DotGothic16FontFamily,
              fontWeight = FontWeight.Normal,
              fontSize = 17.sp,
              letterSpacing = 2.sp,
            )
          }

          if (showRtaButton) {
            PixelBevelPad(
              borderColor = palette.statusSuccess,
              fillColor = palette.statusSuccess.copy(alpha = 0.16f),
              onClick = onStartRta,
              modifier =
                Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .semantics { contentDescription = "title_rta" },
            ) {
              val fg =
                if (LocalPadPressed.current) {
                  if (palette.isDark) palette.background else palette.tile
                } else {
                  palette.statusSuccess
                }
              Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = strings.rtaButtonLabel,
                  color = fg,
                  fontFamily = DotGothic16FontFamily,
                  fontWeight = FontWeight.Normal,
                  fontSize = 16.sp,
                  letterSpacing = 2.sp,
                )
                if (rtaBestTimeMs != null) {
                  Text(
                    text = formatRtaTime(rtaBestTimeMs),
                    color = fg.copy(alpha = 0.85f),
                    fontFamily = DotGothic16FontFamily,
                    fontSize = 12.sp,
                  )
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val pgColor = if (playGamesLinked) palette.accentSecondary else palette.glyph
            PixelBevelPad(
              borderColor = pgColor,
              fillColor = pgColor.copy(alpha = 0.12f),
              onClick = onPlayGamesClick,
              modifier =
                Modifier
                  .height(44.dp)
                  .semantics { contentDescription = "title_play_games" },
            ) {
              val fg =
                if (LocalPadPressed.current) {
                  if (palette.isDark) palette.background else palette.tile
                } else {
                  pgColor
                }
              Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Image(
                  painter = painterResource(R.drawable.ic_play_games_controller),
                  contentDescription = null,
                  colorFilter = ColorFilter.tint(fg),
                  modifier = Modifier.size(22.dp),
                )
                Text(
                  text = if (playGamesLinked) strings.playGamesAchievementsBtn else strings.playGamesSectionTitle,
                  color = fg,
                  fontFamily = DotGothic16FontFamily,
                  fontSize = 12.sp,
                )
              }
            }
          }

          if (playGamesToast != null) {
            val msg =
              when (playGamesToast) {
                "restored" -> strings.playGamesRestoredToast
                "no_cloud_save" -> strings.playGamesNoCloudToast
                else -> strings.playGamesUnavailableToast
              }
            Text(
              text = msg,
              color = palette.muted,
              fontFamily = DotGothic16FontFamily,
              fontSize = 11.sp,
            )
          }
        }
      }

      Box(
        modifier =
          Modifier
            .background(palette.panel.copy(alpha = 0.88f), RectangleShape)
            .border(1.dp, palette.border, RectangleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
      ) {
        Text(
          text = "v${BuildConfig.VERSION_NAME}",
          color = palette.muted,
          fontFamily = DotGothic16FontFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 10.sp,
        )
      }
    }
  }
}

@Composable
private fun ScrollingGlyphBackdrop(
  glyphs: Map<String, GlyphGrid>,
  wordOrder: List<String>,
  palette: KidouPalette,
  modifier: Modifier = Modifier,
) {
  val glyphList =
    remember(glyphs, wordOrder) {
      val ordered = wordOrder.mapNotNull { glyphs[it] }
      if (ordered.isNotEmpty()) ordered else glyphs.values.toList()
    }
  val infinite = rememberInfiniteTransition(label = "title_glyph_scroll")
  val scrollProgress by
    infinite.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 28000, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "scroll_offset",
    )

  Canvas(modifier = modifier) {
    if (glyphList.isEmpty()) return@Canvas
    val tileDp = 34.dp.toPx()
    val gapDp = 12.dp.toPx()
    val stepX = tileDp + gapDp
    val rowStepY = tileDp + 22.dp.toPx()
    val rowCount = ((size.height / rowStepY).toInt() + 2).coerceAtLeast(10)
    val patternSpan = stepX * glyphList.size

    for (rowIdx in 0 until rowCount) {
      val y = rowIdx * rowStepY + 10.dp.toPx()
      val dir = if (rowIdx % 2 == 0) -1f else 1f
      val speedMult = 1f + (rowIdx % 3) * 0.2f
      val rawShift = (scrollProgress * patternSpan * speedMult) % patternSpan
      val baseShift = if (dir < 0f) -rawShift else rawShift - patternSpan

      val startCol = floor(-baseShift / stepX).toInt() - 1
      val visibleCols = (size.width / stepX).toInt() + 4
      val baseColor =
        when (rowIdx % 4) {
          0 -> palette.accentPrimary.copy(alpha = 0.22f)
          2 -> palette.accentSecondary.copy(alpha = 0.17f)
          else -> palette.glyph.copy(alpha = 0.16f)
        }

      for (colOffset in 0 until visibleCols) {
        val c = startCol + colOffset
        val x = baseShift + c * stepX
        if (x + tileDp < 0f || x > size.width) continue

        val idx = (((c + rowIdx * 7) % glyphList.size) + glyphList.size) % glyphList.size
        val grid = glyphList[idx]

        drawRect(
          color = palette.border.copy(alpha = 0.18f),
          topLeft = Offset(x, y),
          size = Size(tileDp, tileDp),
        )

        val pad = 4.dp.toPx()
        val inner = tileDp - pad * 2
        val cell = inner / 12f
        for (r in 0 until 12) {
          for (col in 0 until 12) {
            if (grid.isPixelOn(r, col)) {
              drawRect(
                color = baseColor,
                topLeft = Offset(x + pad + col * cell, y + pad + r * cell),
                size = Size(cell.coerceAtLeast(1f), cell.coerceAtLeast(1f)),
              )
            }
          }
        }
      }
    }
  }
}
