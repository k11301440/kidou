package com.kidou.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.engine.Base5
import com.kidou.game.model.AppLanguage
import com.kidou.game.model.DictionaryEntry
import com.kidou.game.model.GlyphGrid
import com.kidou.game.model.LampState
import com.kidou.game.model.LevelDef
import com.kidou.game.model.LevelRuntimeState
import com.kidou.game.model.WordDef
import com.kidou.game.ui.components.GlyphSentenceRow
import com.kidou.game.ui.components.GlyphWordTile
import com.kidou.game.ui.components.PixelActionButton
import com.kidou.game.ui.components.PixelBevelPad
import com.kidou.game.ui.components.PixelCrtDisplay
import com.kidou.game.ui.components.PixelGlyph
import com.kidou.game.ui.components.PixelLed
import com.kidou.game.ui.components.PixelStonePanel
import com.kidou.game.ui.i18n.stringsFor
import com.kidou.game.ui.theme.KidouColors
import com.kidou.game.viewmodel.GameUiState

@Composable
fun LevelScreen(
  uiState: GameUiState,
  onBackToBoard: () -> Unit,
  onOpenNextLevel: (Int, Int) -> Unit,
  onTrigger: (String) -> Unit,
  onWordClick: (String) -> Unit,
  onOpenDictionary: () -> Unit,
  onOpenMenu: () -> Unit,
) {
  val data = uiState.gameData ?: return
  val levelState = uiState.activeLevelState ?: return
  val boardDef = data.boardCollection.boardById(levelState.boardId)
  val levelDef = boardDef.levelByNumber(levelState.levelNumber) ?: return
  val strings = stringsFor(uiState.language)
  val haptics = LocalHapticFeedback.current
  val nextLevelExists = boardDef.levelByNumber(levelState.levelNumber + 1) != null

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .background(KidouColors.Background)
        .safeDrawingPadding()
        .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // 1. Common Top Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PixelActionButton(
        label = "← ${strings.backToBoard}",
        accent = KidouColors.Cyan,
        onClick = onBackToBoard,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelActionButton(
          label = strings.dictionary,
          accent = KidouColors.Amber,
          onClick = onOpenDictionary,
        )
        PixelActionButton(
          label = strings.menu,
          accent = KidouColors.Muted,
          onClick = onOpenMenu,
        )
      }
    }

    // 2. Upper Half: Riveted Machine Chassis + CRT Display + Engraved Plates
    PixelStonePanel(
      borderColor = if (levelState.isCleared) KidouColors.Green else KidouColors.BorderBright,
      modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
      Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Machine Name + Base-5 Level Number + Status LEDs
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          GlyphSentenceRow(
            slots = listOf(listOf("n_machine"), Base5.toDigitWordIds(levelDef.levelNumber)),
            glyphs = data.glyphs,
            words = data.words,
            dictionary = uiState.dictionary,
            language = uiState.language,
            onWordClick = onWordClick,
            compact = true,
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            PixelLed(
              color = lampColorOf(levelState.lamp),
              size = 14.dp,
              modifier = Modifier.semantics { contentDescription = "lamp_${levelState.lamp.name.lowercase()}" },
            )
            if (levelDef.sequenceSteps.isNotEmpty()) {
              levelDef.sequenceSteps.indices.forEach { stepIdx ->
                PixelLed(
                  color =
                    if (stepIdx < levelState.seqStep || levelState.isCleared) KidouColors.Green
                    else KidouColors.LampOff,
                  size = 10.dp,
                )
              }
            }
          }
        }

        // Recessed Phosphor CRT Readout
        PixelCrtDisplay(
          borderColor =
            when (levelState.lamp) {
              LampState.GREEN -> KidouColors.Green
              LampState.RED -> KidouColors.Red
              else -> KidouColors.BorderBright
            },
        ) {
          if (levelState.primaryMessage.isNotEmpty()) {
            GlyphSentenceRow(
              slots = levelState.primaryMessage,
              glyphs = data.glyphs,
              words = data.words,
              dictionary = uiState.dictionary,
              language = uiState.language,
              onWordClick = onWordClick,
              glyphColor =
                if (levelState.lamp == LampState.RED) KidouColors.Red else KidouColors.Glyph,
            )
          }
          if (levelState.secondaryMessage.isNotEmpty()) {
            GlyphSentenceRow(
              slots = levelState.secondaryMessage,
              glyphs = data.glyphs,
              words = data.words,
              dictionary = uiState.dictionary,
              language = uiState.language,
              onWordClick = onWordClick,
              compact = true,
              glyphColor =
                when (levelState.lamp) {
                  LampState.GREEN -> KidouColors.Green
                  LampState.RED -> KidouColors.Amber
                  else -> KidouColors.Glyph.copy(alpha = 0.88f)
                },
            )
          }
        }

        // Cleared Banner inside Machine Panel
        if (levelState.isCleared) {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .background(KidouColors.Green.copy(alpha = 0.14f), RectangleShape)
                .border(2.dp, KidouColors.Green, RectangleShape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = strings.activatedBanner,
              color = KidouColors.Green,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              PixelActionButton(
                label = strings.backToBoard,
                accent = KidouColors.Cyan,
                onClick = onBackToBoard,
              )
              if (nextLevelExists) {
                PixelActionButton(
                  label = strings.nextLevel,
                  accent = KidouColors.Green,
                  onClick = { onOpenNextLevel(levelState.boardId, levelState.levelNumber + 1) },
                )
              }
            }
          }
        }

        // Engraved Instruction Plates
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          levelDef.plates.forEachIndexed { idx, plateSlots ->
            val stepDone =
              levelDef.sequenceSteps.isNotEmpty() &&
                (idx < levelState.seqStep || levelState.isCleared)
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .background(
                    if (stepDone) KidouColors.Green.copy(alpha = 0.08f) else KidouColors.Tile.copy(alpha = 0.7f),
                    RectangleShape,
                  )
                  .border(
                    1.5.dp,
                    if (stepDone) KidouColors.Green else KidouColors.Border,
                    RectangleShape,
                  )
                  .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
              GlyphSentenceRow(
                slots = plateSlots,
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

    // 3. Lower Half: Mechanical Control Deck
    ControlDeckPanel(
      levelDef = levelDef,
      levelState = levelState,
      glyphs = data.glyphs,
      words = data.words,
      dictionary = uiState.dictionary,
      language = uiState.language,
      onWordClick = onWordClick,
      onTrigger = { trig ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onTrigger(trig)
      },
    )
  }
}

