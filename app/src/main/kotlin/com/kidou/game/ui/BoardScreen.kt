package com.kidou.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.engine.Base5
import com.kidou.game.engine.LevelEngine
import com.kidou.game.model.BoardDef
import com.kidou.game.ui.components.GlyphSentenceRow
import com.kidou.game.ui.components.PixelActionButton
import com.kidou.game.ui.components.PixelGlyph
import com.kidou.game.ui.components.PixelLed
import com.kidou.game.ui.components.PixelStonePanel
import com.kidou.game.ui.i18n.stringsFor
import com.kidou.game.ui.theme.KidouColors
import com.kidou.game.viewmodel.DictionaryTab
import com.kidou.game.viewmodel.GameUiState

@Composable
fun BoardScreen(
  uiState: GameUiState,
  onSelectBoard: (Int) -> Unit,
  onOpenLevel: (Int, Int) -> Unit,
  onWordClick: (String) -> Unit,
  onOpenDictionary: () -> Unit,
  onOpenStory: () -> Unit,
  onOpenGuide: () -> Unit,
  onOpenMenu: () -> Unit,
) {
  val data = uiState.gameData ?: return
  val strings = stringsFor(uiState.language)
  val currentBoard = data.boardCollection.boardById(uiState.selectedBoardId)

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .background(KidouColors.Background)
        .safeDrawingPadding()
        .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Top Navigation Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PixelActionButton(
        label = strings.dictionary,
        accent = KidouColors.Amber,
        onClick = onOpenDictionary,
        modifier = Modifier.weight(1f),
      )
      PixelActionButton(
        label = strings.story,
        accent = KidouColors.Cyan,
        onClick = onOpenStory,
        modifier = Modifier.weight(1f),
      )
      PixelActionButton(
        label = strings.guide,
        accent = KidouColors.Green,
        onClick = onOpenGuide,
        modifier = Modifier.weight(1f),
      )
      PixelActionButton(
        label = strings.menu,
        accent = KidouColors.Muted,
        onClick = onOpenMenu,
        modifier = Modifier.weight(1f),
      )
    }

    // Board Selector Bar (Board 1 / Board 2 unlocked at 20/25)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      data.boardCollection.boards.forEach { board ->
        val unlocked = LevelEngine.isBoardUnlocked(board, uiState.clearedLevelKeys)
        val selected = board.id == uiState.selectedBoardId
        val borderColor =
          when {
            selected -> KidouColors.Amber
            unlocked -> KidouColors.BorderBright
            else -> KidouColors.Border.copy(alpha = 0.5f)
          }
        val bgColor =
          when {
            selected -> KidouColors.Amber.copy(alpha = 0.16f)
            unlocked -> KidouColors.Panel
            else -> KidouColors.Background
          }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
          modifier =
            Modifier
              .weight(1f)
              .background(bgColor, RectangleShape)
              .border(2.dp, borderColor, RectangleShape)
              .semantics { contentDescription = "board_tab_${board.id}" }
              .then(if (unlocked) Modifier.clickable { onSelectBoard(board.id) } else Modifier)
              .padding(vertical = 8.dp, horizontal = 8.dp),
        ) {
          PixelGlyph(
            grid = data.glyphs["n_board"],
            size = 16.dp,
            color = if (unlocked) KidouColors.Glyph else KidouColors.Muted.copy(alpha = 0.4f),
          )
          Spacer(modifier = Modifier.size(6.dp))
          Base5.toDigitWordIds(board.id).forEach { dId ->
            PixelGlyph(
              grid = data.glyphs[dId],
              size = 16.dp,
              color =
                if (selected) KidouColors.Amber
                else if (unlocked) KidouColors.Glyph
                else KidouColors.Muted.copy(alpha = 0.4f),
            )
          }
          if (!unlocked) {
            Spacer(modifier = Modifier.size(6.dp))
            PixelGlyph(
              grid = data.glyphs["n_lock"],
              size = 14.dp,
              color = KidouColors.Muted.copy(alpha = 0.45f),
            )
          }
        }
      }
    }

    // Board Chassis with Base-5 Title & 5x5 Matrix
    PixelStonePanel(
      modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          GlyphSentenceRow(
            slots = currentBoard.titleSlots,
            glyphs = data.glyphs,
            words = data.words,
            dictionary = uiState.dictionary,
            language = uiState.language,
            onWordClick = onWordClick,
            compact = true,
          )
          Text(
            text = strings.boardTitle,
            color = KidouColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
          )
        }

        // 25-pip visual activation bar (with pip #20 amber threshold marker, zero Arabic numbers!)
        BoardProgressPipBar(
          clearedCount = LevelEngine.countBoardClears(currentBoard.id, uiState.clearedLevelKeys),
          totalSlots = currentBoard.totalSlots,
          threshold = 20,
        )

        // 5x5 Grid of Base-5 Level Tiles
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          for (row in 0 until 5) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              for (col in 0 until 5) {
                val levelNum = row * 5 + col + 1
                val levelDef = currentBoard.levelByNumber(levelNum)
                val isCleared =
                  LevelEngine.levelKey(currentBoard.id, levelNum) in uiState.clearedLevelKeys
                BoardLevelCell(
                  boardId = currentBoard.id,
                  levelNumber = levelNum,
                  available = levelDef != null,
                  isCleared = isCleared,
                  glyphs = data.glyphs,
                  onClick = {
                    if (levelDef != null) {
                      onOpenLevel(currentBoard.id, levelNum)
                    }
                  },
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }

        if (currentBoard.id == 2) {
          Text(
            text = strings.levelLockedWip,
            color = KidouColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
          )
        } else if (!uiState.isBoard2Unlocked) {
          Text(
            text = strings.boardLockedHint,
            color = KidouColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
          )
        }
      }
    }
  }
}

