package com.kidou.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.engine.Base5
import com.kidou.game.engine.LevelEngine
import com.kidou.game.model.GlyphGrid
import com.kidou.game.ui.components.GlyphSentenceRow
import com.kidou.game.ui.components.PixelActionButton
import com.kidou.game.ui.components.PixelGlyph
import com.kidou.game.ui.components.PixelLed
import com.kidou.game.ui.components.PixelStonePanel
import com.kidou.game.ui.components.rememberKidouHapticClick
import com.kidou.game.ui.i18n.stringsFor
import com.kidou.game.ui.theme.DotGothic16FontFamily
import com.kidou.game.ui.theme.KidouTheme
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
  val palette = KidouTheme.palette
  val strings = stringsFor(uiState.language)
  val hapticClick = rememberKidouHapticClick()
  val currentBoard = data.boardCollection.boardById(uiState.selectedBoardId)

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .background(palette.background)
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
        accent = palette.accentPrimary,
        onClick = onOpenDictionary,
        modifier = Modifier.weight(1f),
      )
      PixelActionButton(
        label = strings.story,
        accent = palette.accentSecondary,
        onClick = onOpenStory,
        modifier = Modifier.weight(1f),
      )
      PixelActionButton(
        label = strings.guide,
        accent = palette.statusSuccess,
        onClick = onOpenGuide,
        modifier = Modifier.weight(1f),
      )
      PixelActionButton(
        label = strings.menu,
        accent = palette.muted,
        onClick = onOpenMenu,
        modifier = Modifier.weight(1f),
      )
    }

    // Board Selector Bar (Boards 1..5)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      data.boardCollection.boards.forEach { board ->
        val unlocked = LevelEngine.isBoardUnlocked(board, uiState.clearedLevelKeys)
        val selected = board.id == uiState.selectedBoardId
        val digits = Base5.toDigitWordIds(board.id)
        val interactionSource = remember(board.id) { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        LaunchedEffect(isPressed) {
          if (isPressed && unlocked) hapticClick()
        }
        val invertedFg = if (palette.isDark) palette.background else palette.tile
        val borderColor =
          when {
            isPressed && unlocked -> palette.accentPrimary
            selected -> palette.accentPrimary
            unlocked -> palette.borderBright
            else -> palette.border.copy(alpha = 0.5f)
          }
        val bgColor =
          when {
            isPressed && unlocked -> palette.accentPrimary
            selected -> palette.accentPrimary.copy(alpha = 0.16f)
            unlocked -> palette.panel
            else -> palette.background
          }
        val glyphSize = if (digits.size > 1) 11.dp else 13.dp
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
          modifier =
            Modifier
              .weight(1f)
              .offset(y = if (isPressed && unlocked) 2.dp else 0.dp)
              .background(bgColor, RectangleShape)
              .border(2.dp, borderColor, RectangleShape)
              .semantics { contentDescription = "board_tab_${board.id}" }
              .then(
                if (unlocked) {
                  Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = { onSelectBoard(board.id) },
                  )
                } else {
                  Modifier
                },
              )
              .padding(vertical = 8.dp, horizontal = 3.dp),
        ) {
          PixelGlyph(
            grid = data.glyphs["n_board"],
            size = 13.dp,
            color =
              when {
                isPressed && unlocked -> invertedFg
                unlocked -> palette.glyph
                else -> palette.muted.copy(alpha = 0.4f)
              },
          )
          Spacer(modifier = Modifier.size(3.dp))
          digits.forEach { dId ->
            PixelGlyph(
              grid = data.glyphs[dId],
              size = glyphSize,
              color =
                when {
                  isPressed && unlocked -> invertedFg
                  selected -> palette.accentPrimary
                  unlocked -> palette.glyph
                  else -> palette.muted.copy(alpha = 0.4f)
                },
            )
          }
          if (!unlocked) {
            Spacer(modifier = Modifier.size(2.dp))
            PixelGlyph(
              grid = data.glyphs["n_lock"],
              size = 11.dp,
              color = palette.muted.copy(alpha = 0.45f),
            )
          }
        }
      }
    }

    // Board Chassis with Base-5 Title & 5x5 Matrix (§2: removed Japanese header label)
    PixelStonePanel(
      modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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

        BoardProgressPipBar(
          clearedCount = LevelEngine.countBoardClears(currentBoard.id, uiState.clearedLevelKeys),
          totalSlots = currentBoard.totalSlots,
          threshold = 20,
        )

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

        val nextBoard =
          data.boardCollection.boards.firstOrNull { it.requiredBoardId == currentBoard.id }
        if (nextBoard != null && !LevelEngine.isBoardUnlocked(nextBoard, uiState.clearedLevelKeys)) {
          val unlockRuleLine1 =
            listOf(
              listOf("n_board") + Base5.toDigitWordIds(currentBoard.id),
              listOf("d_1", "d_0", "d_0"),
              listOf("m_among"),
              listOf("d_4", "d_0"),
              listOf("v_activate"),
              listOf("m_if"),
            )
          val unlockRuleLine2 =
            listOf(
              listOf("n_board", "m_next"),
              listOf("v_open"),
            )
          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .background(palette.panelSunken, RectangleShape)
                .border(1.dp, palette.border, RectangleShape)
                .semantics { contentDescription = "board_unlock_rule_glyphs" }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            GlyphSentenceRow(
              slots = unlockRuleLine1,
              glyphs = data.glyphs,
              words = data.words,
              dictionary = uiState.dictionary,
              language = uiState.language,
              onWordClick = onWordClick,
              compact = true,
            )
            GlyphSentenceRow(
              slots = unlockRuleLine2,
              glyphs = data.glyphs,
              words = data.words,
              dictionary = uiState.dictionary,
              language = uiState.language,
              onWordClick = onWordClick,
              compact = true,
            )
          }
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
  val palette = KidouTheme.palette
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.panelSunken, RectangleShape)
        .border(1.dp, palette.border, RectangleShape)
        .padding(horizontal = 6.dp, vertical = 5.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    for (i in 1..totalSlots) {
      val filled = i <= clearedCount
      val isThreshold = i == threshold
      val color =
        when {
          filled -> palette.statusSuccess
          isThreshold -> palette.accentPrimary.copy(alpha = 0.6f)
          else -> palette.lampOff
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
  glyphs: Map<String, GlyphGrid>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = KidouTheme.palette
  val hapticClick = rememberKidouHapticClick()
  val interactionSource = remember(boardId, levelNumber) { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  LaunchedEffect(isPressed) {
    if (isPressed && available) hapticClick()
  }

  val digits = Base5.toDigitWordIds(levelNumber)
  val invertedFg = if (palette.isDark) palette.background else palette.tile
  val activeAccent = if (isCleared) palette.statusSuccess else palette.accentPrimary
  val borderColor =
    when {
      !available -> palette.border.copy(alpha = 0.35f)
      isPressed -> activeAccent
      isCleared -> palette.statusSuccess
      else -> palette.borderBright
    }
  val bgColor =
    when {
      !available -> palette.background
      isPressed -> activeAccent
      isCleared -> palette.statusSuccess.copy(alpha = 0.14f)
      else -> palette.tile
    }
  val glyphColor =
    when {
      !available -> palette.muted.copy(alpha = 0.25f)
      isPressed -> invertedFg
      isCleared -> palette.statusSuccess
      else -> palette.glyph
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
        .offset(y = if (isPressed && available) 2.dp else 0.dp)
        .background(bgColor, RectangleShape)
        .border(if (isCleared || isPressed) 2.dp else 1.5.dp, borderColor, RectangleShape)
        .semantics {
          contentDescription =
            if (isCleared) "board_${boardId}_level_${levelNumber}_cleared"
            else "board_${boardId}_level_$levelNumber"
        }
        .then(
          if (available) {
            Modifier.clickable(
              interactionSource = interactionSource,
              indication = null,
              role = Role.Button,
              onClick = onClick,
            )
          } else {
            Modifier
          },
        )
        .padding(4.dp),
  ) {
    PixelLed(
      color =
        when {
          !available -> palette.background
          isPressed -> invertedFg
          isCleared -> palette.statusSuccess
          else -> palette.accentPrimary.copy(alpha = 0.45f)
        },
      size = 7.dp,
      modifier = Modifier.align(Alignment.TopEnd),
    )

    if (isCleared) {
      PixelGlyph(
        grid = glyphs["adj_complete"],
        size = 24.dp,
        color = if (isPressed) invertedFg else palette.statusSuccess,
        modifier = Modifier.align(Alignment.Center),
      )
    } else {
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
}