@Composable
private fun ControlDeckPanel(
  levelDef: LevelDef,
  levelState: LevelRuntimeState,
  glyphs: Map<String, GlyphGrid>,
  words: Map<String, WordDef>,
  dictionary: Map<String, DictionaryEntry>,
  language: AppLanguage,
  onWordClick: (String) -> Unit,
  onTrigger: (String) -> Unit,
) {
  val hasButton = "button" in levelDef.unlockedControls
  val toggles = levelDef.unlockedControls.filter { it in listOf("lever", "key", "conduit", "lock") }
  val numerics = levelDef.unlockedControls.filter { it in listOf("dial", "valve") }

  PixelStonePanel(
    containerColor = KidouColors.Panel,
    borderColor = KidouColors.BorderBright,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (toggles.isNotEmpty()) {
        toggles.chunked(2).forEach { pair ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            pair.forEach { control ->
              Box(modifier = Modifier.weight(1f)) {
                ToggleModule(
                  control = control,
                  state = levelState,
                  glyphs = glyphs,
                  words = words,
                  dictionary = dictionary,
                  language = language,
                  onWordClick = onWordClick,
                  onTrigger = onTrigger,
                )
              }
            }
            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
          }
        }
      }

      if (numerics.isNotEmpty()) {
        numerics.forEach { control ->
          NumericModule(
            control = control,
            value = if (control == "dial") levelState.dial else levelState.valve,
            glyphs = glyphs,
            words = words,
            dictionary = dictionary,
            language = language,
            onWordClick = onWordClick,
            onTrigger = onTrigger,
          )
        }
      }

      if (hasButton) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .background(KidouColors.PanelSunken, RectangleShape)
              .border(1.5.dp, KidouColors.Border, RectangleShape)
              .padding(6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GlyphWordTile(
              wordId = "n_button",
              grid = glyphs["n_button"],
              wordDef = words["n_button"],
              entry = dictionary["n_button"],
              language = language,
              onClick = { onWordClick("n_button") },
              compact = true,
            )
            GlyphWordTile(
              wordId = "v_push",
              grid = glyphs["v_push"],
              wordDef = words["v_push"],
              entry = dictionary["v_push"],
              language = language,
              onClick = { onWordClick("v_push") },
              compact = true,
            )
          }

          PixelBevelPad(
            borderColor = KidouColors.Amber,
            fillColor = KidouColors.Amber.copy(alpha = 0.22f),
            onClick = { onTrigger("button_push") },
            modifier =
              Modifier
                .weight(1f)
                .padding(start = 10.dp)
                .height(48.dp)
                .semantics { contentDescription = "control_button" },
          ) {
            Box(
              modifier =
                Modifier
                  .size(16.dp)
                  .background(KidouColors.Amber, RectangleShape),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ToggleModule(
  control: String,
  state: LevelRuntimeState,
  glyphs: Map<String, GlyphGrid>,
  words: Map<String, WordDef>,
  dictionary: Map<String, DictionaryEntry>,
  language: AppLanguage,
  onWordClick: (String) -> Unit,
  onTrigger: (String) -> Unit,
) {
  val (nounId, stateWordId, triggerName) =
    when (control) {
      "lever" ->
        Triple(
          "n_lever",
          if (state.lever == "up") "v_up" else "v_down",
          if (state.lever == "up") "lever_pull" else "lever_push",
        )
      "key" ->
        Triple(
          "n_key",
          if (state.key == "in") "v_insert" else "v_remove",
          if (state.key == "in") "key_remove" else "key_insert",
        )
      "conduit" ->
        Triple(
          "n_conduit",
          if (state.conduit == "connected") "v_connect" else "v_cut",
          if (state.conduit == "connected") "conduit_cut" else "conduit_connect",
        )
      else ->
        Triple(
          "n_lock",
          if (state.lock == "locked") "v_lock" else "v_unlock",
          if (state.lock == "locked") "lock_unlock" else "lock_lock",
        )
    }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(KidouColors.PanelSunken, RectangleShape)
        .border(1.5.dp, KidouColors.Border, RectangleShape)
        .padding(5.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
      GlyphWordTile(
        wordId = nounId,
        grid = glyphs[nounId],
        wordDef = words[nounId],
        entry = dictionary[nounId],
        language = language,
        onClick = { onWordClick(nounId) },
        compact = true,
      )
      GlyphWordTile(
        wordId = stateWordId,
        grid = glyphs[stateWordId],
        wordDef = words[stateWordId],
        entry = dictionary[stateWordId],
        language = language,
        onClick = { onWordClick(stateWordId) },
        compact = true,
      )
    }

    PixelBevelPad(
      onClick = { onTrigger(triggerName) },
      modifier =
        Modifier
          .size(width = 44.dp, height = 42.dp)
          .semantics { contentDescription = "control_$control" },
    ) {
      when (control) {
        "lever" -> {
          val up = state.lever == "up"
          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(20.dp, 12.dp).background(if (up) KidouColors.Amber else KidouColors.LampOff))
            Box(Modifier.size(20.dp, 12.dp).background(if (!up) KidouColors.Cyan else KidouColors.LampOff))
          }
        }
        "key" -> {
          val inserted = state.key == "in"
          Canvas(modifier = Modifier.size(26.dp)) {
            drawRect(KidouColors.BorderBright, Offset(4f, 3f), Size(size.width - 8f, size.height - 6f))
            drawRect(
              if (inserted) KidouColors.Green else KidouColors.PanelSunken,
              Offset(9f, 7f),
              Size(size.width - 18f, size.height - 14f),
            )
          }
        }
        "conduit" -> {
          val connected = state.conduit == "connected"
          Canvas(modifier = Modifier.size(30.dp, 20.dp)) {
            drawRect(KidouColors.Glyph, Offset(1f, size.height * 0.25f), Size(7f, size.height * 0.5f))
            drawRect(KidouColors.Glyph, Offset(size.width - 8f, size.height * 0.25f), Size(7f, size.height * 0.5f))
            if (connected) {
              drawRect(KidouColors.Cyan, Offset(8f, size.height * 0.36f), Size(size.width - 16f, size.height * 0.28f))
            } else {
              drawRect(KidouColors.Red, Offset(size.width * 0.46f, 2f), Size(3f, size.height - 4f))
            }
          }
        }
        else -> {
          val locked = state.lock == "locked"
          PixelGlyph(
            grid = glyphs[if (locked) "v_lock" else "v_unlock"],
            size = 20.dp,
            color = if (locked) KidouColors.Amber else KidouColors.Green,
          )
        }
      }
    }
  }
}