@Composable
private fun BoardProgressPipBar(
  clearedCount: Int,
  totalSlots: Int,
  threshold: Int,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(KidouColors.PanelSunken, RectangleShape)
        .border(1.dp, KidouColors.Border, RectangleShape)
        .padding(horizontal = 6.dp, vertical = 5.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    for (i in 1..totalSlots) {
      val filled = i <= clearedCount
      val isThreshold = i == threshold
      val color =
        when {
          filled -> KidouColors.Green
          isThreshold -> KidouColors.Amber.copy(alpha = 0.55f)
          else -> KidouColors.LampOff
        }
      Box(
        modifier =
          Modifier
            .weight(1f)
            .height(if (isThreshold) 10.dp else 7.dp)
            .background(color),
      )
    }
  }
}

@Composable
private fun BoardLevelCell(
  boardId: Int,
  levelNumber: Int,
  available: Boolean,
  isCleared: Boolean,
  glyphs: Map<String, com.kidou.game.model.GlyphGrid>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val digits = Base5.toDigitWordIds(levelNumber)
  val borderColor =
    when {
      !available -> KidouColors.Border.copy(alpha = 0.35f)
      isCleared -> KidouColors.Green
      else -> KidouColors.BorderBright
    }
  val bgColor =
    when {
      !available -> KidouColors.Background
      isCleared -> KidouColors.Green.copy(alpha = 0.14f)
      else -> KidouColors.Tile
    }
  val glyphColor =
    when {
      !available -> KidouColors.Muted.copy(alpha = 0.25f)
      isCleared -> KidouColors.Green
      else -> KidouColors.Glyph
    }
  val glyphSize =
    when (digits.size) {
      1 -> 20.dp
      2 -> 15.dp
      else -> 11.dp
    }

  Box(
    modifier =
      modifier
        .aspectRatio(1f)
        .background(bgColor, RectangleShape)
        .border(if (isCleared) 2.dp else 1.5.dp, borderColor, RectangleShape)
        .semantics { contentDescription = "board_${boardId}_level_$levelNumber" }
        .then(if (available) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(4.dp),
  ) {
    // Top-right status LED pip
    PixelLed(
      color =
        when {
          !available -> KidouColors.Background
          isCleared -> KidouColors.Green
          else -> KidouColors.Amber.copy(alpha = 0.4f)
        },
      size = 7.dp,
      modifier = Modifier.align(Alignment.TopEnd),
    )

    // Base-5 numeric glyph display ONLY (zero Arabic/Japanese numerals!)
    Row(
      modifier = Modifier.align(Alignment.Center),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      digits.forEach { dId ->
        PixelGlyph(grid = glyphs[dId], size = glyphSize, color = glyphColor)
      }
    }
  }
}
