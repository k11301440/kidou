package com.kidou.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.kidou.game.ui.components.EmbeddedGlyphWithRuby
import com.kidou.game.ui.components.GlyphSentenceRow
import com.kidou.game.ui.components.GlyphWordTile
import com.kidou.game.ui.components.LocalPadPressed
import com.kidou.game.ui.components.PixelActionButton
import com.kidou.game.ui.components.PixelBevelPad
import com.kidou.game.ui.components.PixelCrtDisplay
import com.kidou.game.ui.components.PixelGlyph
import com.kidou.game.ui.components.PixelLed
import com.kidou.game.ui.components.PixelStonePanel
import com.kidou.game.ui.components.rememberKidouHapticClick
import com.kidou.game.ui.i18n.stringsFor
import com.kidou.game.ui.theme.DotGothic16FontFamily
import com.kidou.game.ui.theme.KidouTheme
import com.kidou.game.viewmodel.GameUiState
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LevelScreen(
  uiState: GameUiState,
  onBackToBoard: () -> Unit,
  onOpenNextLevel: (Int, Int) -> Unit,
  onTrigger: (String) -> Unit,
  onTickTicker: () -> Unit,
  onWordClick: (String) -> Unit,
  onOpenDictionary: () -> Unit,
  onOpenMenu: () -> Unit,
) {
  val data = uiState.gameData ?: return
  val levelState = uiState.activeLevelState ?: return
  val boardDef = data.boardCollection.boardById(levelState.boardId)
  val levelDef = boardDef.levelByNumber(levelState.levelNumber) ?: return
  val palette = KidouTheme.palette
  val strings = stringsFor(uiState.language)
  val nextLevelExists = boardDef.levelByNumber(levelState.levelNumber + 1) != null
  val displayScroll = rememberScrollState()
  val coroutineScope = rememberCoroutineScope()
  var viewportHeightPx by remember { mutableStateOf(1f) }
  val req = levelDef.requirements

  // Cycling digit display loop (non-failing, repeats continuously while not cleared)
  LaunchedEffect(
    levelState.boardId,
    levelState.levelNumber,
    levelDef.tickerEnabled,
    levelState.isCleared,
  ) {
    if (!levelDef.tickerEnabled || levelState.isCleared) return@LaunchedEffect
    val interval = levelDef.tickerIntervalMs.coerceAtLeast(450L)
    while (true) {
      delay(interval)
      onTickTicker()
    }
  }

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .background(palette.background)
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
        accent = palette.accentSecondary,
        onClick = onBackToBoard,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelActionButton(
          label = strings.dictionary,
          accent = palette.accentPrimary,
          onClick = onOpenDictionary,
        )
        PixelActionButton(
          label = strings.menu,
          accent = palette.muted,
          onClick = onOpenMenu,
        )
      }
    }

    // 2. Upper Half: Machine Chassis + Scrollable CRT & Plates
    PixelStonePanel(
      borderColor = if (levelState.isCleared) palette.statusSuccess else palette.borderBright,
      modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Pinned Header: Machine Name + Base-5 Level Number + Status LEDs
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
              color = lampColorOf(levelState.lamp, palette),
              size = 14.dp,
              modifier = Modifier.semantics { contentDescription = "lamp_${levelState.lamp.name.lowercase()}" },
            )
            val stepTotal =
              when {
                levelDef.sequenceSteps.isNotEmpty() -> levelDef.sequenceSteps.size
                req?.orderSequence?.isNotEmpty() == true -> req.orderSequence.size
                req?.tickerTargets?.isNotEmpty() == true -> req.tickerTargets.size
                else -> 0
              }
            val stepCompleted =
              when {
                levelDef.sequenceSteps.isNotEmpty() -> levelState.seqStep
                req?.orderSequence?.isNotEmpty() == true -> levelState.orderProgress
                req?.tickerTargets?.isNotEmpty() == true -> levelState.tickerCaptures
                else -> 0
              }
            for (stepIdx in 0 until stepTotal) {
              PixelLed(
                color =
                  if (stepIdx < stepCompleted || levelState.isCleared) palette.statusSuccess
                  else palette.lampOff,
                size = 10.dp,
              )
            }
          }
        }

        // Scrollable Display Area + Right Scrollbar + Down Arrow Indicator
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .weight(1f)
              .onSizeChanged { viewportHeightPx = it.height.toFloat().coerceAtLeast(1f) },
        ) {
          Column(
            modifier =
              Modifier
                .fillMaxSize()
                .verticalScroll(displayScroll)
                .padding(end = if (displayScroll.maxValue > 0) 10.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            // Recessed Phosphor CRT Readout
            PixelCrtDisplay(
              borderColor =
                when (levelState.lamp) {
                  LampState.GREEN -> palette.statusSuccess
                  LampState.RED -> palette.statusDanger
                  else -> palette.borderBright
                },
            ) {
              if (levelDef.tickerEnabled) {
                val digitId = "d_${levelState.tickerDigit}"
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .background(palette.tile.copy(alpha = 0.72f), RectangleShape)
                      .border(
                        1.5.dp,
                        if (levelState.isCleared) palette.statusSuccess else palette.accentPrimary.copy(alpha = 0.75f),
                        RectangleShape,
                      )
                      .padding(horizontal = 8.dp, vertical = 6.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  GlyphWordTile(
                    wordId = "n_signal",
                    grid = data.glyphs["n_signal"],
                    wordDef = data.words["n_signal"],
                    entry = uiState.dictionary["n_signal"],
                    language = uiState.language,
                    onClick = { onWordClick("n_signal") },
                    compact = true,
                  )
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                      levelDef.tickerSequence.forEachIndexed { idx, _ ->
                        val active = idx == levelState.tickerIndex
                        Box(
                          modifier =
                            Modifier
                              .size(7.dp)
                              .background(
                                if (active) palette.accentPrimary else palette.lampOff,
                                RectangleShape,
                              ),
                        )
                      }
                    }
                    Box(
                      contentAlignment = Alignment.Center,
                      modifier =
                        Modifier
                          .size(44.dp)
                          .background(palette.panelSunken, RectangleShape)
                          .border(
                            1.5.dp,
                            if (levelState.isCleared) palette.statusSuccess else palette.accentPrimary,
                            RectangleShape,
                          )
                          .semantics { contentDescription = "ticker_digit_${levelState.tickerDigit}" },
                    ) {
                      PixelGlyph(
                        grid = data.glyphs[digitId],
                        size = 28.dp,
                        color = if (levelState.isCleared) palette.statusSuccess else palette.accentPrimary,
                      )
                    }
                  }
                }
              }

              if (levelState.primaryMessage.isNotEmpty()) {
                GlyphSentenceRow(
                  slots = levelState.primaryMessage,
                  glyphs = data.glyphs,
                  words = data.words,
                  dictionary = uiState.dictionary,
                  language = uiState.language,
                  onWordClick = onWordClick,
                  glyphColor =
                    when (levelState.lamp) {
                      LampState.RED -> palette.statusDanger
                      LampState.GREEN -> palette.statusSuccess
                      else -> palette.glyph
                    },
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
                      LampState.GREEN -> palette.statusSuccess
                      LampState.RED -> palette.statusWarning
                      else -> palette.glyph.copy(alpha = 0.88f)
                    },
                )
              }
            }

            // Engraved Instruction Plates (or masked memory blanks if memoryMode && hasOperated)
            val hidePlates = levelDef.memoryMode && levelState.hasOperated && !levelState.isCleared
            if (hidePlates) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                GlyphWordTile(
                  wordId = "v_hide",
                  grid = data.glyphs["v_hide"],
                  wordDef = data.words["v_hide"],
                  entry = uiState.dictionary["v_hide"],
                  language = uiState.language,
                  onClick = { onWordClick("v_hide") },
                  compact = true,
                  glyphColor = palette.muted,
                )
                PixelActionButton(
                  label = strings.memoryRevealButton,
                  accent = palette.accentSecondary,
                  onClick = { onTrigger("memory_reset") },
                  modifier = Modifier.semantics { contentDescription = "memory_reveal_btn" },
                )
              }
              levelDef.plates.forEach { _ ->
                Box(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .height(34.dp)
                      .background(palette.panelSunken, RectangleShape)
                      .border(1.dp, palette.border.copy(alpha = 0.55f), RectangleShape),
                )
              }
            } else {
              levelDef.plates.forEachIndexed { idx, plateSlots ->
                val stepDone =
                  (levelDef.sequenceSteps.isNotEmpty() && (idx < levelState.seqStep || levelState.isCleared))
                Box(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .background(
                        if (stepDone) palette.statusSuccess.copy(alpha = 0.1f)
                        else palette.tile.copy(alpha = 0.72f),
                        RectangleShape,
                      )
                      .border(
                        1.5.dp,
                        if (stepDone) palette.statusSuccess else palette.border,
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

            if (displayScroll.maxValue > 0) {
              Spacer(modifier = Modifier.height(22.dp))
            }
          }

          // Vertical Pixel Scrollbar Track & Thumb
          if (displayScroll.maxValue > 0) {
            val totalContent = viewportHeightPx + displayScroll.maxValue.toFloat()
            Canvas(
              modifier =
                Modifier
                  .align(Alignment.CenterEnd)
                  .fillMaxHeight()
                  .width(5.dp)
                  .semantics { contentDescription = "display_scrollbar" },
            ) {
              drawRect(color = palette.border.copy(alpha = 0.45f))
              val thumbFraction = (viewportHeightPx / totalContent).coerceIn(0.18f, 0.9f)
              val thumbH = size.height * thumbFraction
              val progress =
                (displayScroll.value.toFloat() / displayScroll.maxValue.toFloat()).coerceIn(0f, 1f)
              val thumbY = (size.height - thumbH) * progress
              drawRect(
                color = palette.accentPrimary,
                topLeft = Offset(0f, thumbY),
                size = Size(size.width, thumbH),
              )
            }
          }

          // Downward Continuation Arrow Indicator
          if (displayScroll.canScrollForward) {
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier
                  .align(Alignment.BottomCenter)
                  .padding(bottom = 2.dp)
                  .background(palette.panel.copy(alpha = 0.92f), RectangleShape)
                  .border(1.5.dp, palette.accentPrimary, RectangleShape)
                  .semantics { contentDescription = "display_scroll_down_hint" }
                  .clickable {
                    coroutineScope.launch {
                      displayScroll.animateScrollTo(displayScroll.value + 200)
                    }
                  }
                  .padding(horizontal = 12.dp, vertical = 2.dp),
            ) {
              Text(
                text = "▼",
                color = palette.accentPrimary,
                fontFamily = DotGothic16FontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
              )
            }
          }
        }
      }
    }

    // 3. Lower Half: Unified Mechanical Control Deck
    ControlDeckPanel(
      levelDef = levelDef,
      levelState = levelState,
      glyphs = data.glyphs,
      words = data.words,
      dictionary = uiState.dictionary,
      language = uiState.language,
      nextLabel = strings.nextLevel,
      onWordClick = onWordClick,
      onTrigger = onTrigger,
      onNextAction = {
        if (nextLevelExists) {
          onOpenNextLevel(levelState.boardId, levelState.levelNumber + 1)
        } else {
          onBackToBoard()
        }
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
  nextLabel: String,
  onWordClick: (String) -> Unit,
  onTrigger: (String) -> Unit,
  onNextAction: () -> Unit,
) {
  val palette = KidouTheme.palette
  val primaryControls = listOf("button", "hold_button", "dual_button", "order_pad")
  val hasPrimary = levelDef.unlockedControls.any { it in primaryControls }
  val primaryType = levelDef.unlockedControls.firstOrNull { it in primaryControls } ?: "button"
  val toggles =
    levelDef.unlockedControls.filter {
      it in listOf("sw_a", "sw_b", "sw_c", "sw_d", "lever", "key", "conduit", "lock")
    }
  val hasRoute = "route" in levelDef.unlockedControls
  val hasSlider = "slider" in levelDef.unlockedControls
  val numerics = levelDef.unlockedControls.filter { it in listOf("dial", "valve") }
  val deckScroll = rememberScrollState()

  PixelStonePanel(
    containerColor = palette.panel,
    borderColor = palette.borderBright,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (toggles.isNotEmpty() || hasRoute || hasSlider || numerics.isNotEmpty()) {
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .heightIn(max = 265.dp)
              .verticalScroll(deckScroll),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (toggles.isNotEmpty()) {
            toggles.chunked(2).forEach { pair ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                pair.forEach { control ->
                  Box(modifier = Modifier.weight(1f)) {
                    UnifiedToggleModule(
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

          if (hasRoute) {
            UnifiedRouteModule(
              currentRoute = levelState.route,
              glyphs = glyphs,
              words = words,
              dictionary = dictionary,
              language = language,
              onWordClick = onWordClick,
              onTrigger = onTrigger,
            )
          }

          if (hasSlider) {
            UnifiedSliderModule(
              sliderValue = levelState.slider,
              glyphs = glyphs,
              words = words,
              dictionary = dictionary,
              language = language,
              onWordClick = onWordClick,
              onTrigger = onTrigger,
            )
          }

          numerics.forEach { control ->
            UnifiedNumericModule(
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
      }

      // Primary Action Area
      if (hasPrimary || levelState.isCleared) {
        if (levelState.isCleared) {
          NextLevelActionRow(
            nextLabel = nextLabel,
            glyphs = glyphs,
            words = words,
            dictionary = dictionary,
            language = language,
            onWordClick = onWordClick,
            onNext = onNextAction,
          )
        } else {
          when (primaryType) {
            "hold_button" ->
              UnifiedHoldButtonRow(
                glyphs = glyphs,
                words = words,
                dictionary = dictionary,
                language = language,
                onWordClick = onWordClick,
                onTrigger = onTrigger,
              )
            "dual_button" ->
              UnifiedDualButtonRow(
                glyphs = glyphs,
                words = words,
                dictionary = dictionary,
                language = language,
                onWordClick = onWordClick,
                onTrigger = onTrigger,
              )
            "order_pad" ->
              UnifiedOrderPadRow(
                glyphs = glyphs,
                words = words,
                dictionary = dictionary,
                language = language,
                onWordClick = onWordClick,
                onTrigger = onTrigger,
              )
            else ->
              UnifiedPushButtonRow(
                glyphs = glyphs,
                words = words,
                dictionary = dictionary,
                language = language,
                onWordClick = onWordClick,
                onPush = { onTrigger("button_push") },
              )
          }
        }
      }
    }
  }
}

@Composable
private fun UnifiedToggleModule(
  control: String,
  state: LevelRuntimeState,
  glyphs: Map<String, GlyphGrid>,
  words: Map<String, WordDef>,
  dictionary: Map<String, DictionaryEntry>,
  language: AppLanguage,
  onWordClick: (String) -> Unit,
  onTrigger: (String) -> Unit,
) {
  val palette = KidouTheme.palette
  val leftIds: List<String>
  val stateWordId: String
  val triggerName: String
  val activeTint: Color

  when (control) {
    "sw_a" -> {
      leftIds = listOf("n_switch", "d_1")
      stateWordId = if (state.swA) "v_activate" else "v_stop"
      triggerName = "sw_a_toggle"
      activeTint = if (state.swA) palette.accentPrimary else palette.muted
    }
    "sw_b" -> {
      leftIds = listOf("n_switch", "d_2")
      stateWordId = if (state.swB) "v_activate" else "v_stop"
      triggerName = "sw_b_toggle"
      activeTint = if (state.swB) palette.accentPrimary else palette.muted
    }
    "sw_c" -> {
      leftIds = listOf("n_switch", "d_3")
      stateWordId = if (state.swC) "v_activate" else "v_stop"
      triggerName = "sw_c_toggle"
      activeTint = if (state.swC) palette.accentPrimary else palette.muted
    }
    "sw_d" -> {
      leftIds = listOf("n_switch", "d_4")
      stateWordId = if (state.swD) "v_activate" else "v_stop"
      triggerName = "sw_d_toggle"
      activeTint = if (state.swD) palette.accentPrimary else palette.muted
    }
    "lever" -> {
      leftIds = listOf("n_lever")
      stateWordId = if (state.lever == "up") "v_up" else "v_down"
      triggerName = if (state.lever == "up") "lever_pull" else "lever_push"
      activeTint = if (state.lever == "down") palette.accentSecondary else palette.accentPrimary
    }
    "key" -> {
      leftIds = listOf("n_key")
      stateWordId = if (state.key == "in") "v_insert" else "v_remove"
      triggerName = if (state.key == "in") "key_remove" else "key_insert"
      activeTint = if (state.key == "in") palette.statusSuccess else palette.muted
    }
    "conduit" -> {
      leftIds = listOf("n_conduit")
      stateWordId = if (state.conduit == "connected") "v_connect" else "v_cut"
      triggerName = if (state.conduit == "connected") "conduit_cut" else "conduit_connect"
      activeTint = if (state.conduit == "connected") palette.accentSecondary else palette.statusDanger
    }
    else -> {
      leftIds = listOf("n_lock")
      stateWordId = if (state.lock == "locked") "v_lock" else "v_unlock"
      triggerName = if (state.lock == "locked") "lock_unlock" else "lock_lock"
      activeTint = if (state.lock == "unlocked") palette.statusSuccess else palette.accentPrimary
    }
  }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.panelSunken, RectangleShape)
        .border(1.5.dp, palette.border, RectangleShape)
        .padding(5.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
      leftIds.forEach { id ->
        GlyphWordTile(
          wordId = id,
          grid = glyphs[id],
          wordDef = words[id],
          entry = dictionary[id],
          language = language,
          onClick = { onWordClick(id) },
          compact = true,
        )
      }
    }

    PixelBevelPad(
      borderColor = activeTint.copy(alpha = 0.85f),
      fillColor = activeTint.copy(alpha = 0.14f),
      onClick = { onTrigger(triggerName) },
      modifier =
        Modifier
          .weight(1f)
          .height(46.dp)
          .semantics { contentDescription = "control_$control" },
    ) {
      EmbeddedGlyphWithRuby(
        wordId = stateWordId,
        grid = glyphs[stateWordId],
        wordDef = words[stateWordId],
        entry = dictionary[stateWordId],
        language = language,
        glyphColor = activeTint,
        glyphSize = 20.dp,
      )
    }
  }
}

@Composable
private fun UnifiedRouteModule(
  currentRoute: Int,
  glyphs: Map<String, GlyphGrid>,
  words: Map<String, WordDef>,
  dictionary: Map<String, DictionaryEntry>,
  language: AppLanguage,
  onWordClick: (String) -> Unit,
  onTrigger: (String) -> Unit,
) {
  val palette = KidouTheme.palette
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.panelSunken, RectangleShape)
        .border(1.5.dp, palette.border, RectangleShape)
        .padding(5.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    GlyphWordTile(
      wordId = "n_route",
      grid = glyphs["n_route"],
      wordDef = words["n_route"],
      entry = dictionary["n_route"],
      language = language,
      onClick = { onWordClick("n_route") },
      compact = true,
    )
    Row(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      for (r in 1..3) {
        val selected = currentRoute == r
        val tint = if (selected) palette.accentSecondary else palette.muted
        val digitWordId = "d_$r"
        PixelBevelPad(
          borderColor = tint,
          fillColor = if (selected) tint.copy(alpha = 0.22f) else palette.tile,
          onClick = { onTrigger("route_$r") },
          modifier =
            Modifier
              .weight(1f)
              .height(44.dp)
              .semantics { contentDescription = "control_route_$r" },
        ) {
          EmbeddedGlyphWithRuby(
            wordId = digitWordId,
            grid = glyphs[digitIdOrFallback(digitWordId)],
            wordDef = words[digitWordId],
            entry = dictionary[digitWordId],
            language = language,
            glyphColor = tint,
            glyphSize = 18.dp,
          )
        }
      }
    }
  }
}

private fun digitIdOrFallback(id: String): String = id

@Composable
private fun UnifiedOrderPadRow(
  glyphs: Map<String, GlyphGrid>,
  words: Map<String, WordDef>,
  dictionary: Map<String, DictionaryEntry>,
  language: AppLanguage,
  onWordClick: (String) -> Unit,
  onTrigger: (String) -> Unit,
) {
  val palette = KidouTheme.palette
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.panelSunken, RectangleShape)
        .border(1.5.dp, palette.border, RectangleShape)
        .padding(6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    GlyphWordTile(
      wordId = "n_button",
      grid = glyphs["n_button"],
      wordDef = words["n_button"],
      entry = dictionary["n_button"],
      language = language,
      onClick = { onWordClick("n_button") },
      compact = true,
    )
    Row(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      for (i in 1..4) {
        val digitWordId = "d_$i"
        PixelBevelPad(
          borderColor = palette.accentPrimary,
          fillColor = palette.accentPrimary.copy(alpha = 0.16f),
          onClick = { onTrigger("order_$i") },
          modifier =
            Modifier
              .weight(1f)
              .height(50.dp)
              .semantics { contentDescription = "control_order_$i" },
        ) {
          EmbeddedGlyphWithRuby(
            wordId = digitWordId,
            grid = glyphs[digitWordId],
            wordDef = words[digitWordId],
            entry = dictionary[digitWordId],
            language = language,
            glyphColor = palette.accentPrimary,
            glyphSize = 20.dp,
          )
        }
      }
    }
  }
}

@Composable
private fun UnifiedSliderModule(
  sliderValue: String,
  glyphs: Map<String, GlyphGrid>,
  words: Map<String, WordDef>,
  dictionary: Map<String, DictionaryEntry>,
  language: AppLanguage,
  onWordClick: (String) -> Unit,
  onTrigger: (String) -> Unit,
) {
  val palette = KidouTheme.palette
  val hapticClick = rememberKidouHapticClick()
  val isRight = sliderValue == "right"
  val dirWordId = if (isRight) "v_right" else "v_left"
  val tint = if (isRight) palette.statusSuccess else palette.accentSecondary
  var dragAccum by remember { mutableFloatStateOf(0f) }
  var isSliding by remember { mutableStateOf(false) }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.panelSunken, RectangleShape)
        .border(1.5.dp, palette.border, RectangleShape)
        .padding(5.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    GlyphWordTile(
      wordId = "n_slider",
      grid = glyphs["n_slider"],
      wordDef = words["n_slider"],
      entry = dictionary["n_slider"],
      language = language,
      onClick = { onWordClick("n_slider") },
      compact = true,
    )

    PixelBevelPad(
      borderColor = tint,
      fillColor = tint.copy(alpha = 0.14f),
      pressed = isSliding,
      modifier =
        Modifier
          .weight(1f)
          .height(46.dp)
          .semantics { contentDescription = "control_slider" }
          .pointerInput(sliderValue) {
            detectHorizontalDragGestures(
              onDragStart = {
                isSliding = true
                dragAccum = 0f
              },
              onHorizontalDrag = { _, dragAmount ->
                dragAccum += dragAmount
              },
              onDragCancel = {
                isSliding = false
                dragAccum = 0f
              },
              onDragEnd = {
                isSliding = false
                if (abs(dragAccum) > 24f) {
                  hapticClick()
                  onTrigger(if (dragAccum > 0f) "slider_right" else "slider_left")
                }
                dragAccum = 0f
              },
            )
          }
          .pointerInput(sliderValue) {
            detectTapGestures(
              onPress = {
                isSliding = true
                tryAwaitRelease()
                isSliding = false
              },
              onTap = {
                hapticClick()
                onTrigger(if (isRight) "slider_left" else "slider_right")
              },
            )
          },
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!isRight) {
          EmbeddedGlyphWithRuby(
            wordId = dirWordId,
            grid = glyphs[dirWordId],
            wordDef = words[dirWordId],
            entry = dictionary[dirWordId],
            language = language,
            glyphColor = tint,
            glyphSize = 20.dp,
          )
          EmbeddedGlyphWithRuby(
            wordId = "v_swipe",
            grid = glyphs["v_swipe"],
            wordDef = words["v_swipe"],
            entry = dictionary["v_swipe"],
            language = language,
            glyphColor = palette.muted,
            glyphSize = 18.dp,
          )
        } else {
          EmbeddedGlyphWithRuby(
            wordId = "v_swipe",
            grid = glyphs["v_swipe"],
            wordDef = words["v_swipe"],
            entry = dictionary["v_swipe"],
            language = language,
            glyphColor = palette.muted,
            glyphSize = 18.dp,
          )
          EmbeddedGlyphWithRuby(
            wordId = dirWordId,
            grid = glyphs[dirWordId],
            wordDef = words[dirWordId],
            entry = dictionary[dirWordId],
            language = language,
            glyphColor = tint,
            glyphSize = 20.dp,
          )
        }
      }
    }
  }
}

