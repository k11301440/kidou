package com.kidou.game.engine

import com.kidou.game.model.AppLanguage
import com.kidou.game.model.BoardDef
import com.kidou.game.model.ConditionRequirement
import com.kidou.game.model.DictionaryEntry
import com.kidou.game.model.EntrySource
import com.kidou.game.model.LampState
import com.kidou.game.model.LevelDef
import com.kidou.game.model.LevelRuntimeState
import com.kidou.game.model.LoadedGameData
import com.kidou.game.model.LogicClause
import com.kidou.game.model.ModuleCheck
import com.kidou.game.model.OperationLogEntry
import com.kidou.game.model.PuzzleType
import com.kidou.game.model.SoundEffect
import com.kidou.game.model.TranslationStats
import com.kidou.game.model.WordColorTag

data class LevelStepResult(
  val newState: LevelRuntimeState,
  val logEntry: OperationLogEntry,
  val sound: SoundEffect,
  val newlyVisibleWordIds: Set<String>,
)

object LevelEngine {
  const val MAX_NUMERIC_VALUE = 25

  fun levelKey(boardId: Int, levelNumber: Int): String = "$boardId-$levelNumber"

  fun createInitialState(boardId: Int, levelDef: LevelDef): LevelRuntimeState {
    val init = levelDef.initialState
    return LevelRuntimeState(
      boardId = boardId,
      levelNumber = levelDef.levelNumber,
      lever = init.lever,
      key = init.key,
      conduit = init.conduit,
      lock = init.lock,
      dial = init.dial,
      valve = init.valve,
      swA = init.swA,
      swB = init.swB,
      swC = init.swC,
      slider = init.slider,
      buttonPresses = 0,
      dualPresses = 0,
      seqStep = 0,
      seqSubProgress = 0,
      isCleared = false,
      lamp = LampState.fromWire(init.lamp),
      primaryMessage = init.primaryMessage,
      secondaryMessage = init.secondaryMessage,
    )
  }

  fun step(
    levelDef: LevelDef,
    currentState: LevelRuntimeState,
    trigger: String,
    timestampMs: Long = System.currentTimeMillis(),
    logId: Long = timestampMs,
  ): LevelStepResult {
    return when (levelDef.puzzleType) {
      PuzzleType.CONDITION -> stepCondition(levelDef, currentState, trigger, timestampMs, logId)
      PuzzleType.SEQUENCE -> stepSequence(levelDef, currentState, trigger, timestampMs, logId)
    }
  }

