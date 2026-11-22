package com.kidou.game.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kidou.game.audio.SoundSynth
import com.kidou.game.data.SaveRepository
import com.kidou.game.engine.GameDataLoader
import com.kidou.game.engine.MachineEngine
import com.kidou.game.model.DictionaryEntry
import com.kidou.game.model.LoadedGameData
import com.kidou.game.model.MachineRuntimeState
import com.kidou.game.model.OperationLogEntry
import com.kidou.game.model.SaveData
import com.kidou.game.model.SoundEffect
import com.kidou.game.model.WordColorTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DictionaryTab {
  SCREEN,
  ALL,
}

enum class MenuTab {
  LOG,
  PROGRESS,
  SETTINGS,
}

data class GameUiState(
  val gameData: LoadedGameData? = null,
  val machineState: MachineRuntimeState = MachineRuntimeState(),
  val dictionary: Map<String, DictionaryEntry> = emptyMap(),
  val discoveredWordIds: Set<String> = emptySet(),
  val screenWordIds: List<String> = emptyList(),
  val operationLog: List<OperationLogEntry> = emptyList(),
  val soundEnabled: Boolean = true,
  val editingWordId: String? = null,
  val showDictionary: Boolean = false,
  val dictionaryTab: DictionaryTab = DictionaryTab.SCREEN,
  val showMenu: Boolean = false,
  val menuTab: MenuTab = MenuTab.LOG,
  val showCompletionDialog: Boolean = false,
) {
  val totalWordsCount: Int
    get() = gameData?.wordOrder?.size ?: 0

  val registeredCount: Int
    get() = dictionary.values.count { it.translation.isNotBlank() }

  val decodeRatePercent: Int
    get() {
      val denom = discoveredWordIds.size.coerceAtLeast(1)
      val translatedDiscovered =
        discoveredWordIds.count { id -> dictionary[id]?.translation?.isNotBlank() == true }
      return (translatedDiscovered * 100) / denom
    }
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
  private val saveRepository = SaveRepository(application.applicationContext)
  private val soundSynth = SoundSynth()
  private val gameData: LoadedGameData = GameDataLoader.loadFromAssets(application.applicationContext)

  private val _uiState = MutableStateFlow(loadInitialUiState())
  val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

  private var logSequence = 0L

  private fun loadInitialUiState(): GameUiState {
    val saved = saveRepository.load()
    val machineState = saved?.machineState ?: MachineEngine.createInitialState(gameData.machine)
    val screenWords = MachineEngine.collectScreenWordIds(gameData.machine, machineState)
    val discovered = (saved?.discoveredWordIds ?: emptySet()) + screenWords
    val dictionary = saved?.dictionary ?: emptyMap()
    val logs = saved?.operationLog ?: emptyList()
    logSequence = logs.maxOfOrNull { it.id } ?: 0L

    return GameUiState(
      gameData = gameData,
      machineState = machineState,
      dictionary = dictionary,
      discoveredWordIds = discovered,
      screenWordIds = screenWords,
      operationLog = logs,
      soundEnabled = saved?.soundEnabled ?: true,
    )
  }

  fun triggerControl(trigger: String) {
    val state = _uiState.value
    val now = System.currentTimeMillis()
    logSequence = maxOf(logSequence + 1, now)

    val result =
      MachineEngine.step(
        machineDef = gameData.machine,
        currentState = state.machineState,
        trigger = trigger,
        timestampMs = now,
        logId = logSequence,
      )

    soundSynth.play(result.sound, state.soundEnabled)

    val updatedLogs =
      if (result.logEntry != null) {
        (listOf(result.logEntry) + state.operationLog).take(120)
      } else {
        state.operationLog
      }
    val screenWords = MachineEngine.collectScreenWordIds(gameData.machine, result.newState)
    val updatedDiscovered = state.discoveredWordIds + result.newlyVisibleWordIds + screenWords
    val justActivated = !state.machineState.isFullyActivated && result.newState.isFullyActivated

    _uiState.update { current ->
      current.copy(
        machineState = result.newState,
        discoveredWordIds = updatedDiscovered,
        screenWordIds = screenWords,
        operationLog = updatedLogs,
        showCompletionDialog = current.showCompletionDialog || justActivated,
      )
    }
    persist()
  }

  fun selectPhase(targetPhase: Int) {
    val state = _uiState.value
    if (targetPhase == state.machineState.currentPhase) return
    val nextMachineState =
      MachineEngine.switchToPhase(gameData.machine, state.machineState, targetPhase)
    if (nextMachineState == state.machineState) return

    soundSynth.play(SoundEffect.CLICK, state.soundEnabled)
    val screenWords = MachineEngine.collectScreenWordIds(gameData.machine, nextMachineState)
    val updatedDiscovered = state.discoveredWordIds + screenWords

    _uiState.update { current ->
      current.copy(
        machineState = nextMachineState,
        screenWordIds = screenWords,
        discoveredWordIds = updatedDiscovered,
      )
    }
    persist()
  }

  fun openWordEditor(wordId: String) {
    _uiState.update { it.copy(editingWordId = wordId) }
  }

  fun closeWordEditor() {
    _uiState.update { it.copy(editingWordId = null) }
  }

  fun saveWordEntry(
    wordId: String,
    translation: String,
    colorTag: WordColorTag,
    memo: String,
  ) {
    val trimmedTrans = translation.trim()
    val trimmedMemo = memo.trim()
    _uiState.update { current ->
      val updatedMap = current.dictionary.toMutableMap()
      if (trimmedTrans.isEmpty() && trimmedMemo.isEmpty() && colorTag == WordColorTag.UNCLASSIFIED) {
        updatedMap.remove(wordId)
      } else {
        updatedMap[wordId] =
          DictionaryEntry(
            wordId = wordId,
            translation = trimmedTrans,
            colorTag = colorTag,
            memo = trimmedMemo,
          )
      }
      current.copy(dictionary = updatedMap, editingWordId = null)
    }
    persist()
  }

  fun openDictionary(tab: DictionaryTab = DictionaryTab.SCREEN) {
    _uiState.update { it.copy(showDictionary = true, dictionaryTab = tab) }
  }

  fun closeDictionary() {
    _uiState.update { it.copy(showDictionary = false) }
  }

  fun setDictionaryTab(tab: DictionaryTab) {
    _uiState.update { it.copy(dictionaryTab = tab) }
  }

  fun openMenu(tab: MenuTab = MenuTab.LOG) {
    _uiState.update { it.copy(showMenu = true, menuTab = tab) }
  }

  fun closeMenu() {
    _uiState.update { it.copy(showMenu = false) }
  }

  fun setMenuTab(tab: MenuTab) {
    _uiState.update { it.copy(menuTab = tab) }
  }

  fun setSoundEnabled(enabled: Boolean) {
    _uiState.update { it.copy(soundEnabled = enabled) }
    persist()
  }

  fun dismissCompletionDialog() {
    _uiState.update { it.copy(showCompletionDialog = false) }
  }

  fun resetAll() {
    saveRepository.clear()
    val initialMachine = MachineEngine.createInitialState(gameData.machine)
    val screenWords = MachineEngine.collectScreenWordIds(gameData.machine, initialMachine)
    _uiState.update { current ->
      current.copy(
        machineState = initialMachine,
        dictionary = emptyMap(),
        discoveredWordIds = screenWords.toSet(),
        screenWordIds = screenWords,
        operationLog = emptyList(),
        editingWordId = null,
        showDictionary = false,
        showMenu = false,
        showCompletionDialog = false,
      )
    }
  }

  fun findWordContexts(wordId: String): List<List<List<String>>> {
    val state = _uiState.value
    val result = LinkedHashSet<List<List<String>>>()
    val phase = gameData.machine.phaseByNumber(state.machineState.currentPhase)
    phase.plates.forEach { plate ->
      if (plate.any { wordId in it }) result.add(plate)
    }
    if (state.machineState.primaryMessage.any { wordId in it }) {
      result.add(state.machineState.primaryMessage)
    }
    if (state.machineState.secondaryMessage.any { wordId in it }) {
      result.add(state.machineState.secondaryMessage)
    }
    state.operationLog.forEach { entry ->
      if (entry.actionLabelSlots.any { wordId in it }) result.add(entry.actionLabelSlots)
      if (entry.resultPrimarySlots.any { wordId in it }) result.add(entry.resultPrimarySlots)
      if (entry.resultSecondarySlots.any { wordId in it }) result.add(entry.resultSecondarySlots)
    }
    return result.take(8)
  }

  private fun persist() {
    val state = _uiState.value
    saveRepository.save(
      SaveData(
        dictionary = state.dictionary,
        discoveredWordIds = state.discoveredWordIds,
        machineState = state.machineState,
        operationLog = state.operationLog,
        soundEnabled = state.soundEnabled,
      )
    )
  }
}
