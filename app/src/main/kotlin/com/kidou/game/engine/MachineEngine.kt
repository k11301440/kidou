package com.kidou.game.engine

import com.kidou.game.model.ConditionSpec
import com.kidou.game.model.LampState
import com.kidou.game.model.MachineDef
import com.kidou.game.model.MachineRuntimeState
import com.kidou.game.model.OperationLogEntry
import com.kidou.game.model.SoundEffect

data class EngineStepResult(
  val newState: MachineRuntimeState,
  val logEntry: OperationLogEntry?,
  val sound: SoundEffect,
  val newlyVisibleWordIds: Set<String>,
)

object MachineEngine {

  fun createInitialState(machineDef: MachineDef): MachineRuntimeState {
    val phase1 = machineDef.phaseByNumber(1)
    val init = phase1.initialState
    return MachineRuntimeState(
      currentPhase = 1,
      maxUnlockedPhase = 1,
      isFullyActivated = false,
      lever = init.lever,
      dial = init.dial,
      key = init.key,
      conduit = init.conduit,
      buttonPresses = init.buttonPresses,
      dialTurns = init.dialTurns,
      seqStep = init.seqStep,
      lamp = LampState.fromWire(init.lamp),
      primaryMessage = init.primaryMessage,
      secondaryMessage = init.secondaryMessage,
    )
  }

  fun switchToPhase(
    machineDef: MachineDef,
    currentState: MachineRuntimeState,
    targetPhase: Int,
  ): MachineRuntimeState {
    if (targetPhase < 1 || targetPhase > currentState.maxUnlockedPhase) {
      return currentState
    }
    val targetDef = machineDef.phaseByNumber(targetPhase)
    val init = targetDef.initialState
    return currentState.copy(
      currentPhase = targetPhase,
      lever = init.lever,
      dial = init.dial,
      key = init.key,
      conduit = init.conduit,
      buttonPresses = init.buttonPresses,
      dialTurns = init.dialTurns,
      seqStep = init.seqStep,
      lamp = LampState.fromWire(init.lamp),
      primaryMessage = init.primaryMessage,
      secondaryMessage = init.secondaryMessage,
    )
  }

  fun step(
    machineDef: MachineDef,
    currentState: MachineRuntimeState,
    trigger: String,
    timestampMs: Long = System.currentTimeMillis(),
    logId: Long = timestampMs,
  ): EngineStepResult {
    val phaseDef = machineDef.phaseByNumber(currentState.currentPhase)
    val matchedRule =
      phaseDef.rules.firstOrNull { rule ->
        (rule.trigger == trigger || rule.trigger == "*") &&
          rule.conditions.matches(currentState)
      }
        ?: return EngineStepResult(
          newState = currentState,
          logEntry = null,
          sound = SoundEffect.CLICK,
          newlyVisibleWordIds = emptySet(),
        )

    val effects = matchedRule.effects

    var nextLever = effects.setLever ?: currentState.lever
    var nextKey = effects.setKey ?: currentState.key
    var nextConduit = effects.setConduit ?: currentState.conduit
    var nextButtonPresses = effects.setButtonPresses ?: currentState.buttonPresses
    var nextDial = currentState.dial
    if (effects.incrementDialWrap != null && effects.incrementDialWrap > 0) {
      nextDial = (nextDial % effects.incrementDialWrap) + 1
    }
    var nextDialTurns = effects.setDialTurns ?: currentState.dialTurns
    if (effects.incrementDialTurns != null) {
      nextDialTurns += effects.incrementDialTurns
    }
    var nextSeqStep = effects.setSeqStep ?: currentState.seqStep

    if (effects.resetPhase4 == true) {
      nextLever = "up"
      nextDial = 1
      nextKey = "out"
      nextSeqStep = 0
      nextDialTurns = 0
      nextButtonPresses = 0
    }

    val expandedPrimary = expandTokens(effects.primaryMessage, nextDial, nextDialTurns)
    val expandedSecondary = expandTokens(effects.secondaryMessage, nextDial, nextDialTurns)
    val nextLamp = effects.lamp?.let { LampState.fromWire(it) } ?: currentState.lamp
    val sound = SoundEffect.fromWire(effects.sound)

    val logEntry =
      OperationLogEntry(
        id = logId,
        timestampMs = timestampMs,
        phase = currentState.currentPhase,
        trigger = trigger,
        actionLabelSlots = triggerToSlots(trigger),
        resultPrimarySlots = expandedPrimary,
        resultSecondarySlots = expandedSecondary,
        lamp = nextLamp,
      )

    var nextPhase = currentState.currentPhase
    var nextMaxUnlocked = currentState.maxUnlockedPhase
    if (effects.advanceToPhase != null && effects.advanceToPhase > currentState.currentPhase) {
      nextPhase = effects.advanceToPhase
      nextMaxUnlocked = maxOf(nextMaxUnlocked, nextPhase)
      val nextPhaseInit = machineDef.phaseByNumber(nextPhase).initialState
      nextLever = nextPhaseInit.lever
      nextDial = nextPhaseInit.dial
      nextKey = nextPhaseInit.key
      nextConduit = nextPhaseInit.conduit
      nextButtonPresses = nextPhaseInit.buttonPresses
      nextDialTurns = nextPhaseInit.dialTurns
      nextSeqStep = nextPhaseInit.seqStep
    }

    val nextActivated = currentState.isFullyActivated || (effects.completeGame == true)

    val newState =
      currentState.copy(
        currentPhase = nextPhase,
        maxUnlockedPhase = nextMaxUnlocked,
        isFullyActivated = nextActivated,
        lever = nextLever,
        dial = nextDial,
        key = nextKey,
        conduit = nextConduit,
        buttonPresses = nextButtonPresses,
        dialTurns = nextDialTurns,
        seqStep = nextSeqStep,
        lamp = nextLamp,
        primaryMessage = expandedPrimary,
        secondaryMessage = expandedSecondary,
      )

    val visibleWords = collectScreenWordIds(machineDef, newState).toMutableSet()
    expandedPrimary.flatten().forEach { visibleWords.add(it) }
    expandedSecondary.flatten().forEach { visibleWords.add(it) }
    triggerToSlots(trigger).flatten().forEach { visibleWords.add(it) }

    return EngineStepResult(
      newState = newState,
      logEntry = logEntry,
      sound = sound,
      newlyVisibleWordIds = visibleWords,
    )
  }