  private fun stepCondition(
    levelDef: LevelDef,
    state: LevelRuntimeState,
    trigger: String,
    timestampMs: Long,
    logId: Long,
  ): LevelStepResult {
    val actionSlots = triggerToSlots(trigger)
    val isPrimarySubmit = trigger in setOf("button_push", "button_hold", "dual_push")

    if (!isPrimarySubmit) {
      val updated = applyModuleTrigger(state, trigger).copy(buttonPresses = 0, dualPresses = 0)
      val (primary, secondary, sound) = moduleReaction(updated, trigger)
      val nextState =
        updated.copy(
          lamp = if (state.isCleared) LampState.GREEN else LampState.AMBER,
          primaryMessage = primary,
          secondaryMessage = secondary,
        )
      return makeResult(levelDef, nextState, trigger, actionSlots, sound, timestampMs, logId)
    }

    val req = levelDef.requirements ?: ConditionRequirement()
    val mismatch = findFirstMismatch(state, req)
    if (mismatch != null) {
      val nextState =
        state.copy(
          buttonPresses = 0,
          dualPresses = 0,
          lamp = LampState.RED,
          primaryMessage = mismatch.first,
          secondaryMessage = mismatch.second,
        )
      return makeResult(levelDef, nextState, trigger, actionSlots, SoundEffect.WARN, timestampMs, logId)
    }

    // Hold requirement
    if (req.requireHold == true) {
      if (trigger != "button_hold") {
        val primary = listOf(listOf("n_button"), listOf("v_hold", "m_not"))
        val secondary = listOf(listOf("n_machine"), listOf("v_stop"))
        val nextState =
          state.copy(
            buttonPresses = 0,
            dualPresses = 0,
            lamp = LampState.RED,
            primaryMessage = primary,
            secondaryMessage = secondary,
          )
        return makeResult(levelDef, nextState, trigger, actionSlots, SoundEffect.WARN, timestampMs, logId)
      }
      val nextState =
        state.copy(
          isCleared = true,
          lamp = LampState.GREEN,
          primaryMessage = levelDef.completionPrimary,
          secondaryMessage = levelDef.completionSecondary,
        )
      return makeResult(levelDef, nextState, trigger, actionSlots, SoundEffect.PHASE_CLEAR, timestampMs, logId)
    }

    // Dual simultaneous press requirement
    if (req.dualPressesEq != null && req.dualPressesEq > 0) {
      val nextDual = state.dualPresses + 1
      if (nextDual < req.dualPressesEq) {
        val primary = listOf(listOf("n_button", "d_2"), listOf("m_together"), Base5.toDigitWordIds(nextDual) + "m_times")
        val secondary = listOf(listOf("n_lamp"), listOf("v_activate"))
        val nextState =
          state.copy(
            dualPresses = nextDual,
            lamp = LampState.AMBER,
            primaryMessage = primary,
            secondaryMessage = secondary,
          )
        return makeResult(levelDef, nextState, trigger, actionSlots, SoundEffect.CLICK, timestampMs, logId)
      }
      val nextState =
        state.copy(
          dualPresses = nextDual,
          isCleared = true,
          lamp = LampState.GREEN,
          primaryMessage = levelDef.completionPrimary,
          secondaryMessage = levelDef.completionSecondary,
        )
      return makeResult(levelDef, nextState, trigger, actionSlots, SoundEffect.PHASE_CLEAR, timestampMs, logId)
    }

    // Standard push button requirement
    val targetPresses = (req.buttonPressesEq ?: 1).coerceAtLeast(1)
    val nextPresses = state.buttonPresses + 1
    if (nextPresses < targetPresses) {
      val primary = listOf(listOf("n_button"), listOf("v_push"), Base5.toDigitWordIds(nextPresses) + "m_times")
      val secondary = listOf(listOf("n_lamp"), listOf("v_activate"))
      val nextState =
        state.copy(
          buttonPresses = nextPresses,
          lamp = LampState.AMBER,
          primaryMessage = primary,
          secondaryMessage = secondary,
        )
      return makeResult(levelDef, nextState, trigger, actionSlots, SoundEffect.CLICK, timestampMs, logId)
    }

    val nextState =
      state.copy(
        buttonPresses = nextPresses,
        isCleared = true,
        lamp = LampState.GREEN,
        primaryMessage = levelDef.completionPrimary,
        secondaryMessage = levelDef.completionSecondary,
      )
    return makeResult(levelDef, nextState, trigger, actionSlots, SoundEffect.PHASE_CLEAR, timestampMs, logId)
  }

