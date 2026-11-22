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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidou.game.model.DictionaryEntry
import com.kidou.game.model.GlyphGrid
import com.kidou.game.model.LampState
import com.kidou.game.model.MachineRuntimeState
import com.kidou.game.ui.components.GlyphSentenceRow
import com.kidou.game.ui.components.GlyphWordTile
import com.kidou.game.ui.components.PixelGlyph
import com.kidou.game.ui.theme.KidouColors
import com.kidou.game.viewmodel.DictionaryTab
import com.kidou.game.viewmodel.GameUiState
import com.kidou.game.viewmodel.GameViewModel

@Composable
fun MainScreen(viewModel: GameViewModel) {
  val uiState by viewModel.uiState.collectAsState()
  val data = uiState.gameData ?: return
  val haptics = LocalHapticFeedback.current

  val currentPhaseDef = data.machine.phaseByNumber(uiState.machineState.currentPhase)

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .background(KidouColors.Background)
        .safeDrawingPadding()
        .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // 1. Minimal Top Bar: Machine Name + Dictionary & Menu
    TopBar(
      machineNameSlots = data.machine.machineNameSlots,
      glyphs = data.glyphs,
      dictionary = uiState.dictionary,
      onWordClick = { viewModel.openWordEditor(it) },
      onOpenDictionary = { viewModel.openDictionary(DictionaryTab.SCREEN) },
      onOpenMenu = { viewModel.openMenu() },
    )

    // 2. Phase Strip (shows 1st..5th naturally in order)
    PhaseStrip(
      currentPhase = uiState.machineState.currentPhase,
      maxUnlockedPhase = uiState.machineState.maxUnlockedPhase,
      isFullyActivated = uiState.machineState.isFullyActivated,
      glyphs = data.glyphs,
      dictionary = uiState.dictionary,
      onSelectPhase = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.selectPhase(it)
      },
      onWordClick = { viewModel.openWordEditor(it) },
    )

    // 3. Machine Chassis (Readout + Engraved Plates)
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .weight(1f)
          .background(KidouColors.Panel, RoundedCornerShape(2.dp))
          .border(1.dp, KidouColors.Border, RoundedCornerShape(2.dp))
          .verticalScroll(rememberScrollState())
          .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      StatusIndicatorBar(
        state = uiState.machineState,
        totalSeqSteps = currentPhaseDef.totalSeqSteps,
      )

      // Recessed Machine Readout Screen
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(KidouColors.PanelSunken, RoundedCornerShape(2.dp))
            .border(1.dp, KidouColors.BorderBright.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (uiState.machineState.primaryMessage.isNotEmpty()) {
          GlyphSentenceRow(
            slots = uiState.machineState.primaryMessage,
            glyphs = data.glyphs,
            dictionary = uiState.dictionary,
            onWordClick = { viewModel.openWordEditor(it) },
            glyphColor =
              if (uiState.machineState.lamp == LampState.RED) KidouColors.Red else KidouColors.Glyph,
          )
        }
        if (uiState.machineState.secondaryMessage.isNotEmpty()) {
          GlyphSentenceRow(
            slots = uiState.machineState.secondaryMessage,
            glyphs = data.glyphs,
            dictionary = uiState.dictionary,
            onWordClick = { viewModel.openWordEditor(it) },
            compact = true,
            glyphColor =
              when (uiState.machineState.lamp) {
                LampState.GREEN -> KidouColors.Green
                LampState.RED -> KidouColors.Amber
                else -> KidouColors.Glyph.copy(alpha = 0.88f)
              },
          )
        }
      }

      // Engraved Plates (Instructions / Labels)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        currentPhaseDef.plates.forEachIndexed { idx, plateSlots ->
          val stepComplete =
            currentPhaseDef.totalSeqSteps > 0 && idx < uiState.machineState.seqStep
          EngravedPlate(
            slots = plateSlots,
            glyphs = data.glyphs,
            dictionary = uiState.dictionary,
            highlightComplete = stepComplete,
            onWordClick = { viewModel.openWordEditor(it) },
          )
        }
      }
    }

    // 4. Tactile Control Deck
    ControlDeck(
      unlockedControls = currentPhaseDef.unlockedControls,
      machineState = uiState.machineState,
      glyphs = data.glyphs,
      dictionary = uiState.dictionary,
      onWordClick = { viewModel.openWordEditor(it) },
      onTrigger = { trigger ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.triggerControl(trigger)
      },
    )
  }

  // Sheets & Dialogs
  val editingId = uiState.editingWordId
  if (editingId != null) {
    WordEditorSheet(
      wordId = editingId,
      glyphs = data.glyphs,
      dictionary = uiState.dictionary,
      contexts = viewModel.findWordContexts(editingId),
      onSave = { trans, tag, memo -> viewModel.saveWordEntry(editingId, trans, tag, memo) },
      onSwitchWord = { nextId -> viewModel.openWordEditor(nextId) },
      onDismiss = { viewModel.closeWordEditor() },
    )
  }

  if (uiState.showDictionary) {
    DictionarySheet(
      uiState = uiState,
      onSelectTab = { viewModel.setDictionaryTab(it) },
      onEditWord = { wordId -> viewModel.openWordEditor(wordId) },
      onDismiss = { viewModel.closeDictionary() },
    )
  }

  if (uiState.showMenu) {
    MenuSheet(
      uiState = uiState,
      onSelectTab = { viewModel.setMenuTab(it) },
      onEditWord = { wordId -> viewModel.openWordEditor(wordId) },
      onToggleSound = { viewModel.setSoundEnabled(it) },
      onResetAll = { viewModel.resetAll() },
      onDismiss = { viewModel.closeMenu() },
    )
  }

  if (uiState.showCompletionDialog) {
    CoreActivatedDialog(
      uiState = uiState,
      onDismiss = { viewModel.dismissCompletionDialog() },
      onReset = { viewModel.resetAll() },
    )
  }
}

