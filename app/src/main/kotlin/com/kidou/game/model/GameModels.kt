package com.kidou.game.model

import com.kidou.game.ui.theme.ColorTheme
import com.kidou.game.ui.theme.ThemeMode

data class GlyphGrid(
  val rows: List<String>,
) {
  val size: Int = 12

  fun isPixelOn(row: Int, col: Int): Boolean {
    val line = rows.getOrNull(row) ?: return false
    return col in line.indices && line[col] == '#'
  }
}

enum class AppLanguage(val code: String) {
  JA("ja"),
  EN("en"),
}

enum class EntrySource {
  PLAYER,
  DEFAULT_SET,
  AD_REVEALED,
  DEBUG_BULK,
}

data class WordDef(
  val id: String,
  val glyphId: String,
  val meaningJa: String,
  val meaningEn: String,
  val pos: String,
  val morphemes: List<String> = emptyList(),
  val isDefaultSet: Boolean = false,
) {
  fun meaning(language: AppLanguage): String =
    when (language) {
      AppLanguage.JA -> meaningJa
      AppLanguage.EN -> meaningEn
    }
}

data class BoardCollectionDef(
  val titleSubtitleJa: String = "言語解読 × 試行錯誤",
  val titleSubtitleEn: String = "DECIPHER × EXPERIMENT",
  val guideLines: List<List<List<String>>> = emptyList(),
  val boards: List<BoardDef> = emptyList(),
) {
  fun boardById(id: Int): BoardDef = boards.firstOrNull { it.id == id } ?: boards.first()

  fun titleSubtitle(language: AppLanguage): String =
    when (language) {
      AppLanguage.JA -> titleSubtitleJa
      AppLanguage.EN -> titleSubtitleEn
    }
}

data class BoardDef(
  val id: Int,
  val titleSlots: List<List<String>>,
  val requiredBoardId: Int? = null,
  val requiredClears: Int = 0,
  val totalSlots: Int = 25,
  val storyLines: List<List<List<String>>> = emptyList(),
  val levels: List<LevelDef> = emptyList(),
) {
  fun levelByNumber(levelNumber: Int): LevelDef? =
    levels.firstOrNull { it.levelNumber == levelNumber }
}

enum class PuzzleType {
  CONDITION,
  SEQUENCE,
}

data class LevelDef(
  val levelNumber: Int,
  val puzzleType: PuzzleType = PuzzleType.CONDITION,
  val unlockedControls: List<String> = emptyList(),
  val plates: List<List<List<String>>> = emptyList(),
  val initialState: LevelInitialState = LevelInitialState(),
  val requirements: ConditionRequirement? = null,
  val sequenceSteps: List<SequenceStepDef> = emptyList(),
  val completionPrimary: List<List<String>> = emptyList(),
  val completionSecondary: List<List<String>> = emptyList(),
  val tickerEnabled: Boolean = false,
  val tickerIntervalMs: Long = 1100L,
  val tickerSequence: List<Int> = emptyList(),
  val switchLinks: Map<String, List<String>> = emptyMap(),
  val memoryMode: Boolean = false,
)

data class LevelInitialState(
  val lever: String = "up",
  val key: String = "out",
  val conduit: String = "cut",
  val lock: String = "locked",
  val dial: Int = 0,
  val valve: Int = 0,
  val swA: Boolean = false,
  val swB: Boolean = false,
  val swC: Boolean = false,
  val swD: Boolean = false,
  val route: Int = 1,
  val slider: String = "left",
  val lamp: String = "off",
  val primaryMessage: List<List<String>> = emptyList(),
  val secondaryMessage: List<List<String>> = emptyList(),
)

data class ModuleCheck(
  val module: String = "",
  val expected: String = "",
)

data class LogicClause(
  val operator: String = "AND",
  val checks: List<ModuleCheck> = emptyList(),
  val hintPrimary: List<List<String>> = emptyList(),
  val hintSecondary: List<List<String>> = emptyList(),
)