  private fun stepSequence(
    levelDef: LevelDef,
    state: LevelRuntimeState,
    trigger: String,
    timestampMs: Long,
    logId: Long,
  ): LevelStepResult {
    val steps = levelDef.sequenceSteps
    val actionSlots = triggerToSlots(trigger)

    if (state.isCleared || state.seqStep >= steps.size) {
      val updated = applyModuleTrigger(state, trigger)
      val (primary, secondary, sound) = moduleReaction(updated, trigger)
      val nextState = updated.copy(primaryMessage = primary, secondaryMessage = secondary)
      return makeResult(levelDef, nextState, trigger, actionSlots, sound, timestampMs, logId)
    }

    val expectedStep = steps[state.seqStep]
    if (trigger != expectedStep.trigger) {
      val reset = createInitialState(state.boardId, levelDef)
      val primary = expectedStep.hintPrimary
      val secondary = listOf(listOf("n_machine"), listOf("v_stop"), listOf("adj_danger"))
      val nextState =
        reset.copy(
          lamp = LampState.RED,
          primaryMessage = primary,
          secondaryMessage = secondary,
        )
      return makeResult(levelDef, nextState, trigger, actionSlots, SoundEffect.WARN, timestampMs, logId)
    }

    val updatedHardware = applyModuleTrigger(state, trigger)
    val nextSub = state.seqSubProgress + 1
    if (nextSub < expectedStep.repeatCount) {
      val primary = actionSlots + listOf(Base5.toDigitWordIds(nextSub) + "m_times")
      val nextState =
        updatedHardware.copy(
          seqSubProgress = nextSub,
          lamp = LampState.AMBER,
          primaryMessage = primary,
          secondaryMessage = emptyList(),
        )
      return makeResult(levelDef, nextState, trigger, actionSlots, SoundEffect.RATCHET, timestampMs, logId)
    }

    val nextStepIndex = state.seqStep + 1
    if (nextStepIndex >= steps.size) {
      val nextState =
        updatedHardware.copy(
          seqStep = nextStepIndex,
          seqSubProgress = 0,
          isCleared = true,
          lamp = LampState.GREEN,
          primaryMessage = levelDef.completionPrimary,
          secondaryMessage = levelDef.completionSecondary,
        )
      return makeResult(levelDef, nextState, trigger, actionSlots, SoundEffect.PHASE_CLEAR, timestampMs, logId)
    }

    val stepCompleteSecondary =
      listOf(Base5.toDigitWordIds(state.seqStep + 1) + "m_ord", listOf("adj_complete"))
    val nextState =
      updatedHardware.copy(
        seqStep = nextStepIndex,
        seqSubProgress = 0,
        lamp = LampState.AMBER,
        primaryMessage = actionSlots,
        secondaryMessage = stepCompleteSecondary,
      )
    return makeResult(levelDef, nextState, trigger, actionSlots, SoundEffect.CHIME, timestampMs, logId)
  }

  private fun makeResult(
    levelDef: LevelDef,
    nextState: LevelRuntimeState,
    trigger: String,
    actionSlots: List<List<String>>,
    sound: SoundEffect,
    timestampMs: Long,
    logId: Long,
  ): LevelStepResult {
    val logEntry =
      OperationLogEntry(
        id = logId,
        timestampMs = timestampMs,
        boardId = nextState.boardId,
        levelNumber = nextState.levelNumber,
        trigger = trigger,
        actionLabelSlots = actionSlots,
        resultPrimarySlots = nextState.primaryMessage,
        resultSecondarySlots = nextState.secondaryMessage,
        lamp = nextState.lamp,
      )
    return LevelStepResult(
      newState = nextState,
      logEntry = logEntry,
      sound = sound,
      newlyVisibleWordIds = collectLevelScreenWordIds(levelDef, nextState).toSet(),
    )
  }