@Composable
private fun TopBar(
  machineNameSlots: List<List<String>>,
  glyphs: Map<String, GlyphGrid>,
  dictionary: Map<String, DictionaryEntry>,
  onWordClick: (String) -> Unit,
  onOpenDictionary: () -> Unit,
  onOpenMenu: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    GlyphSentenceRow(
      slots = machineNameSlots,
      glyphs = glyphs,
      dictionary = dictionary,
      onWordClick = onWordClick,
      compact = true,
      modifier = Modifier.weight(1f),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TopActionButton(label = "辞書", accent = KidouColors.Amber, onClick = onOpenDictionary)
      TopActionButton(label = "メニュー", accent = KidouColors.Muted, onClick = onOpenMenu)
    }
  }
}

@Composable
private fun TopActionButton(label: String, accent: Color, onClick: () -> Unit) {
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier
        .clip(RoundedCornerShape(2.dp))
        .background(KidouColors.Panel)
        .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Text(
      text = label,
      color = if (accent == KidouColors.Muted) KidouColors.Glyph else accent,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      fontSize = 12.sp,
    )
  }
}

@Composable
private fun PhaseStrip(
  currentPhase: Int,
  maxUnlockedPhase: Int,
  isFullyActivated: Boolean,
  glyphs: Map<String, GlyphGrid>,
  dictionary: Map<String, DictionaryEntry>,
  onSelectPhase: (Int) -> Unit,
  onWordClick: (String) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    GlyphWordTile(
      wordId = "n_phase",
      grid = glyphs["n_phase"],
      entry = dictionary["n_phase"],
      onClick = { onWordClick("n_phase") },
      compact = true,
    )

    Row(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      for (p in 1..5) {
        val unlocked = p <= maxUnlockedPhase
        val active = p == currentPhase
        val completed = p < maxUnlockedPhase || (p == 5 && isFullyActivated)
        val borderColor =
          when {
            active -> KidouColors.Amber
            completed -> KidouColors.Green.copy(alpha = 0.65f)
            unlocked -> KidouColors.BorderBright
            else -> KidouColors.Border.copy(alpha = 0.45f)
          }
        val bgColor =
          when {
            active -> KidouColors.Amber.copy(alpha = 0.16f)
            completed -> KidouColors.Green.copy(alpha = 0.08f)
            unlocked -> KidouColors.Panel
            else -> KidouColors.Background
          }
        val glyphColor =
          when {
            active -> KidouColors.Amber
            completed -> KidouColors.Green
            unlocked -> KidouColors.Glyph
            else -> KidouColors.Muted.copy(alpha = 0.3f)
          }

        Row(
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier
              .weight(1f)
              .height(44.dp)
              .clip(RoundedCornerShape(2.dp))
              .background(bgColor)
              .border(if (active) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(2.dp))
              .semantics { contentDescription = "phase_$p" }
              .then(if (unlocked) Modifier.clickable { onSelectPhase(p) } else Modifier),
        ) {
          if (unlocked) {
            PixelGlyph(grid = glyphs["num_$p"], size = 14.dp, color = glyphColor)
            Spacer(modifier = Modifier.width(2.dp))
            PixelGlyph(grid = glyphs["m_ord"], size = 12.dp, color = glyphColor)
          } else {
            Text(
              text = "--",
              color = KidouColors.Muted.copy(alpha = 0.35f),
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StatusIndicatorBar(
  state: MachineRuntimeState,
  totalSeqSteps: Int,
) {
  val lampColor = lampColorOf(state.lamp)

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier =
          Modifier
            .size(16.dp)
            .background(lampColor, RoundedCornerShape(2.dp))
            .border(1.dp, KidouColors.Glyph.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
            .semantics { contentDescription = "lamp_${state.lamp.name.lowercase()}" },
      )
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val bars = 4
        for (i in 0 until bars) {
          val lit =
            when (state.lamp) {
              LampState.GREEN -> true
              LampState.AMBER -> i < 2
              LampState.RED -> i == 0 || i == 3
              LampState.OFF -> false
            }
          Box(
            modifier =
              Modifier
                .width(12.dp)
                .height(6.dp)
                .background(if (lit) lampColor.copy(alpha = 0.85f) else KidouColors.LampOff),
          )
        }
      }
    }

    if (totalSeqSteps > 0) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (s in 0 until totalSeqSteps) {
          val done = s < state.seqStep
          Box(
            modifier =
              Modifier
                .size(10.dp)
                .background(if (done) KidouColors.Green else KidouColors.LampOff)
                .border(1.dp, KidouColors.BorderBright),
          )
        }
      }
    } else if (state.currentPhase == 5) {
      val checks =
        listOf(
          state.conduit == "connected",
          state.key == "in",
          state.dial == 5,
          state.lever == "up",
        )
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        checks.forEach { ok ->
          Box(
            modifier =
              Modifier
                .size(10.dp)
                .background(if (ok) KidouColors.Cyan else KidouColors.LampOff)
                .border(1.dp, KidouColors.BorderBright),
          )
        }
      }
    }
  }
}