@Composable
private fun UnifiedNumericModule(
  control: String,
  value: Int,
  glyphs: Map<String, GlyphGrid>,
  words: Map<String, WordDef>,
  dictionary: Map<String, DictionaryEntry>,
  language: AppLanguage,
  onWordClick: (String) -> Unit,
  onTrigger: (String) -> Unit,
) {
  val palette = KidouTheme.palette
  val nounId = if (control == "dial") "n_dial" else "n_valve"
  val digits = Base5.toDigitWordIds(value)
  val decTrig = if (control == "dial") "dial_dec" else "valve_dec"
  val incTrig = if (control == "dial") "dial_inc" else "valve_inc"
  val inc5Trig = if (control == "dial") "dial_inc5" else "valve_inc5"
  val invertedFg = if (palette.isDark) palette.background else palette.tile

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.panelSunken, RectangleShape)
        .border(1.5.dp, palette.border, RectangleShape)
        .padding(5.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
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

    Row(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PixelBevelPad(
        borderColor = palette.accentSecondary,
        onClick = { onTrigger(decTrig) },
        modifier =
          Modifier
            .size(width = 40.dp, height = 44.dp)
            .semantics { contentDescription = "${control}_dec" },
      ) {
        val fg = if (LocalPadPressed.current) invertedFg else palette.accentSecondary
        Text("-", color = fg, fontFamily = DotGothic16FontFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp)
      }

      Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier
            .weight(1f)
            .height(44.dp)
            .background(palette.tile, RectangleShape)
            .border(1.5.dp, palette.accentPrimary.copy(alpha = 0.7f), RectangleShape),
      ) {
        digits.forEach { dId ->
          PixelGlyph(grid = glyphs[dId], size = 18.dp, color = palette.accentPrimary)
          Spacer(modifier = Modifier.width(2.dp))
        }
      }

      PixelBevelPad(
        borderColor = palette.accentPrimary,
        onClick = { onTrigger(incTrig) },
        modifier =
          Modifier
            .size(width = 40.dp, height = 44.dp)
            .semantics { contentDescription = "${control}_inc" },
      ) {
        val fg = if (LocalPadPressed.current) invertedFg else palette.accentPrimary
        Text("+", color = fg, fontFamily = DotGothic16FontFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp)
      }

      PixelBevelPad(
        borderColor = palette.statusSuccess,
        onClick = { onTrigger(inc5Trig) },
        modifier =
          Modifier
            .size(width = 46.dp, height = 44.dp)
            .semantics { contentDescription = "${control}_inc5" },
      ) {
        val fg = if (LocalPadPressed.current) invertedFg else palette.statusSuccess
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("+", color = fg, fontFamily = DotGothic16FontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp)
          PixelGlyph(grid = glyphs["d_1"], size = 9.dp, color = fg)
          PixelGlyph(grid = glyphs["d_0"], size = 9.dp, color = fg)
        }
      }
    }
  }
}