  private fun applyModuleTrigger(state: LevelRuntimeState, trigger: String): LevelRuntimeState =
    when (trigger) {
      "lever_pull" -> state.copy(lever = "down")
      "lever_push" -> state.copy(lever = "up")
      "key_insert" -> state.copy(key = "in")
      "key_remove" -> state.copy(key = "out")
      "conduit_connect" -> state.copy(conduit = "connected")
      "conduit_cut" -> state.copy(conduit = "cut")
      "lock_lock" -> state.copy(lock = "locked")
      "lock_unlock" -> state.copy(lock = "unlocked")
      "sw_a_toggle" -> state.copy(swA = !state.swA)
      "sw_b_toggle" -> state.copy(swB = !state.swB)
      "sw_c_toggle" -> state.copy(swC = !state.swC)
      "slider_right" -> state.copy(slider = "right")
      "slider_left" -> state.copy(slider = "left")
      "dial_inc" -> state.copy(dial = (state.dial + 1).coerceAtMost(MAX_NUMERIC_VALUE))
      "dial_dec" -> state.copy(dial = (state.dial - 1).coerceAtLeast(0))
      "dial_inc5" -> state.copy(dial = (state.dial + 5).coerceAtMost(MAX_NUMERIC_VALUE))
      "valve_inc" -> state.copy(valve = (state.valve + 1).coerceAtMost(MAX_NUMERIC_VALUE))
      "valve_dec" -> state.copy(valve = (state.valve - 1).coerceAtLeast(0))
      "valve_inc5" -> state.copy(valve = (state.valve + 5).coerceAtMost(MAX_NUMERIC_VALUE))
      else -> state
    }

  private fun moduleReaction(
    updated: LevelRuntimeState,
    trigger: String,
  ): Triple<List<List<String>>, List<List<String>>, SoundEffect> {
    return when (trigger) {
      "lever_pull" -> Triple(listOf(listOf("n_lever"), listOf("v_down")), emptyList(), SoundEffect.CLUNK)
      "lever_push" -> Triple(listOf(listOf("n_lever"), listOf("v_up")), emptyList(), SoundEffect.CLUNK)
      "key_insert" -> Triple(listOf(listOf("n_key"), listOf("v_insert")), emptyList(), SoundEffect.CHIME)
      "key_remove" -> Triple(listOf(listOf("n_key"), listOf("v_remove")), emptyList(), SoundEffect.CLUNK)
      "conduit_connect" ->
        Triple(listOf(listOf("n_conduit"), listOf("v_connect")), emptyList(), SoundEffect.CHIME)
      "conduit_cut" ->
        Triple(listOf(listOf("n_conduit"), listOf("v_cut")), emptyList(), SoundEffect.CLUNK)
      "lock_lock" -> Triple(listOf(listOf("n_lock"), listOf("v_lock")), emptyList(), SoundEffect.CLUNK)
      "lock_unlock" ->
        Triple(listOf(listOf("n_lock"), listOf("v_unlock")), emptyList(), SoundEffect.CHIME)
      "sw_a_toggle" ->
        Triple(
          listOf(listOf("n_switch", "d_1"), listOf(if (updated.swA) "v_activate" else "v_stop")),
          emptyList(),
          SoundEffect.CLICK,
        )
      "sw_b_toggle" ->
        Triple(
          listOf(listOf("n_switch", "d_2"), listOf(if (updated.swB) "v_activate" else "v_stop")),
          emptyList(),
          SoundEffect.CLICK,
        )
      "sw_c_toggle" ->
        Triple(
          listOf(listOf("n_switch", "d_3"), listOf(if (updated.swC) "v_activate" else "v_stop")),
          emptyList(),
          SoundEffect.CLICK,
        )
      "slider_right" ->
        Triple(listOf(listOf("n_slider"), listOf("v_right")), emptyList(), SoundEffect.RATCHET)
      "slider_left" ->
        Triple(listOf(listOf("n_slider"), listOf("v_left")), emptyList(), SoundEffect.RATCHET)
      "dial_inc",
      "dial_dec",
      "dial_inc5" ->
        Triple(
          listOf(listOf("n_dial"), listOf("v_turn"), Base5.toDigitWordIds(updated.dial)),
          emptyList(),
          SoundEffect.RATCHET,
        )
      "valve_inc",
      "valve_dec",
      "valve_inc5" ->
        Triple(
          listOf(listOf("n_valve"), Base5.toDigitWordIds(updated.valve)),
          emptyList(),
          SoundEffect.RATCHET,
        )
      else -> Triple(updated.primaryMessage, updated.secondaryMessage, SoundEffect.CLICK)
    }
  }