@Composable
private fun EngravedPlate(
  slots: List<List<String>>,
  glyphs: Map<String, GlyphGrid>,
  dictionary: Map<String, DictionaryEntry>,
  highlightComplete: Boolean,
  onWordClick: (String) -> Unit,
) {
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(
          if (highlightComplete) KidouColors.Green.copy(alpha = 0.08f) else KidouColors.Tile.copy(alpha = 0.55f),
          RoundedCornerShape(2.dp),
        )
        .border(
          1.dp,
          if (highlightComplete) KidouColors.Green.copy(alpha = 0.65f) else KidouColors.Border,
          RoundedCornerShape(2.dp),
        )
        .padding(horizontal = 10.dp, vertical = 8.dp),
  ) {
    GlyphSentenceRow(
      slots = slots,
      glyphs = glyphs,
      dictionary = dictionary,
      onWordClick = onWordClick,
      compact = true,
    )
  }
}

@Composable
private fun ControlDeck(
  unlockedControls: List<String>,
  machineState: MachineRuntimeState,
  glyphs: Map<String, GlyphGrid>,
  dictionary: Map<String, DictionaryEntry>,
  onWordClick: (String) -> Unit,
  onTrigger: (String) -> Unit,
) {
  val secondaryControls = unlockedControls.filter { it != "button" }
  val hasButton = "button" in unlockedControls

  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(KidouColors.Panel, RoundedCornerShape(2.dp))
        .border(1.dp, KidouColors.Border, RoundedCornerShape(2.dp))
        .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (secondaryControls.isNotEmpty()) {
      secondaryControls.chunked(2).forEach { pair ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          pair.forEach { control ->
            Box(modifier = Modifier.weight(1f)) {
              SecondaryControlTile(
                control = control,
                machineState = machineState,
                glyphs = glyphs,
                dictionary = dictionary,
                onWordClick = onWordClick,
                onTrigger = onTrigger,
              )
            }
          }
          if (pair.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }

    if (hasButton) {
      PushButtonControl(
        glyphs = glyphs,
        dictionary = dictionary,
        onWordClick = onWordClick,
        onPush = { onTrigger("button_push") },
      )
    }
  }
}

@Composable
private fun SecondaryControlTile(
  control: String,
  machineState: MachineRuntimeState,
  glyphs: Map<String, GlyphGrid>,
  dictionary: Map<String, DictionaryEntry>,
  onWordClick: (String) -> Unit,
  onTrigger: (String) -> Unit,
) {
  when (control) {
    "lever" -> {
      val isUp = machineState.lever == "up"
      val stateWord = if (isUp) "v_up" else "v_down"
      ControlModuleRow(
        leftWordId = "n_lever",
        stateWordId = stateWord,
        glyphs = glyphs,
        dictionary = dictionary,
        onWordClick = onWordClick,
        actuatorDesc = "control_lever",
        onActuate = { onTrigger(if (isUp) "lever_pull" else "lever_push") },
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Box(
            modifier =
              Modifier
                .size(width = 22.dp, height = 14.dp)
                .background(if (isUp) KidouColors.Amber else KidouColors.LampOff),
          )
          Box(
            modifier =
              Modifier
                .size(width = 22.dp, height = 14.dp)
                .background(if (!isUp) KidouColors.Cyan else KidouColors.LampOff),
          )
        }
      }
    }

    "dial" -> {
      val dialWord = "num_${machineState.dial.coerceIn(1, 5)}"
      ControlModuleRow(
        leftWordId = "n_dial",
        stateWordId = dialWord,
        glyphs = glyphs,
        dictionary = dictionary,
        onWordClick = onWordClick,
        actuatorDesc = "control_dial",
        onActuate = { onTrigger("dial_turn") },
      ) {
        DialWheelVisual(value = machineState.dial)
      }
    }

    "key" -> {
      val isIn = machineState.key == "in"
      val stateWord = if (isIn) "v_insert" else "v_remove"
      ControlModuleRow(
        leftWordId = "n_key",
        stateWordId = stateWord,
        glyphs = glyphs,
        dictionary = dictionary,
        onWordClick = onWordClick,
        actuatorDesc = "control_key",
        onActuate = { onTrigger(if (isIn) "key_remove" else "key_insert") },
      ) {
        KeySocketVisual(inserted = isIn)
      }
    }

    "conduit" -> {
      val connected = machineState.conduit == "connected"
      val stateWord = if (connected) "v_connect" else "v_cut"
      ControlModuleRow(
        leftWordId = "n_conduit",
        stateWordId = stateWord,
        glyphs = glyphs,
        dictionary = dictionary,
        onWordClick = onWordClick,
        actuatorDesc = "control_conduit",
        onActuate = { onTrigger(if (connected) "conduit_cut" else "conduit_connect") },
      ) {
        ConduitBridgeVisual(connected = connected)
      }
    }
  }
}

