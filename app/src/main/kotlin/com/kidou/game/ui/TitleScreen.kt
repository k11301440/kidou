package com.kidou.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.model.AppLanguage
import com.kidou.game.model.GlyphGrid
import com.kidou.game.ui.components.PixelActionButton
import com.kidou.game.ui.components.PixelBevelPad
import com.kidou.game.ui.components.PixelGlyph
import com.kidou.game.ui.components.PixelStonePanel
import com.kidou.game.ui.i18n.stringsFor
import com.kidou.game.ui.theme.KidouColors
import kotlin.math.floor

@Composable
fun TitleScreen(
  glyphs: Map<String, GlyphGrid>,
  wordOrder: List<String>,
  language: AppLanguage,
  bgmEnabled: Boolean,
  onStart: () -> Unit,
  onToggleLanguage: (AppLanguage) -> Unit,
  onToggleBgm: (Boolean) -> Unit,
) {
  val strings = stringsFor(language)

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(KidouColors.Background),
  ) {
    ScrollingGlyphBackdrop(
      glyphs = glyphs,
      wordOrder = wordOrder,
      modifier = Modifier.fillMaxSize(),
    )

    // Subtle dark center vignette for legibility
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(KidouColors.Background.copy(alpha = 0.42f)),
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
          accent = KidouColors.Cyan,
          onClick = {
            onToggleLanguage(if (language == AppLanguage.JA) AppLanguage.EN else AppLanguage.JA)
          },
        )
        PixelActionButton(
          label = if (bgmEnabled) "BGM ON" else "BGM OFF",
          accent = if (bgmEnabled) KidouColors.Amber else KidouColors.Muted,
          onClick = { onToggleBgm(!bgmEnabled) },
        )
      }

      PixelStonePanel(
        borderColor = KidouColors.Amber.copy(alpha = 0.85f),
        containerColor = KidouColors.Panel.copy(alpha = 0.94f),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            PixelGlyph(grid = glyphs["n_machine"], size = 28.dp, color = KidouColors.Glyph)
            PixelGlyph(grid = glyphs["v_activate"], size = 28.dp, color = KidouColors.Amber)
            PixelGlyph(grid = glyphs["n_core"], size = 28.dp, color = KidouColors.Green)
          }

          Text(
            text = "KIDOU",
            color = KidouColors.Glyph,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            letterSpacing = 6.sp,
          )

          Text(
            text = strings.titleSubtitle,
            color = KidouColors.Amber,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 1.5.sp,
          )

          Spacer(modifier = Modifier.height(6.dp))

          PixelBevelPad(
            borderColor = KidouColors.Amber,
            fillColor = KidouColors.Amber.copy(alpha = 0.22f),
            onClick = onStart,
            modifier =
              Modifier
                .fillMaxWidth()
                .height(54.dp)
                .semantics { contentDescription = "title_start" },
          ) {
            Text(
              text = strings.startGame,
              color = KidouColors.Amber,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              letterSpacing = 2.sp,
            )
          }
        }
      }

      Box(
        modifier =
          Modifier
            .background(KidouColors.Panel.copy(alpha = 0.85f), RectangleShape)
            .border(1.dp, KidouColors.Border, RectangleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
      ) {
        Text(
          text = "v0.2.0 PROTOTYPE",
          color = KidouColors.Muted,
          fontFamily = FontFamily.Monospace,
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
  modifier: Modifier = Modifier,
) {
  val glyphList = remember(glyphs, wordOrder) {
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
          0 -> KidouColors.Amber.copy(alpha = 0.22f)
          2 -> KidouColors.Cyan.copy(alpha = 0.16f)
          else -> KidouColors.Glyph.copy(alpha = 0.16f)
        }

      for (colOffset in 0 until visibleCols) {
        val c = startCol + colOffset
        val x = baseShift + c * stepX
        if (x + tileDp < 0f || x > size.width) continue

        val idx = (((c + rowIdx * 7) % glyphList.size) + glyphList.size) % glyphList.size
        val grid = glyphList[idx]

        // Subtle stone tile outline behind each drifting glyph
        drawRect(
          color = KidouColors.Border.copy(alpha = 0.16f),
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