  private fun findFirstMismatch(
    state: LevelRuntimeState,
    req: ConditionRequirement,
  ): Pair<List<List<String>>, List<List<String>>>? {
    req.logicClauses.forEach { clause ->
      if (!evaluateLogicClause(state, clause)) {
        val p = clause.hintPrimary.ifEmpty { listOf(listOf("n_switch"), listOf("v_match", "m_not")) }
        val s = clause.hintSecondary.ifEmpty { listOf(listOf("n_machine"), listOf("v_stop")) }
        return p to s
      }
    }
    if (req.swAEq != null && state.swA != req.swAEq) {
      return listOf(listOf("n_switch", "d_1"), listOf(if (req.swAEq) "v_activate" else "v_stop", "m_not")) to
        listOf(listOf("n_machine"), listOf("v_stop"))
    }
    if (req.swBEq != null && state.swB != req.swBEq) {
      return listOf(listOf("n_switch", "d_2"), listOf(if (req.swBEq) "v_activate" else "v_stop", "m_not")) to
        listOf(listOf("n_machine"), listOf("v_stop"))
    }
    if (req.swCEq != null && state.swC != req.swCEq) {
      return listOf(listOf("n_switch", "d_3"), listOf(if (req.swCEq) "v_activate" else "v_stop", "m_not")) to
        listOf(listOf("n_machine"), listOf("v_stop"))
    }
    if (req.conduitEq != null && state.conduit != req.conduitEq) {
      val verb = if (req.conduitEq == "connected") "v_connect" else "v_cut"
      return listOf(listOf("n_conduit"), listOf(verb, "m_not")) to
        listOf(listOf("n_machine"), listOf("v_stop"), listOf("adj_danger"))
    }
    if (req.lockEq != null && state.lock != req.lockEq) {
      val verb = if (req.lockEq == "locked") "v_lock" else "v_unlock"
      return listOf(listOf("n_lock"), listOf(verb, "m_not")) to
        listOf(listOf("n_machine"), listOf("v_stop"))
    }
    if (req.keyEq != null && state.key != req.keyEq) {
      val verb = if (req.keyEq == "in") "v_insert" else "v_remove"
      return listOf(listOf("n_key"), listOf(verb, "m_not")) to
        listOf(listOf("n_machine"), listOf("v_stop"))
    }
    if (req.sliderEq != null && state.slider != req.sliderEq) {
      val dir = if (req.sliderEq == "right") "v_right" else "v_left"
      return listOf(listOf("n_slider"), listOf(dir, "m_not")) to
        listOf(listOf("n_machine"), listOf("v_stop"))
    }
    if (req.dialEq != null && state.dial != req.dialEq) {
      val dirVerb = if (state.dial < req.dialEq) "v_increase" else "v_decrease"
      return listOf(listOf("n_dial"), listOf("v_match", "m_not")) to
        listOf(listOf("n_dial"), listOf(dirVerb), Base5.toDigitWordIds(state.dial))
    }
    if (req.valveEq != null && state.valve != req.valveEq) {
      val dirVerb = if (state.valve < req.valveEq) "v_increase" else "v_decrease"
      return listOf(listOf("n_valve"), listOf("v_match", "m_not")) to
        listOf(listOf("n_valve"), listOf(dirVerb), Base5.toDigitWordIds(state.valve))
    }
    if (req.leverEq != null && state.lever != req.leverEq) {
      val verb = if (req.leverEq == "up") "v_up" else "v_down"
      return listOf(listOf("n_lever"), listOf(verb, "m_not")) to
        listOf(listOf("n_machine"), listOf("v_stop"))
    }
    return null
  }