@Composable
private fun UnifiedPushButtonRow(
  glyphs: Map<String, GlyphGrid>,
  words: Map<String, WordDef>,
  dictionary: Map<String, DictionaryEntry>,
  language: AppLanguage,
  onWordClick: (String) -> Unit,
  onPush: () -> Unit,
) {
  val palette = KidouTheme.palette
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.panelSunken, RectangleShape)
        .border(1.5.dp, palette.border, RectangleShape)
        .padding(6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    GlyphWordTile(
      wordId = "n_button",
      grid = glyphs["n_button"],
      wordDef = words["n_button"],
      entry = dictionary["n_button"],
      language = language,
      onClick = { onWordClick("n_button") },
      compact = true,
    )

    PixelBevelPad(
      borderColor = palette.accentPrimary,
      fillColor = palette.accentPrimary.copy(alpha = 0.2f),
      onClick = onPush,
      modifier =
        Modifier
          .weight(1f)
          .height(50.dp)
          .semantics { contentDescription = "control_button" },
    ) {
      EmbeddedGlyphWithRuby(
        wordId = "v_push",
        grid = glyphs["v_push"],
        wordDef = words["v_push"],
        entry = dictionary["v_push"],
        language = language,
        glyphColor = palette.accentPrimary,
        glyphSize = 22.dp,
      )
    }
  }
}