@Composable
private fun ControlModuleRow(
  leftWordId: String,
  stateWordId: String,
  glyphs: Map<String, GlyphGrid>,
  dictionary: Map<String, DictionaryEntry>,
  onWordClick: (String) -> Unit,
  actuatorDesc: String,
  onActuate: () -> Unit,
  actuatorVisual: @Composable () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(KidouColors.PanelSunken, RoundedCornerShape(2.dp))
        .border(1.dp, KidouColors.Border, RoundedCornerShape(2.dp))
        .padding(6.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      GlyphWordTile(
        wordId = leftWordId,
        grid = glyphs[leftWordId],
        entry = dictionary[leftWordId],
        onClick = { onWordClick(leftWordId) },
        compact = true,
      )
      GlyphWordTile(
        wordId = stateWordId,
        grid = glyphs[stateWordId],
        entry = dictionary[stateWordId],
        onClick = { onWordClick(stateWordId) },
        compact = true,
      )
    }

    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier
          .size(width = 48.dp, height = 44.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(KidouColors.Tile)
          .border(1.dp, KidouColors.BorderBright, RoundedCornerShape(2.dp))
          .semantics { contentDescription = actuatorDesc }
          .clickable(role = Role.Button, onClick = onActuate),
    ) {
      actuatorVisual()
    }
  }
}

@Composable
private fun PushButtonControl(
  glyphs: Map<String, GlyphGrid>,
  dictionary: Map<String, DictionaryEntry>,
  onWordClick: (String) -> Unit,
  onPush: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(KidouColors.PanelSunken, RoundedCornerShape(2.dp))
        .border(1.dp, KidouColors.Border, RoundedCornerShape(2.dp))
        .padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      GlyphWordTile(
        wordId = "n_button",
        grid = glyphs["n_button"],
        entry = dictionary["n_button"],
        onClick = { onWordClick("n_button") },
        compact = true,
      )
      GlyphWordTile(
        wordId = "v_push",
        grid = glyphs["v_push"],
        entry = dictionary["v_push"],
        onClick = { onWordClick("v_push") },
        compact = true,
      )
    }

    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier
          .weight(1f)
          .padding(start = 10.dp)
          .height(48.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(KidouColors.Amber.copy(alpha = 0.2f))
          .border(1.5.dp, KidouColors.Amber, RoundedCornerShape(2.dp))
          .semantics { contentDescription = "control_button" }
          .clickable(role = Role.Button, onClick = onPush),
    ) {
      Box(
        modifier =
          Modifier
            .size(16.dp)
            .background(KidouColors.Amber, RoundedCornerShape(2.dp)),
      )
    }
  }
}