  private fun evaluateLogicClause(state: LevelRuntimeState, clause: LogicClause): Boolean {
    if (clause.checks.isEmpty()) return true
    return when (clause.operator.uppercase()) {
      "OR" -> clause.checks.any { moduleMatches(state, it) }
      "XOR" -> clause.checks.count { moduleMatches(state, it) } % 2 == 1
      "NOT" -> clause.checks.none { moduleMatches(state, it) }
      else -> clause.checks.all { moduleMatches(state, it) }
    }
  }

  private fun moduleMatches(state: LevelRuntimeState, check: ModuleCheck): Boolean {
    val actual =
      when (check.module) {
        "sw_a" -> state.swA.toString()
        "sw_b" -> state.swB.toString()
        "sw_c" -> state.swC.toString()
        "lever" -> state.lever
        "key" -> state.key
        "conduit" -> state.conduit
        "lock" -> state.lock
        "slider" -> state.slider
        else -> ""
      }
    return actual == check.expected
  }

  fun triggerToSlots(trigger: String): List<List<String>> =
    when (trigger) {
      "button_push" -> listOf(listOf("n_button"), listOf("v_push"))
      "button_hold" -> listOf(listOf("n_button"), listOf("v_hold"))
      "dual_push" -> listOf(listOf("n_button", "d_2"), listOf("m_together"), listOf("v_push"))
      "lever_pull" -> listOf(listOf("n_lever"), listOf("v_pull"))
      "lever_push" -> listOf(listOf("n_lever"), listOf("v_push"))
      "key_insert" -> listOf(listOf("n_key"), listOf("v_insert"))
      "key_remove" -> listOf(listOf("n_key"), listOf("v_remove"))
      "conduit_connect" -> listOf(listOf("n_conduit"), listOf("v_connect"))
      "conduit_cut" -> listOf(listOf("n_conduit"), listOf("v_cut"))
      "lock_lock" -> listOf(listOf("n_lock"), listOf("v_lock"))
      "lock_unlock" -> listOf(listOf("n_lock"), listOf("v_unlock"))
      "sw_a_toggle" -> listOf(listOf("n_switch", "d_1"))
      "sw_b_toggle" -> listOf(listOf("n_switch", "d_2"))
      "sw_c_toggle" -> listOf(listOf("n_switch", "d_3"))
      "slider_right" -> listOf(listOf("n_slider"), listOf("v_right"))
      "slider_left" -> listOf(listOf("n_slider"), listOf("v_left"))
      "dial_inc", "dial_inc5" -> listOf(listOf("n_dial"), listOf("v_increase"))
      "dial_dec" -> listOf(listOf("n_dial"), listOf("v_decrease"))
      "valve_inc", "valve_inc5" -> listOf(listOf("n_valve"), listOf("v_increase"))
      "valve_dec" -> listOf(listOf("n_valve"), listOf("v_decrease"))
      else -> emptyList()
    }

