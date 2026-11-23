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
      buttonPresses = 0,
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

    if (trigger != "button_push") {
      val updated = applyModuleTrigger(state, trigger).copy(buttonPresses = 0)
      val (primary, secondary, sound) = moduleReaction(updated, trigger)
      val nextState =
        updated.copy(
          lamp = if (state.isCleared) LampState.GREEN else LampState.AMBER,
          primaryMessage = primary,
          secondaryMessage = secondary,
        )
      val logEntry =
        OperationLogEntry(
          id = logId,
          timestampMs = timestampMs,
          boardId = state.boardId,
          levelNumber = state.levelNumber,
          trigger = trigger,
          actionLabelSlots = actionSlots,
          resultPrimarySlots = primary,
          resultSecondarySlots = secondary,
          lamp = nextState.lamp,
        )
      return LevelStepResult(
        newState = nextState,
        logEntry = logEntry,
        sound = sound,
        newlyVisibleWordIds = collectLevelScreenWordIds(levelDef, nextState).toSet(),
      )
    }

    // Evaluate condition requirements on button push
    val req = levelDef.requirements ?: ConditionRequirement()
    val mismatch = findFirstMismatch(state, req)
    if (mismatch != null) {
      val nextState =
        state.copy(
          buttonPresses = 0,
          lamp = LampState.RED,
          primaryMessage = mismatch.first,
          secondaryMessage = mismatch.second,
        )
      val logEntry =
        OperationLogEntry(
          id = logId,
          timestampMs = timestampMs,
          boardId = state.boardId,
          levelNumber = state.levelNumber,
          trigger = trigger,
          actionLabelSlots = actionSlots,
          resultPrimarySlots = mismatch.first,
          resultSecondarySlots = mismatch.second,
          lamp = LampState.RED,
        )
      return LevelStepResult(
        newState = nextState,
        logEntry = logEntry,
        sound = SoundEffect.WARN,
        newlyVisibleWordIds = collectLevelScreenWordIds(levelDef, nextState).toSet(),
      )
    }

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
      val logEntry =
        OperationLogEntry(
          id = logId,
          timestampMs = timestampMs,
          boardId = state.boardId,
          levelNumber = state.levelNumber,
          trigger = trigger,
          actionLabelSlots = actionSlots,
          resultPrimarySlots = primary,
          resultSecondarySlots = secondary,
          lamp = LampState.AMBER,
        )
      return LevelStepResult(
        newState = nextState,
        logEntry = logEntry,
        sound = SoundEffect.CLICK,
        newlyVisibleWordIds = collectLevelScreenWordIds(levelDef, nextState).toSet(),
      )
    }

    val nextState =
      state.copy(
        buttonPresses = nextPresses,
        isCleared = true,
        lamp = LampState.GREEN,
        primaryMessage = levelDef.completionPrimary,
        secondaryMessage = levelDef.completionSecondary,
      )
    val logEntry =
      OperationLogEntry(
        id = logId,
        timestampMs = timestampMs,
        boardId = state.boardId,
        levelNumber = state.levelNumber,
        trigger = trigger,
        actionLabelSlots = actionSlots,
        resultPrimarySlots = nextState.primaryMessage,
        resultSecondarySlots = nextState.secondaryMessage,
        lamp = LampState.GREEN,
      )
    return LevelStepResult(
      newState = nextState,
      logEntry = logEntry,
      sound = SoundEffect.PHASE_CLEAR,
      newlyVisibleWordIds = collectLevelScreenWordIds(levelDef, nextState).toSet(),
    )
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
      val logEntry =
        OperationLogEntry(
          id = logId,
          timestampMs = timestampMs,
          boardId = state.boardId,
          levelNumber = state.levelNumber,
          trigger = trigger,
          actionLabelSlots = actionSlots,
          resultPrimarySlots = primary,
          resultSecondarySlots = secondary,
          lamp = nextState.lamp,
        )
      return LevelStepResult(
        newState = nextState,
        logEntry = logEntry,
        sound = sound,
        newlyVisibleWordIds = collectLevelScreenWordIds(levelDef, nextState).toSet(),
      )
    }

    val expectedStep = steps[state.seqStep]
    if (trigger != expectedStep.trigger) {
      // Soft reset to step 0 without failure/game over
      val reset = createInitialState(state.boardId, levelDef)
      val primary = expectedStep.hintPrimary
      val secondary = listOf(listOf("n_machine"), listOf("v_stop"), listOf("adj_danger"))
      val nextState =
        reset.copy(
          lamp = LampState.RED,
          primaryMessage = primary,
          secondaryMessage = secondary,
        )
      val logEntry =
        OperationLogEntry(
          id = logId,
          timestampMs = timestampMs,
          boardId = state.boardId,
          levelNumber = state.levelNumber,
          trigger = trigger,
          actionLabelSlots = actionSlots,
          resultPrimarySlots = primary,
          resultSecondarySlots = secondary,
          lamp = LampState.RED,
        )
      return LevelStepResult(
        newState = nextState,
        logEntry = logEntry,
        sound = SoundEffect.WARN,
        newlyVisibleWordIds = collectLevelScreenWordIds(levelDef, nextState).toSet(),
      )
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
      val logEntry =
        OperationLogEntry(
          id = logId,
          timestampMs = timestampMs,
          boardId = state.boardId,
          levelNumber = state.levelNumber,
          trigger = trigger,
          actionLabelSlots = actionSlots,
          resultPrimarySlots = primary,
          resultSecondarySlots = emptyList(),
          lamp = LampState.AMBER,
        )
      return LevelStepResult(
        newState = nextState,
        logEntry = logEntry,
        sound = SoundEffect.RATCHET,
        newlyVisibleWordIds = collectLevelScreenWordIds(levelDef, nextState).toSet(),
      )
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
      val logEntry =
        OperationLogEntry(
          id = logId,
          timestampMs = timestampMs,
          boardId = state.boardId,
          levelNumber = state.levelNumber,
          trigger = trigger,
          actionLabelSlots = actionSlots,
          resultPrimarySlots = nextState.primaryMessage,
          resultSecondarySlots = nextState.secondaryMessage,
          lamp = LampState.GREEN,
        )
      return LevelStepResult(
        newState = nextState,
        logEntry = logEntry,
        sound = SoundEffect.PHASE_CLEAR,
        newlyVisibleWordIds = collectLevelScreenWordIds(levelDef, nextState).toSet(),
      )
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
    val logEntry =
      OperationLogEntry(
        id = logId,
        timestampMs = timestampMs,
        boardId = state.boardId,
        levelNumber = state.levelNumber,
        trigger = trigger,
        actionLabelSlots = actionSlots,
        resultPrimarySlots = actionSlots,
        resultSecondarySlots = stepCompleteSecondary,
        lamp = LampState.AMBER,
      )
    return LevelStepResult(
      newState = nextState,
      logEntry = logEntry,
      sound = SoundEffect.CHIME,
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

  fun triggerToSlots(trigger: String): List<List<String>> =
    when (trigger) {
      "button_push" -> listOf(listOf("n_button"), listOf("v_push"))
      "lever_pull" -> listOf(listOf("n_lever"), listOf("v_pull"))
      "lever_push" -> listOf(listOf("n_lever"), listOf("v_push"))
      "key_insert" -> listOf(listOf("n_key"), listOf("v_insert"))
      "key_remove" -> listOf(listOf("n_key"), listOf("v_remove"))
      "conduit_connect" -> listOf(listOf("n_conduit"), listOf("v_connect"))
      "conduit_cut" -> listOf(listOf("n_conduit"), listOf("v_cut"))
      "lock_lock" -> listOf(listOf("n_lock"), listOf("v_lock"))
      "lock_unlock" -> listOf(listOf("n_lock"), listOf("v_unlock"))
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

    levelDef.unlockedControls.forEach { control ->
      when (control) {
        "button" -> {
          ids.add("n_button")
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