data class ConditionRequirement(
  val leverEq: String? = null,
  val keyEq: String? = null,
  val conduitEq: String? = null,
  val lockEq: String? = null,
  val dialEq: Int? = null,
  val valveEq: Int? = null,
  val swAEq: Boolean? = null,
  val swBEq: Boolean? = null,
  val swCEq: Boolean? = null,
  val swDEq: Boolean? = null,
  val routeEq: Int? = null,
  val sliderEq: String? = null,
  val dialEqualsValve: Boolean? = null,
  val dialPlusValveEq: Int? = null,
  val requireHold: Boolean? = null,
  val dualPressesEq: Int? = null,
  val buttonPressesEq: Int? = null,
  val orderSequence: List<Int> = emptyList(),
  val tickerTargets: List<Int> = emptyList(),
  val logicClauses: List<LogicClause> = emptyList(),
)

data class SequenceStepDef(
  val trigger: String,
  val repeatCount: Int = 1,
  val hintPrimary: List<List<String>> = emptyList(),
)

enum class WordColorTag(val labelJa: String, val labelEn: String) {
  UNCLASSIFIED("未分類", "None"),
  HYPOTHESIS("仮説", "Guess"),
  CONFIRMED("確信", "Sure"),
  OBJECT("対象", "Object"),
  ACTION("操作", "Action"),
  MODIFIER("状態/数", "Mod/Num");

  fun label(language: AppLanguage): String =
    when (language) {
      AppLanguage.JA -> labelJa
      AppLanguage.EN -> labelEn
    }
}

data class DictionaryEntry(
  val wordId: String,
  val translation: String = "",
  val colorTag: WordColorTag = WordColorTag.UNCLASSIFIED,
  val memo: String = "",
  val source: EntrySource = EntrySource.PLAYER,
) {
  fun resolvedTranslation(wordDef: WordDef?, language: AppLanguage): String {
    return when (source) {
      EntrySource.PLAYER -> translation
      EntrySource.DEFAULT_SET,
      EntrySource.AD_REVEALED,
      EntrySource.DEBUG_BULK -> wordDef?.meaning(language) ?: translation
    }
  }
}

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
  CORE_ACTIVATE,
}

data class OperationLogEntry(
  val id: Long,
  val timestampMs: Long,
  val boardId: Int,
  val levelNumber: Int,
  val trigger: String,
  val actionLabelSlots: List<List<String>>,
  val resultPrimarySlots: List<List<String>>,
  val resultSecondarySlots: List<List<String>>,
  val lamp: LampState,
)

data class LevelRuntimeState(
  val boardId: Int = 1,
  val levelNumber: Int = 1,
  val lever: String = "up",
  val key: String = "out",
  val conduit: String = "cut",
  val lock: String = "locked",
  val dial: Int = 0,
  val valve: Int = 0,
  val swA: Boolean = false,
  val swB: Boolean = false,
  val swC: Boolean = false,
  val swD: Boolean = false,
  val route: Int = 1,
  val slider: String = "left",
  val buttonPresses: Int = 0,
  val dualPresses: Int = 0,
  val orderProgress: Int = 0,
  val tickerIndex: Int = 0,
  val tickerDigit: Int = 0,
  val tickerCaptures: Int = 0,
  val hasOperated: Boolean = false,
  val seqStep: Int = 0,
  val seqSubProgress: Int = 0,
  val isCleared: Boolean = false,
  val lamp: LampState = LampState.OFF,
  val primaryMessage: List<List<String>> = emptyList(),
  val secondaryMessage: List<List<String>> = emptyList(),
)

data class TranslationStats(
  val translatedCount: Int,
  val totalCount: Int,
  val percent: Int,
)

data class SaveDataV2(
  val dictionary: Map<String, DictionaryEntry> = emptyMap(),
  val discoveredWordIds: Set<String> = emptySet(),
  val clearedLevelKeys: Set<String> = emptySet(),
  val operationLog: List<OperationLogEntry> = emptyList(),
  val soundEnabled: Boolean = true,
  val bgmEnabled: Boolean = true,
  val hapticsEnabled: Boolean? = true,
  val language: AppLanguage = AppLanguage.JA,
  val themeMode: ThemeMode = ThemeMode.DARK,
  val colorTheme: ColorTheme = ColorTheme.AMBER_RUIN,
  val selectedBoardId: Int = 1,
)

data class LoadedGameData(
  val glyphs: Map<String, GlyphGrid>,
  val words: Map<String, WordDef>,
  val wordOrder: List<String>,
  val boardCollection: BoardCollectionDef,
) {
  val defaultSetWordIds: Set<String> =
    words.values.filter { it.isDefaultSet }.map { it.id }.toSet()
}