  fun collectLevelScreenWordIds(
    levelDef: LevelDef,
    state: LevelRuntimeState,
  ): List<String> {
    val ids = LinkedHashSet<String>()

    fun addSlots(slots: List<List<String>>) {
      slots.forEach { slot -> slot.forEach { ids.add(it) } }
    }

    ids.add("n_machine")
    ids.addAll(Base5.toDigitWordIds(levelDef.levelNumber))
    addSlots(state.primaryMessage)
    addSlots(state.secondaryMessage)
    levelDef.plates.forEach { addSlots(it) }
    if (state.isCleared) {
      ids.add("m_next")
    }

    levelDef.unlockedControls.forEach { control ->
      when (control) {
        "button" -> {
          ids.add("n_button")
          ids.add("v_push")
        }
        "hold_button" -> {
          ids.add("n_button")
          ids.add("v_hold")
        }
        "dual_button" -> {
          ids.add("n_button")
          ids.add("d_2")
          ids.add("v_push")
        }
        "lever" -> {
          ids.add("n_lever")
          ids.add(if (state.lever == "up") "v_up" else "v_down")
        }
        "key" -> {
          ids.add("n_key")
          ids.add(if (state.key == "in") "v_insert" else "v_remove")
        }
        "conduit" -> {
          ids.add("n_conduit")
          ids.add(if (state.conduit == "connected") "v_connect" else "v_cut")
        }
        "lock" -> {
          ids.add("n_lock")
          ids.add(if (state.lock == "locked") "v_lock" else "v_unlock")
        }
        "sw_a" -> {
          ids.add("n_switch")
          ids.add("d_1")
          ids.add(if (state.swA) "v_activate" else "v_stop")
        }
        "sw_b" -> {
          ids.add("n_switch")
          ids.add("d_2")
          ids.add(if (state.swB) "v_activate" else "v_stop")
        }
        "sw_c" -> {
          ids.add("n_switch")
          ids.add("d_3")
          ids.add(if (state.swC) "v_activate" else "v_stop")
        }
        "slider" -> {
          ids.add("n_slider")
          ids.add("v_swipe")
          ids.add(if (state.slider == "right") "v_right" else "v_left")
        }
        "dial" -> {
          ids.add("n_dial")
          ids.addAll(Base5.toDigitWordIds(state.dial))
        }
        "valve" -> {
          ids.add("n_valve")
          ids.addAll(Base5.toDigitWordIds(state.valve))
        }
      }
    }

    return ids.toList()
  }

  fun collectBoardScreenWordIds(boardDef: BoardDef): List<String> {
    val ids = LinkedHashSet<String>()
    boardDef.titleSlots.flatten().forEach { ids.add(it) }
    for (n in 1..boardDef.totalSlots) {
      ids.addAll(Base5.toDigitWordIds(n))
    }
    ids.add("adj_complete")
    return ids.toList()
  }

  fun isBoardUnlocked(boardDef: BoardDef, clearedLevelKeys: Set<String>): Boolean {
    val reqBoard = boardDef.requiredBoardId ?: return true
    return countBoardClears(reqBoard, clearedLevelKeys) >= boardDef.requiredClears
  }

  fun countBoardClears(boardId: Int, clearedLevelKeys: Set<String>): Int {
    val prefix = "$boardId-"
    return clearedLevelKeys.count { it.startsWith(prefix) }
  }

  fun buildDefaultDictionary(
    data: LoadedGameData,
    language: AppLanguage,
    existing: Map<String, DictionaryEntry> = emptyMap(),
  ): Map<String, DictionaryEntry> {
    val out = existing.toMutableMap()
    data.words.values.filter { it.isDefaultSet }.forEach { word ->
      if (!out.containsKey(word.id)) {
        out[word.id] =
          DictionaryEntry(
            wordId = word.id,
            translation = word.meaning(language),
            colorTag = defaultTagForPos(word.pos),
            memo = "",
            source = EntrySource.DEFAULT_SET,
          )
      }
    }
    return out
  }

  fun computeTranslationStats(
    data: LoadedGameData,
    discoveredWordIds: Set<String>,
    dictionary: Map<String, DictionaryEntry>,
    language: AppLanguage,
  ): TranslationStats {
    val countableDiscovered = discoveredWordIds.filter { it !in data.defaultSetWordIds }
    val translated =
      countableDiscovered.count { wordId ->
        val entry = dictionary[wordId] ?: return@count false
        val wordDef = data.words[wordId]
        entry.resolvedTranslation(wordDef, language).isNotBlank()
      }
    val total = countableDiscovered.size
    val percent = if (total == 0) 0 else (translated * 100) / total
    return TranslationStats(
      translatedCount = translated,
      totalCount = total,
      percent = percent,
    )
  }

  fun defaultTagForPos(pos: String): WordColorTag =
    when (pos) {
      "noun" -> WordColorTag.OBJECT
      "verb" -> WordColorTag.ACTION
      "digit", "modifier" -> WordColorTag.MODIFIER
      else -> WordColorTag.HYPOTHESIS
    }
}