@Composable
private fun UnifiedHoldButtonRow(
  glyphs: Map<String, GlyphGrid>,
  words: Map<String, WordDef>,
  dictionary: Map<String, DictionaryEntry>,
  language: AppLanguage,
  onWordClick: (String) -> Unit,
  onTrigger: (String) -> Unit,
) {
  val palette = KidouTheme.palette
  var holding by remember { mutableStateOf(false) }
  var holdProgress by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(holding) {
    if (!holding) {
      holdProgress = 0f
      return@LaunchedEffect
    }
    val steps = 17
    for (i in 1..steps) {
      delay(50L)
      holdProgress = i / steps.toFloat()
    }
    holding = false
    holdProgress = 0f
    onTrigger("button_hold")
  }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.panelSunken, RectangleShape)
        .border(1.5.dp, palette.border, RectangleShape)
        .padding(6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    GlyphWordTile(
      wordId = "n_button",
      grid = glyphs["n_button"],
      wordDef = words["n_button"],
      entry = dictionary["n_button"],
      language = language,
      onClick = { onWordClick("n_button") },
      compact = true,
    )

    PixelBevelPad(
      borderColor = palette.accentPrimary,
      fillColor = palette.accentPrimary.copy(alpha = 0.16f),
      pressed = holding,
      modifier =
        Modifier
          .weight(1f)
          .height(50.dp)
          .semantics { contentDescription = "control_hold_button" }
          .pointerInput(Unit) {
            detectTapGestures(
              onPress = {
                holding = true
                val released = tryAwaitRelease()
                if (holding) {
                  holding = false
                  if (released) {
                    onTrigger("button_push")
                  }
                }
              },
            )
          },
    ) {
      if (holdProgress > 0f) {
        Box(
          modifier =
            Modifier
              .align(Alignment.CenterStart)
              .fillMaxHeight()
              .fillMaxWidth(holdProgress)
              .background(palette.statusSuccess.copy(alpha = 0.35f)),
        )
      }
      EmbeddedGlyphWithRuby(
        wordId = "v_hold",
        grid = glyphs["v_hold"],
        wordDef = words["v_hold"],
        entry = dictionary["v_hold"],
        language = language,
        glyphColor = if (holdProgress > 0.5f) palette.statusSuccess else palette.accentPrimary,
        glyphSize = 22.dp,
      )
    }
  }
}