@Composable
private fun DialWheelVisual(value: Int) {
  Canvas(modifier = Modifier.size(30.dp)) {
    val w = size.width
    val h = size.height
    drawRect(
      color = KidouColors.BorderBright,
      topLeft = Offset(3f, 3f),
      size = Size(w - 6f, h - 6f),
    )
    drawRect(
      color = KidouColors.PanelSunken,
      topLeft = Offset(7f, 7f),
      size = Size(w - 14f, h - 14f),
    )
    // Highlight notch position around 5 steps
    val notchPos =
      when (value) {
        1 -> Offset(w * 0.2f, h * 0.2f)
        2 -> Offset(w * 0.5f, h * 0.1f)
        3 -> Offset(w * 0.78f, h * 0.2f)
        4 -> Offset(w * 0.78f, h * 0.65f)
        else -> Offset(w * 0.2f, h * 0.65f)
      }
    drawRect(
      color = KidouColors.Amber,
      topLeft = notchPos,
      size = Size(w * 0.22f, h * 0.22f),
    )
  }
}

@Composable
private fun KeySocketVisual(inserted: Boolean) {
  Canvas(modifier = Modifier.size(30.dp)) {
    val w = size.width
    val h = size.height
    // Outer socket
    drawRect(
      color = KidouColors.BorderBright,
      topLeft = Offset(w * 0.2f, h * 0.15f),
      size = Size(w * 0.6f, h * 0.7f),
    )
    drawRect(
      color = if (inserted) KidouColors.Green else KidouColors.PanelSunken,
      topLeft = Offset(w * 0.34f, h * 0.28f),
      size = Size(w * 0.32f, h * 0.44f),
    )
  }
}

@Composable
private fun ConduitBridgeVisual(connected: Boolean) {
  Canvas(modifier = Modifier.size(34.dp, 24.dp)) {
    val w = size.width
    val h = size.height
    // Left terminal & right terminal
    drawRect(color = KidouColors.Glyph, topLeft = Offset(2f, h * 0.25f), size = Size(8f, h * 0.5f))
    drawRect(color = KidouColors.Glyph, topLeft = Offset(w - 10f, h * 0.25f), size = Size(8f, h * 0.5f))
    if (connected) {
      drawRect(
        color = KidouColors.Cyan,
        topLeft = Offset(10f, h * 0.38f),
        size = Size(w - 20f, h * 0.24f),
      )
    } else {
      drawRect(
        color = KidouColors.Red.copy(alpha = 0.7f),
        topLeft = Offset(w * 0.45f, h * 0.1f),
        size = Size(3f, h * 0.8f),
      )
    }
  }
}

@Composable
private fun CoreActivatedDialog(
  uiState: GameUiState,
  onDismiss: () -> Unit,
  onReset: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = KidouColors.Panel,
    titleContentColor = KidouColors.Green,
    textContentColor = KidouColors.Glyph,
    shape = RoundedCornerShape(4.dp),
    title = {
      Text(
        text = "中核装置 完全起動",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "5つの起動フェーズをすべて完了した。",
          fontFamily = FontFamily.Monospace,
          fontSize = 13.sp,
        )
        Text(
          text =
            "遭遇語彙: ${uiState.discoveredWordIds.size} / ${uiState.totalWordsCount}\n" +
              "登録語数: ${uiState.registeredCount}語（解読率 ${uiState.decodeRatePercent}%）",
          fontFamily = FontFamily.Monospace,
          fontSize = 12.sp,
          color = KidouColors.Amber,
        )
        Text(
          text = "辞書の確認や訳語の書き換えはこのまま続けられる。",
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = KidouColors.Muted,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onDismiss,
        shape = RoundedCornerShape(2.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = KidouColors.Green,
            contentColor = KidouColors.Background,
          ),
      ) {
        Text("装置へ戻る", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      OutlinedButton(
        onClick = onReset,
        shape = RoundedCornerShape(2.dp),
      ) {
        Text("最初から", fontFamily = FontFamily.Monospace, color = KidouColors.Muted)
      }
    },
  )
}