@Composable
private fun NumericModule(
  control: String,
  value: Int,
  glyphs: Map<String, GlyphGrid>,
  words: Map<String, WordDef>,
  dictionary: Map<String, DictionaryEntry>,
  language: AppLanguage,
  onWordClick: (String) -> Unit,
  onTrigger: (String) -> Unit,
) {
  val nounId = if (control == "dial") "n_dial" else "n_valve"
  val digits = Base5.toDigitWordIds(value)
  val decTrig = if (control == "dial") "dial_dec" else "valve_dec"
  val incTrig = if (control == "dial") "dial_inc" else "valve_inc"
  val inc5Trig = if (control == "dial") "dial_inc5" else "valve_inc5"

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(KidouColors.PanelSunken, RectangleShape)
        .border(1.5.dp, KidouColors.Border, RectangleShape)
        .padding(6.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      GlyphWordTile(
        wordId = nounId,
        grid = glyphs[nounId],
        wordDef = words[nounId],
        entry = dictionary[nounId],
        language = language,
        onClick = { onWordClick(nounId) },
        compact = true,
      )
      digits.forEach { dId ->
        GlyphWordTile(
          wordId = dId,
          grid = glyphs[dId],
          wordDef = words[dId],
          entry = dictionary[dId],
          language = language,
          onClick = { onWordClick(dId) },
          compact = true,
          glyphColor = KidouColors.Amber,
        )
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      PixelBevelPad(
        onClick = { onTrigger(decTrig) },
        modifier =
          Modifier
            .size(width = 36.dp, height = 40.dp)
            .semantics { contentDescription = "${control}_dec" },
      ) {
        Text("-", color = KidouColors.Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
      }
      PixelBevelPad(
        onClick = { onTrigger(incTrig) },
        modifier =
          Modifier
            .size(width = 36.dp, height = 40.dp)
            .semantics { contentDescription = "${control}_inc" },
      ) {
        Text("+", color = KidouColors.Amber, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
      }
      PixelBevelPad(
        onClick = { onTrigger(inc5Trig) },
        modifier =
          Modifier
            .size(width = 42.dp, height = 40.dp)
            .semantics { contentDescription = "${control}_inc5" },
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("+", color = KidouColors.Green, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          PixelGlyph(grid = glyphs["d_1"], size = 9.dp, color = KidouColors.Green)
          PixelGlyph(grid = glyphs["d_0"], size = 9.dp, color = KidouColors.Green)
        }
      }
    }
  }
}