@Composable
private fun UnifiedDualButtonRow(
  glyphs: Map<String, GlyphGrid>,
  words: Map<String, WordDef>,
  dictionary: Map<String, DictionaryEntry>,
  language: AppLanguage,
  onWordClick: (String) -> Unit,
  onTrigger: (String) -> Unit,
) {
  val palette = KidouTheme.palette
  var pad1Down by remember { mutableStateOf(false) }
  var pad2Down by remember { mutableStateOf(false) }
  var pad1TapTime by remember { mutableLongStateOf(0L) }
  var pad2TapTime by remember { mutableLongStateOf(0L) }
  var chordFired by remember { mutableStateOf(false) }

  fun checkChord() {
    val multiDown = pad1Down && pad2Down
    val quickChord =
      pad1TapTime > 0L && pad2TapTime > 0L && abs(pad1TapTime - pad2TapTime) <= 650L
    if (!chordFired && (multiDown || quickChord)) {
      chordFired = true
      pad1TapTime = 0L
      pad2TapTime = 0L
      onTrigger("dual_push")
    }
  }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.panelSunken, RectangleShape)
        .border(1.5.dp, palette.border, RectangleShape)
        .padding(6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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
        wordId = "d_2",
        grid = glyphs["d_2"],
        wordDef = words["d_2"],
        entry = dictionary["d_2"],
        language = language,
        onClick = { onWordClick("d_2") },
        compact = true,
      )
    }

    Row(
      modifier = Modifier.weight(1f),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      PixelBevelPad(
        borderColor = if (pad1Down) palette.statusSuccess else palette.accentPrimary,
        fillColor =
          if (pad1Down) palette.statusSuccess.copy(alpha = 0.25f)
          else palette.accentPrimary.copy(alpha = 0.16f),
        pressed = pad1Down,
        modifier =
          Modifier
            .weight(1f)
            .height(50.dp)
            .semantics { contentDescription = "control_dual_1" }
            .pointerInput(Unit) {
              detectTapGestures(
                onPress = {
                  chordFired = false
                  pad1Down = true
                  pad1TapTime = System.currentTimeMillis()
                  checkChord()
                  tryAwaitRelease()
                  pad1Down = false
                },
              )
            },
      ) {
        EmbeddedGlyphWithRuby(
          wordId = "v_push",
          grid = glyphs["v_push"],
          wordDef = words["v_push"],
          entry = dictionary["v_push"],
          language = language,
          glyphColor = palette.accentPrimary,
          glyphSize = 20.dp,
        )
      }

      PixelBevelPad(
        borderColor = if (pad2Down) palette.statusSuccess else palette.accentPrimary,
        fillColor =
          if (pad2Down) palette.statusSuccess.copy(alpha = 0.25f)
          else palette.accentPrimary.copy(alpha = 0.16f),
        pressed = pad2Down,
        modifier =
          Modifier
            .weight(1f)
            .height(50.dp)
            .semantics { contentDescription = "control_dual_2" }
            .pointerInput(Unit) {
              detectTapGestures(
                onPress = {
                  chordFired = false
                  pad2Down = true
                  pad2TapTime = System.currentTimeMillis()
                  checkChord()
                  tryAwaitRelease()
                  pad2Down = false
                },
              )
            },
      ) {
        EmbeddedGlyphWithRuby(
          wordId = "v_push",
          grid = glyphs["v_push"],
          wordDef = words["v_push"],
          entry = dictionary["v_push"],
          language = language,
          glyphColor = palette.accentPrimary,
          glyphSize = 20.dp,
        )
      }
    }
  }
}

