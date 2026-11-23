package com.kidou.game.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.ui.theme.KidouColors

@Composable
fun PixelStonePanel(
  modifier: Modifier = Modifier,
  borderColor: Color = KidouColors.BorderBright,
  containerColor: Color = KidouColors.Panel,
  rivets: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier =
      modifier
        .background(containerColor, RectangleShape)
        .border(2.dp, borderColor, RectangleShape)
        .drawBehind {
          val inset = 3.dp.toPx()
          drawRect(
            color = Color(0xFF07090A),
            topLeft = Offset(inset, inset),
            size = Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)),
            alpha = 0.35f,
          )
          if (rivets) {
            val rSize = 4.dp.toPx()
            val pad = 5.dp.toPx()
            val rColor = KidouColors.BorderBright.copy(alpha = 0.8f)
            drawRect(rColor, Offset(pad, pad), Size(rSize, rSize))
            drawRect(rColor, Offset(size.width - pad - rSize, pad), Size(rSize, rSize))
            drawRect(rColor, Offset(pad, size.height - pad - rSize), Size(rSize, rSize))
            drawRect(rColor, Offset(size.width - pad - rSize, size.height - pad - rSize), Size(rSize, rSize))
          }
        }
        .padding(12.dp),
    content = content,
  )
}

@Composable
fun PixelCrtDisplay(
  modifier: Modifier = Modifier,
  borderColor: Color = KidouColors.BorderBright,
  content: @Composable ColumnScope.() -> Unit,
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .background(KidouColors.PanelSunken, RectangleShape)
        .border(2.dp, borderColor, RectangleShape),
  ) {
    Canvas(modifier = Modifier.matchParentSize()) {
      val step = 4.dp.toPx()
      var y = 0f
      while (y < size.height) {
        drawLine(
          color = Color(0xFF161D22),
          start = Offset(0f, y),
          end = Offset(size.width, y),
          strokeWidth = 1f,
          alpha = 0.45f,
        )
        y += step
      }
    }
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      content = content,
    )
  }
}

@Composable
fun PixelActionButton(
  label: String,
  accent: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
) {
  val bg = if (selected) accent.copy(alpha = 0.22f) else KidouColors.Tile
  val border = if (selected) accent else accent.copy(alpha = 0.72f)
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .heightIn(min = 38.dp)
        .background(bg, RectangleShape)
        .border(2.dp, border, RectangleShape)
        .drawBehind {
          // Top pixel highlight & bottom pixel shadow
          drawRect(Color.White.copy(alpha = 0.08f), Offset(2f, 2f), Size(size.width - 4f, 3f))
          drawRect(Color.Black.copy(alpha = 0.45f), Offset(2f, size.height - 5f), Size(size.width - 4f, 3f))
        }
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 7.dp),
  ) {
    Text(
      text = label,
      color = if (selected || accent != KidouColors.Muted) accent else KidouColors.Glyph,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      fontSize = 11.sp,
      maxLines = 1,
    )
  }
}

@Composable
fun PixelBevelPad(
  modifier: Modifier = Modifier,
  borderColor: Color = KidouColors.BorderBright,
  fillColor: Color = KidouColors.Tile,
  contentAlignment: Alignment = Alignment.Center,
  onClick: (() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(
    contentAlignment = contentAlignment,
    modifier =
      modifier
        .clip(RoundedCornerShape(0.dp))
        .background(fillColor)
        .border(2.dp, borderColor)
        .drawBehind {
          drawRect(Color.White.copy(alpha = 0.09f), Offset(0f, 0f), Size(size.width, 4f))
          drawRect(Color.Black.copy(alpha = 0.4f), Offset(0f, size.height - 4f), Size(size.width, 4f))
        }
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    content = content,
  )
}

@Composable
fun PixelLed(
  color: Color,
  size: Dp = 12.dp,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .size(size)
        .background(color, RectangleShape)
        .border(1.dp, KidouColors.Glyph.copy(alpha = 0.4f), RectangleShape),
  )
}

@Composable
fun PixelDivider(modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(2.dp)
        .background(KidouColors.Border),
  )
}
