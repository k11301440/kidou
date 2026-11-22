package com.kidou.game.model

data class GlyphGrid(
  val rows: List<String>,
) {
  val size: Int = 12

  fun isPixelOn(row: Int, col: Int): Boolean {
    val line = rows.getOrNull(row) ?: return false
    return col in line.indices && line[col] == '#'
  }
}

data class WordDef(
  val id: String,
  val glyphId: String,
  val internalMeaning: String,
  val pos: String,
  val morphemes: List<String> = emptyList(),
)

data class MachineDef(
  val machineNameSlots: List<List<String>>,
  val phases: List<PhaseDef>,
) {
  fun phaseByNumber(phaseNumber: Int): PhaseDef =
    phases.firstOrNull { it.phase == phaseNumber } ?: phases.first()
}

data class PhaseDef(
  val phase: Int,
  val unlockedControls: List<String>,
  val plates: List<List<List<String>>>,
  val initialState: PhaseInitialState,
  val totalSeqSteps: Int = 0,
  val rules: List<TransitionRule>,
)

data class PhaseInitialState(
  val lever: String = "up",
  val dial: Int = 1,
  val key: String = "out",
  val conduit: String = "cut",
  val buttonPresses: Int = 0,
  val dialTurns: Int = 0,
  val seqStep: Int = 0,
  val lamp: String = "off",
  val primaryMessage: List<List<String>> = emptyList(),
  val secondaryMessage: List<List<String>> = emptyList(),
)

data class TransitionRule(
  val trigger: String,
  val conditions: ConditionSpec = ConditionSpec(),
  val effects: EffectSpec,
)

data class ConditionSpec(
  val buttonPressesEq: Int? = null,
  val leverEq: String? = null,
  val dialEq: Int? = null,
  val dialLt: Int? = null,
  val dialGt: Int? = null,
  val keyEq: String? = null,
  val conduitEq: String? = null,
  val seqStepEq: Int? = null,
  val dialTurnsEq: Int? = null,
  val dialTurnsLt: Int? = null,
)

data class EffectSpec(
  val setLever: String? = null,
  val setKey: String? = null,
  val setConduit: String? = null,
  val setButtonPresses: Int? = null,
  val setDialTurns: Int? = null,
  val incrementDialTurns: Int? = null,
  val incrementDialWrap: Int? = null,
  val setSeqStep: Int? = null,
  val resetPhase4: Boolean? = null,
  val lamp: String? = null,
  val sound: String? = null,
  val advanceToPhase: Int? = null,
  val completeGame: Boolean? = null,
  val primaryMessage: List<List<String>> = emptyList(),
  val secondaryMessage: List<List<String>> = emptyList(),
)

enum class WordColorTag(val label: String, val colorHex: Long) {
  UNCLASSIFIED("未分類", 0xFF8B969E),
  HYPOTHESIS("仮説", 0xFFF2B84B),
  CONFIRMED("確信", 0xFF5EEB8F),
  OBJECT("対象", 0xFF58C4DD),
  ACTION("操作", 0xFFB892FF),
  MODIFIER("状態/数", 0xFFE87E6B),
}

data class DictionaryEntry(
  val wordId: String,
  val translation: String = "",
  val colorTag: WordColorTag = WordColorTag.UNCLASSIFIED,
  val memo: String = "",
)

enum class LampState {
  OFF,
  AMBER,
  GREEN,
  RED;

  companion object {
    fun fromWire(value: String?): LampState =
      when (value?.lowercase()) {
        "amber" -> AMBER
        "green" -> GREEN
        "red" -> RED
        else -> OFF
      }
  }
}

enum class SoundEffect {
  CLICK,
  CLUNK,
  RATCHET,
  CHIME,
  WARN,
  PHASE_CLEAR,
  CORE_ACTIVATE;

  companion object {
    fun fromWire(value: String?): SoundEffect =
      when (value?.lowercase()) {
        "clunk" -> CLUNK
        "ratchet" -> RATCHET
        "chime" -> CHIME
        "warn" -> WARN
        "phase_clear" -> PHASE_CLEAR
        "core_activate" -> CORE_ACTIVATE
        else -> CLICK
      }
  }
}

data class OperationLogEntry(
  val id: Long,
  val timestampMs: Long,
  val phase: Int,
  val trigger: String,
  val actionLabelSlots: List<List<String>>,
  val resultPrimarySlots: List<List<String>>,
  val resultSecondarySlots: List<List<String>>,
  val lamp: LampState,
)

data class MachineRuntimeState(
  val currentPhase: Int = 1,
  val maxUnlockedPhase: Int = 1,
  val isFullyActivated: Boolean = false,
  val lever: String = "up",
  val dial: Int = 1,
  val key: String = "out",
  val conduit: String = "cut",
  val buttonPresses: Int = 0,
  val dialTurns: Int = 0,
  val seqStep: Int = 0,
  val lamp: LampState = LampState.OFF,
  val primaryMessage: List<List<String>> = emptyList(),
  val secondaryMessage: List<List<String>> = emptyList(),
)

data class SaveData(
  val dictionary: Map<String, DictionaryEntry> = emptyMap(),
  val discoveredWordIds: Set<String> = emptySet(),
  val machineState: MachineRuntimeState = MachineRuntimeState(),
  val operationLog: List<OperationLogEntry> = emptyList(),
  val soundEnabled: Boolean = true,
)

data class LoadedGameData(
  val glyphs: Map<String, GlyphGrid>,
  val words: Map<String, WordDef>,
  val wordOrder: List<String>,
  val machine: MachineDef,
)