  fun collectScreenWordIds(
    machineDef: MachineDef,
    state: MachineRuntimeState,
  ): List<String> {
    val ids = LinkedHashSet<String>()

    fun addSlots(slots: List<List<String>>) {
      slots.forEach { slot -> slot.forEach { ids.add(it) } }
    }

    addSlots(machineDef.machineNameSlots)

    // Phase strip exposes n_phase, ordinals 1..maxUnlockedPhase, and m_ord
    ids.add("n_phase")
    for (p in 1..state.maxUnlockedPhase) {
      ids.add("num_$p")
    }
    ids.add("m_ord")

    addSlots(state.primaryMessage)
    addSlots(state.secondaryMessage)

    val phaseDef = machineDef.phaseByNumber(state.currentPhase)
    phaseDef.plates.forEach { plate -> addSlots(plate) }

    phaseDef.unlockedControls.forEach { control ->
      when (control) {
        "button" -> {
          ids.add("n_button")
          ids.add("v_push")
        }
        "lever" -> {
          ids.add("n_lever")
          ids.add(if (state.lever == "up") "v_up" else "v_down")
        }
        "dial" -> {
          ids.add("n_dial")
          ids.add("num_${state.dial.coerceIn(1, 5)}")
        }
        "key" -> {
          ids.add("n_key")
          ids.add(if (state.key == "in") "v_insert" else "v_remove")
        }
        "conduit" -> {
          ids.add("n_conduit")
          ids.add(if (state.conduit == "connected") "v_connect" else "v_cut")
        }
      }
    }

    return ids.toList()
  }

  fun triggerToSlots(trigger: String): List<List<String>> =
    when (trigger) {
      "button_push" -> listOf(listOf("n_button"), listOf("v_push"))
      "lever_pull" -> listOf(listOf("n_lever"), listOf("v_pull"))
      "lever_push" -> listOf(listOf("n_lever"), listOf("v_push"))
      "dial_turn" -> listOf(listOf("n_dial"), listOf("v_turn"))
      "key_insert" -> listOf(listOf("n_key"), listOf("v_insert"))
      "key_remove" -> listOf(listOf("n_key"), listOf("v_remove"))
      "conduit_connect" -> listOf(listOf("n_conduit"), listOf("v_connect"))
      "conduit_cut" -> listOf(listOf("n_conduit"), listOf("v_cut"))
      else -> emptyList()
    }

  private fun expandTokens(
    slots: List<List<String>>,
    dial: Int,
    dialTurns: Int,
  ): List<List<String>> {
    return slots.map { slot ->
      slot.map { token ->
        when (token) {
          "\$dial_num" -> "num_${dial.coerceIn(1, 5)}"
          "\$turn_num" -> "num_${dialTurns.coerceIn(1, 5)}"
          else -> token
        }
      }
    }
  }

  private fun ConditionSpec.matches(state: MachineRuntimeState): Boolean {
    if (buttonPressesEq != null && state.buttonPresses != buttonPressesEq) return false
    if (leverEq != null && state.lever != leverEq) return false
    if (dialEq != null && state.dial != dialEq) return false
    if (dialLt != null && state.dial >= dialLt) return false
    if (dialGt != null && state.dial <= dialGt) return false
    if (keyEq != null && state.key != keyEq) return false
    if (conduitEq != null && state.conduit != conduitEq) return false
    if (seqStepEq != null && state.seqStep != seqStepEq) return false
    if (dialTurnsEq != null && state.dialTurns != dialTurnsEq) return false
    if (dialTurnsLt != null && state.dialTurns >= dialTurnsLt) return false
    return true
  }
}
