package com.kidou.game.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.model.DictionaryEntry
import com.kidou.game.model.GlyphGrid
import com.kidou.game.model.WordColorTag
import com.kidou.game.ui.theme.KidouColors
import kotlin.math.floor

@Composable
fun PixelGlyph(
  grid: GlyphGrid?,
  size: Dp,
  color: Color = KidouColors.Glyph,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier.size(size)) {
    if (grid == null) return@Canvas
    val cellW = this.size.width / 12f
    val cellH = this.size.height / 12f
    for (r in 0 until 12) {
      for (c in 0 until 12) {
        if (grid.isPixelOn(r, c)) {
          val x0 = floor(c * cellW)
          val y0 = floor(r * cellH)
          val x1 = floor((c + 1) * cellW)
          val y1 = floor((r + 1) * cellH)
          drawRect(
            color = color,
            topLeft = Offset(x0, y0),
            size = Size((x1 - x0).coerceAtLeast(1f), (y1 - y0).coerceAtLeast(1f)),
          )
        }
      }
    }
  }
}

@Composable
fun GlyphWordTile(
  wordId: String,
  grid: GlyphGrid?,
  entry: DictionaryEntry?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  glyphColor: Color = KidouColors.Glyph,
) {
  val translation = entry?.translation?.trim().orEmpty()
  val colorTag = entry?.colorTag ?: WordColorTag.UNCLASSIFIED
  val tagColor = Color(colorTag.colorHex)
  val hasTag = colorTag != WordColorTag.UNCLASSIFIED
  val borderColor =
    when {
      hasTag -> tagColor.copy(alpha = 0.65f)
      translation.isNotEmpty() -> KidouColors.BorderBright
      else -> KidouColors.Border
    }

  val glyphSize = if (compact) 22.dp else 30.dp
  val hPad = if (compact) 6.dp else 8.dp
  val vPad = if (compact) 4.dp else 5.dp
  val rubyHeight = if (compact) 13.dp else 15.dp
  val desc = if (translation.isNotEmpty()) "$wordId:$translation" else "glyph:$wordId"

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      modifier
        .widthIn(min = if (compact) 34.dp else 44.dp, max = 96.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(KidouColors.Tile)
        .border(1.dp, borderColor, RoundedCornerShape(2.dp))
        .semantics { contentDescription = desc }
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = hPad, vertical = vPad),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.heightIn(min = rubyHeight),
    ) {
      if (translation.isNotEmpty()) {
        Text(
          text = translation,
          color = if (hasTag) tagColor else KidouColors.Glyph,
          fontFamily = FontFamily.Monospace,
          fontSize = if (compact) 9.sp else 10.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
        )
      } else {
        Text(
          text = "··",
          color = if (hasTag) tagColor.copy(alpha = 0.8f) else KidouColors.Muted.copy(alpha = 0.45f),
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp,
          textAlign = TextAlign.Center,
        )
      }
    }
    PixelGlyph(
      grid = grid,
      size = glyphSize,
      color = glyphColor,
      modifier = Modifier.padding(top = 2.dp),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlyphSentenceRow(
  slots: List<List<String>>,
  glyphs: Map<String, GlyphGrid>,
  dictionary: Map<String, DictionaryEntry>,
  onWordClick: (String) -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  glyphColor: Color = KidouColors.Glyph,
) {
  if (slots.isEmpty()) return
  val slotGap = if (compact) 11.dp else 16.dp
  val wordGap = if (compact) 3.dp else 4.dp

  FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(slotGap),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    slots.forEach { slotWords ->
      Row(
        horizontalArrangement = Arrangement.spacedBy(wordGap),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        slotWords.forEach { wordId ->
          GlyphWordTile(
            wordId = wordId,
            grid = glyphs[wordId],
            entry = dictionary[wordId],
            onClick = { onWordClick(wordId) },
            compact = compact,
            glyphColor = glyphColor,
          )
        }
      }
    }
  }
}