@Composable
private fun NextLevelActionRow(
  nextLabel: String,
  glyphs: Map<String, GlyphGrid>,
  words: Map<String, WordDef>,
  dictionary: Map<String, DictionaryEntry>,
  language: AppLanguage,
  onWordClick: (String) -> Unit,
  onNext: () -> Unit,
) {
  val palette = KidouTheme.palette
  val invertedFg = if (palette.isDark) palette.background else palette.tile
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(palette.panelSunken, RectangleShape)
        .border(1.5.dp, palette.statusSuccess, RectangleShape)
        .padding(6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    GlyphWordTile(
      wordId = "m_next",
      grid = glyphs["m_next"],
      wordDef = words["m_next"],
      entry = dictionary["m_next"],
      language = language,
      onClick = { onWordClick("m_next") },
      compact = true,
      glyphColor = palette.statusSuccess,
    )

    PixelBevelPad(
      borderColor = palette.statusSuccess,
      fillColor = palette.statusSuccess.copy(alpha = 0.22f),
      onClick = onNext,
      modifier =
        Modifier
          .weight(1f)
          .height(50.dp)
          .semantics { contentDescription = "control_next" },
    ) {
      val fg = if (LocalPadPressed.current) invertedFg else palette.statusSuccess
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PixelGlyph(grid = glyphs["m_next"], size = 20.dp, color = fg)
        Text(
          text = nextLabel,
          color = fg,
          fontFamily = DotGothic16FontFamily,
          fontWeight = FontWeight.Normal,
          fontSize = 15.sp,
          letterSpacing = 1.5.sp,
        )
      }
    }
  }
}
