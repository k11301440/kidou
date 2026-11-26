package com.kidou.game.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kidou.game.BuildConfig
import com.kidou.game.ads.AdLoadState
import com.kidou.game.ads.RewardedAdManager
import com.kidou.game.audio.BgmPlayer
import com.kidou.game.audio.SoundSynth
import com.kidou.game.data.SaveRepository
import com.kidou.game.engine.Base5
import com.kidou.game.engine.GameDataLoader
import com.kidou.game.engine.LevelEngine
import com.kidou.game.engine.RtaLevelGenerator
import com.kidou.game.model.AppLanguage
import com.kidou.game.model.DictionaryEntry
import com.kidou.game.model.EntrySource
import com.kidou.game.model.LevelDef
import com.kidou.game.model.LevelRuntimeState
import com.kidou.game.model.LoadedGameData
import com.kidou.game.model.OperationLogEntry
import com.kidou.game.model.SaveDataV2
import com.kidou.game.model.SoundEffect
import com.kidou.game.model.TranslationStats
import com.kidou.game.model.WordColorTag
import com.kidou.game.playgames.PlayGamesManager
import com.kidou.game.playgames.PlayGamesStatus
import com.kidou.game.ui.theme.ColorTheme
import com.kidou.game.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScreenRoute {
  TITLE,
  BOARD,
  LEVEL,
  RTA,
}

enum class DictionaryTab {
  SCREEN,
  ALL,
}

enum class MenuTab {
  PROGRESS,
  SETTINGS,
}

data class RtaRunState(
  val levels: List<LevelDef>,
  val currentIndex: Int = 0,
  val activeLevelState: LevelRuntimeState,
  val startElapsedMs: Long = 0L,
  val elapsedMs: Long = 0L,
  val finished: Boolean = false,
  val isNewBest: Boolean = false,
) {
  val currentLevelDef: LevelDef get() = levels[currentIndex]
}

data class GameUiState(
  val gameData: LoadedGameData? = null,
  val route: ScreenRoute = ScreenRoute.TITLE,
  val selectedBoardId: Int = 1,
  val activeLevelState: LevelRuntimeState? = null,
  val rtaRun: RtaRunState? = null,
  val clearedLevelKeys: Set<String> = emptySet(),
  val dictionary: Map<String, DictionaryEntry> = emptyMap(),
  val discoveredWordIds: Set<String> = emptySet(),
  val screenWordIds: List<String> = emptyList(),
  val operationLog: List<OperationLogEntry> = emptyList(),
  val soundEnabled: Boolean = true,
  val bgmEnabled: Boolean = true,
  val hapticsEnabled: Boolean = true,
  val language: AppLanguage = AppLanguage.JA,
  val themeMode: ThemeMode = ThemeMode.DARK,
  val colorTheme: ColorTheme = ColorTheme.AMBER_RUIN,
  val rtaBestTimeMs: Long? = null,
  val rtaClearCount: Int = 0,
  val playGamesOptIn: Boolean = false,
  val unlockedAchievements: Set<String> = emptySet(),
  val playGamesStatus: PlayGamesStatus = PlayGamesStatus(),
  val playGamesToast: String? = null,
  val editingWordId: String? = null,
  val showDictionary: Boolean = false,
  val dictionaryTab: DictionaryTab = DictionaryTab.SCREEN,
  val showMenu: Boolean = false,
  val menuTab: MenuTab = MenuTab.PROGRESS,
  val showStory: Boolean = false,
  val showGuide: Boolean = false,
  val adLoadState: AdLoadState = AdLoadState.IDLE,
  val adErrorToast: Boolean = false,
) {
  val translationStats: TranslationStats
    get() {
      val data = gameData ?: return TranslationStats(0, 0, 0)
      return LevelEngine.computeTranslationStats(data, discoveredWordIds, dictionary, language)
    }

  val board1Clears: Int get() = LevelEngine.countBoardClears(1, clearedLevelKeys)
  val board2Clears: Int get() = LevelEngine.countBoardClears(2, clearedLevelKeys)
  val board3Clears: Int get() = LevelEngine.countBoardClears(3, clearedLevelKeys)
  val board4Clears: Int get() = LevelEngine.countBoardClears(4, clearedLevelKeys)
  val board5Clears: Int get() = LevelEngine.countBoardClears(5, clearedLevelKeys)
  val totalClears: Int
    get() =
      board1Clears + board2Clears + board3Clears + board4Clears + board5Clears

  val isFinalLevelCleared: Boolean
    get() = LevelEngine.isFinalLevelCleared(clearedLevelKeys)

  val isBoard2Unlocked: Boolean
    get() {
      val board2 = gameData?.boardCollection?.boards?.firstOrNull { it.id == 2 } ?: return false
      return LevelEngine.isBoardUnlocked(board2, clearedLevelKeys)
    }

  val isBoard3Unlocked: Boolean
    get() {
      val board3 = gameData?.boardCollection?.boards?.firstOrNull { it.id == 3 } ?: return false
      return LevelEngine.isBoardUnlocked(board3, clearedLevelKeys)
    }

  val isBoard4Unlocked: Boolean
    get() {
      val board4 = gameData?.boardCollection?.boards?.firstOrNull { it.id == 4 } ?: return false
      return LevelEngine.isBoardUnlocked(board4, clearedLevelKeys)
    }

  val isBoard5Unlocked: Boolean
    get() {
      val board5 = gameData?.boardCollection?.boards?.firstOrNull { it.id == 5 } ?: return false
      return LevelEngine.isBoardUnlocked(board5, clearedLevelKeys)
    }
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
  private val saveRepository = SaveRepository(application.applicationContext)
  private val soundSynth = SoundSynth()
  private val bgmPlayer = BgmPlayer(application.applicationContext)
  private val rewardedAdManager = RewardedAdManager()
  private val playGamesManager = PlayGamesManager(application.applicationContext, saveRepository)
  private val gameData: LoadedGameData = GameDataLoader.loadFromAssets(application.applicationContext)

  private val _uiState = MutableStateFlow(loadInitialUiState())
  val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

  private var logSequence = 0L

  init {
    rewardedAdManager.initialize(application.applicationContext)
    viewModelScope.launch {
      rewardedAdManager.adState.collect { adSt ->
        _uiState.update {
          it.copy(
            adLoadState = adSt,
            adErrorToast = if (adSt == AdLoadState.READY) false else it.adErrorToast,
          )
        }
      }
    }
    viewModelScope.launch {
      playGamesManager.status.collect { pgStatus ->
        _uiState.update { it.copy(playGamesStatus = pgStatus) }
      }
    }
  }

  private fun loadInitialUiState(): GameUiState {
    val saved = saveRepository.load()
    val language = saved?.language ?: AppLanguage.JA
    val selectedBoardId = saved?.selectedBoardId ?: 1
    val boardDef = gameData.boardCollection.boardById(selectedBoardId)
    val boardWords = LevelEngine.collectBoardScreenWordIds(boardDef)
    val discovered = (saved?.discoveredWordIds ?: emptySet()) + boardWords
    val dictionary =
      LevelEngine.buildDefaultDictionary(
        data = gameData,
        language = language,
        existing = saved?.dictionary ?: emptyMap(),
      )
    val logs = saved?.operationLog ?: emptyList()
    logSequence = logs.maxOfOrNull { it.id } ?: 0L

    return GameUiState(
      gameData = gameData,
      route = ScreenRoute.TITLE,
      selectedBoardId = selectedBoardId,
      activeLevelState = null,
      clearedLevelKeys = saved?.clearedLevelKeys ?: emptySet(),
      dictionary = dictionary,
      discoveredWordIds = discovered,
      screenWordIds = boardWords,
      operationLog = logs,
      soundEnabled = saved?.soundEnabled ?: true,
      bgmEnabled = saved?.bgmEnabled ?: true,
      hapticsEnabled = saved?.hapticsEnabled ?: true,
      language = language,
      themeMode = saved?.themeMode ?: ThemeMode.DARK,
      colorTheme = saved?.colorTheme ?: ColorTheme.AMBER_RUIN,
      rtaBestTimeMs = saved?.rtaBestTimeMs,
      rtaClearCount = saved?.rtaClearCount ?: 0,
      playGamesOptIn = saved?.playGamesOptIn ?: false,
      unlockedAchievements = saved?.unlockedAchievements ?: emptySet(),
    )
  }

  fun onForeground(activity: Activity? = null) {
    bgmPlayer.onForeground(_uiState.value.bgmEnabled)
    rewardedAdManager.preload(getApplication())
    playGamesManager.checkAuthState(activity) { authed ->
      if (authed && _uiState.value.playGamesOptIn) {
        syncPlayGamesAchievements(activity)
      }
    }
  }

  fun onBackground() {
    bgmPlayer.onBackground()
  }

  override fun onCleared() {
    super.onCleared()
    bgmPlayer.release()
  }

  fun enterGameFromTitle() {
    soundSynth.play(SoundEffect.CHIME, _uiState.value.soundEnabled)
    val state = _uiState.value
    val boardDef = gameData.boardCollection.boardById(state.selectedBoardId)
    val boardWords = LevelEngine.collectBoardScreenWordIds(boardDef)
    _uiState.update {
      it.copy(
        route = ScreenRoute.BOARD,
        screenWordIds = boardWords,
        discoveredWordIds = it.discoveredWordIds + boardWords,
      )
    }
  }

  fun startRtaRun(seed: Long = System.currentTimeMillis()) {
    val state = _uiState.value
    if (!state.isFinalLevelCleared) return
    val generated = RtaLevelGenerator.generateRun(seed = seed, count = 10, validWordIds = gameData.words.keys)
    if (BuildConfig.DEBUG) {
      generated.forEachIndexed { idx, gen ->
        val req = gen.levelDef.requirements
        Log.d(
          "KIDOU_RTA",
          "IDX=$idx|ARCH=${gen.archetype.name}|SEQ=${gen.levelDef.tickerSequence.joinToString(",")}|TARGETS=${req?.tickerTargets?.joinToString(",") ?: ""}|HOLD=${req?.requireHold == true}|TRIGGERS=${gen.solutionTriggers.joinToString(",")}",
        )
      }
    }
    val defs = generated.map { it.levelDef }
    if (defs.isEmpty()) return
    val firstState = LevelEngine.createInitialState(0, defs.first())
    val screenWords = LevelEngine.collectLevelScreenWordIds(defs.first(), firstState)
    val nowMono = SystemClock.elapsedRealtime()
    soundSynth.play(SoundEffect.CHIME, state.soundEnabled)
    _uiState.update {
      it.copy(
        route = ScreenRoute.RTA,
        activeLevelState = null,
        rtaRun =
          RtaRunState(
            levels = defs,
            currentIndex = 0,
            activeLevelState = firstState,
            startElapsedMs = nowMono,
            elapsedMs = 0L,
            finished = false,
            isNewBest = false,
          ),
        screenWordIds = screenWords,
        discoveredWordIds = it.discoveredWordIds + screenWords,
      )
    }
  }

  fun tickRtaTimer() {
    val run = _uiState.value.rtaRun ?: return
    if (run.finished) return
    val elapsed = (SystemClock.elapsedRealtime() - run.startElapsedMs).coerceAtLeast(0L)
    _uiState.update { cur ->
      val activeRun = cur.rtaRun ?: return@update cur
      if (activeRun.finished) cur else cur.copy(rtaRun = activeRun.copy(elapsedMs = elapsed))
    }
  }

  fun tickActiveRtaTicker() {
    val run = _uiState.value.rtaRun ?: return
    if (run.finished) return
    val levelDef = run.currentLevelDef
    if (!levelDef.tickerEnabled || run.activeLevelState.isCleared) return
    val nextLevelState = LevelEngine.advanceTicker(levelDef, run.activeLevelState)
    if (nextLevelState != run.activeLevelState) {
      val digitWord = Base5.toDigitWordIds(nextLevelState.tickerDigit)
      _uiState.update { cur ->
        val activeRun = cur.rtaRun ?: return@update cur
        cur.copy(
          rtaRun = activeRun.copy(activeLevelState = nextLevelState),
          discoveredWordIds = cur.discoveredWordIds + digitWord,
          screenWordIds = (cur.screenWordIds + digitWord).distinct(),
        )
      }
    }
  }

  fun triggerRtaControl(trigger: String, activity: Activity? = null) {
    val state = _uiState.value
    val run = state.rtaRun ?: return
    if (run.finished) return
    val levelDef = run.currentLevelDef
    val nowWall = System.currentTimeMillis()
    logSequence = maxOf(logSequence + 1, nowWall)

    val result =
      LevelEngine.step(
        levelDef = levelDef,
        currentState = run.activeLevelState,
        trigger = trigger,
        timestampMs = nowWall,
        logId = logSequence,
      )
    soundSynth.play(result.sound, state.soundEnabled)

    if (result.newState.isCleared) {
      val nextIdx = run.currentIndex + 1
      if (nextIdx < run.levels.size) {
        val nextDef = run.levels[nextIdx]
        val nextLevelState = LevelEngine.createInitialState(0, nextDef)
        val screenWords = LevelEngine.collectLevelScreenWordIds(nextDef, nextLevelState)
        _uiState.update { cur ->
          val activeRun = cur.rtaRun ?: return@update cur
          cur.copy(
            rtaRun =
              activeRun.copy(
                currentIndex = nextIdx,
                activeLevelState = nextLevelState,
                elapsedMs = (SystemClock.elapsedRealtime() - activeRun.startElapsedMs).coerceAtLeast(0L),
              ),
            screenWordIds = screenWords,
            discoveredWordIds = cur.discoveredWordIds + result.newlyVisibleWordIds + screenWords,
          )
        }
      } else {
        val finalElapsed = (SystemClock.elapsedRealtime() - run.startElapsedMs).coerceAtLeast(1L)
        val prevBest = state.rtaBestTimeMs
        val isNewBest = prevBest == null || finalElapsed < prevBest
        val newBest = if (isNewBest) finalElapsed else prevBest
        _uiState.update { cur ->
          val activeRun = cur.rtaRun ?: return@update cur
          cur.copy(
            rtaBestTimeMs = newBest,
            rtaClearCount = cur.rtaClearCount + 1,
            rtaRun =
              activeRun.copy(
                activeLevelState = result.newState,
                elapsedMs = finalElapsed,
                finished = true,
                isNewBest = isNewBest,
              ),
            discoveredWordIds = cur.discoveredWordIds + result.newlyVisibleWordIds,
          )
        }
        persist(activity)
      }
      return
    }

    val screenWords = LevelEngine.collectLevelScreenWordIds(levelDef, result.newState)
    _uiState.update { cur ->
      val activeRun = cur.rtaRun ?: return@update cur
      cur.copy(
        rtaRun =
          activeRun.copy(
            activeLevelState = result.newState,
            elapsedMs = (SystemClock.elapsedRealtime() - activeRun.startElapsedMs).coerceAtLeast(0L),
          ),
        screenWordIds = screenWords,
        discoveredWordIds = cur.discoveredWordIds + result.newlyVisibleWordIds + screenWords,
      )
    }
  }

  fun exitRtaToTitle() {
    soundSynth.play(SoundEffect.CLICK, _uiState.value.soundEnabled)
    _uiState.update { it.copy(route = ScreenRoute.TITLE, rtaRun = null) }
  }

  fun selectBoard(boardId: Int) {
    val state = _uiState.value
    val boardDef = gameData.boardCollection.boardById(boardId)
    if (!LevelEngine.isBoardUnlocked(boardDef, state.clearedLevelKeys)) return
    soundSynth.play(SoundEffect.CLICK, state.soundEnabled)
    val boardWords = LevelEngine.collectBoardScreenWordIds(boardDef)
    _uiState.update {
      it.copy(
        selectedBoardId = boardId,
        screenWordIds = boardWords,
        discoveredWordIds = it.discoveredWordIds + boardWords,
      )
    }
    persist()
  }

  fun openLevel(boardId: Int, levelNumber: Int) {
    val boardDef = gameData.boardCollection.boardById(boardId)
    if (!LevelEngine.isBoardUnlocked(boardDef, _uiState.value.clearedLevelKeys)) return
    val levelDef = boardDef.levelByNumber(levelNumber) ?: return
    val levelState = LevelEngine.createInitialState(boardId, levelDef)
    val screenWords = LevelEngine.collectLevelScreenWordIds(levelDef, levelState)
    soundSynth.play(SoundEffect.CLICK, _uiState.value.soundEnabled)

    _uiState.update {
      it.copy(
        route = ScreenRoute.LEVEL,
        selectedBoardId = boardId,
        activeLevelState = levelState,
        screenWordIds = screenWords,
        discoveredWordIds = it.discoveredWordIds + screenWords,
      )
    }
    persist()
  }

  fun backToBoard() {
    val state = _uiState.value
    val boardDef = gameData.boardCollection.boardById(state.selectedBoardId)
    val boardWords = LevelEngine.collectBoardScreenWordIds(boardDef)
    soundSynth.play(SoundEffect.CLICK, state.soundEnabled)
    _uiState.update {
      it.copy(
        route = ScreenRoute.BOARD,
        activeLevelState = null,
        screenWordIds = boardWords,
        discoveredWordIds = it.discoveredWordIds + boardWords,
      )
    }
  }

  fun triggerControl(trigger: String, activity: Activity? = null) {
    val state = _uiState.value
    val levelState = state.activeLevelState ?: return
    val boardDef = gameData.boardCollection.boardById(levelState.boardId)
    val levelDef = boardDef.levelByNumber(levelState.levelNumber) ?: return

    val now = System.currentTimeMillis()
    logSequence = maxOf(logSequence + 1, now)

    val result =
      LevelEngine.step(
        levelDef = levelDef,
        currentState = levelState,
        trigger = trigger,
        timestampMs = now,
        logId = logSequence,
      )

    soundSynth.play(result.sound, state.soundEnabled)

    val updatedLogs = (listOf(result.logEntry) + state.operationLog).take(150)
    val screenWords = LevelEngine.collectLevelScreenWordIds(levelDef, result.newState)
    val updatedCleared =
      if (result.newState.isCleared) {
        state.clearedLevelKeys + LevelEngine.levelKey(levelState.boardId, levelState.levelNumber)
      } else {
        state.clearedLevelKeys
      }

    _uiState.update {
      it.copy(
        activeLevelState = result.newState,
        clearedLevelKeys = updatedCleared,
        discoveredWordIds = it.discoveredWordIds + result.newlyVisibleWordIds + screenWords,
        screenWordIds = screenWords,
        operationLog = updatedLogs,
      )
    }
    persist(if (result.newState.isCleared) activity else null)
  }

  fun openWordEditor(wordId: String) {
    rewardedAdManager.preload(getApplication())
    _uiState.update { it.copy(editingWordId = wordId, adErrorToast = false) }
  }

  fun closeWordEditor() {
    _uiState.update { it.copy(editingWordId = null, adErrorToast = false) }
  }

  fun saveWordEntry(
    wordId: String,
    translation: String,
    colorTag: WordColorTag,
    memo: String,
  ) {
    val trimmedTrans = translation.trim()
    val trimmedMemo = memo.trim()
    val state = _uiState.value
    val wordDef = gameData.words[wordId]
    val existing = state.dictionary[wordId]

    _uiState.update { current ->
      val updatedMap = current.dictionary.toMutableMap()
      val isUnchangedNonPlayer =
        existing != null &&
          existing.source != EntrySource.PLAYER &&
          trimmedTrans == existing.resolvedTranslation(wordDef, current.language)

      if (trimmedTrans.isEmpty() && trimmedMemo.isEmpty() && colorTag == WordColorTag.UNCLASSIFIED) {
        if (wordDef?.isDefaultSet == true) {
          updatedMap[wordId] =
            DictionaryEntry(
              wordId = wordId,
              translation = "",
              colorTag = colorTag,
              memo = "",
              source = EntrySource.PLAYER,
            )
        } else {
          updatedMap.remove(wordId)
        }
      } else if (isUnchangedNonPlayer) {
        updatedMap[wordId] = existing.copy(colorTag = colorTag, memo = trimmedMemo)
      } else {
        updatedMap[wordId] =
          DictionaryEntry(
            wordId = wordId,
            translation = trimmedTrans,
            colorTag = colorTag,
            memo = trimmedMemo,
            source = EntrySource.PLAYER,
          )
      }
      current.copy(dictionary = updatedMap, editingWordId = null)
    }
    persist()
  }

  fun watchAdAndRevealWord(activity: Activity?, wordId: String) {
    if (activity == null) {
      _uiState.update { it.copy(adErrorToast = true) }
      return
    }
    if (rewardedAdManager.adState.value != AdLoadState.READY) {
      rewardedAdManager.preload(activity.applicationContext)
      _uiState.update { it.copy(adErrorToast = true) }
      return
    }
    _uiState.update { it.copy(adErrorToast = false) }
    rewardedAdManager.showAd(
      activity = activity,
      onRewardEarned = { revealWordViaAd(wordId, activity) },
      onFailedOrClosed = { rewarded ->
        if (!rewarded) {
          _uiState.update { it.copy(adErrorToast = true) }
        }
      },
    )
  }

  fun revealWordViaAd(wordId: String, activity: Activity? = null) {
    val wordDef = gameData.words[wordId] ?: return
    _uiState.update { current ->
      val updatedMap = current.dictionary.toMutableMap()
      val existing = updatedMap[wordId]
      updatedMap[wordId] =
        DictionaryEntry(
          wordId = wordId,
          translation = wordDef.meaning(current.language),
          colorTag = existing?.colorTag?.takeIf { it != WordColorTag.UNCLASSIFIED }
            ?: LevelEngine.defaultTagForPos(wordDef.pos),
          memo = existing?.memo.orEmpty(),
          source = EntrySource.AD_REVEALED,
        )
      current.copy(
        dictionary = updatedMap,
        discoveredWordIds = current.discoveredWordIds + wordId,
      )
    }
    persist(activity)
  }

  fun onPlayGamesEntryAction(
    activity: Activity?,
    onLaunchAchievements: (Intent) -> Unit,
  ) {
    _uiState.update { it.copy(playGamesOptIn = true, playGamesToast = null) }
    persist()

    if (_uiState.value.playGamesStatus.isAuthenticated) {
      syncPlayGamesAchievements(activity)
      playGamesManager.openAchievementsUi(
        activity = activity,
        onIntentReady = onLaunchAchievements,
        onUnavailable = { _uiState.update { it.copy(playGamesToast = "unavailable") } },
      )
      return
    }

    playGamesManager.requestSignIn(activity) { authed ->
      if (authed) {
        syncPlayGamesAchievements(activity)
        playGamesManager.saveToCloud(activity, toSaveData(_uiState.value))
        playGamesManager.openAchievementsUi(
          activity = activity,
          onIntentReady = onLaunchAchievements,
          onUnavailable = { _uiState.update { it.copy(playGamesToast = "unavailable") } },
        )
      } else {
        _uiState.update { it.copy(playGamesToast = "unavailable") }
      }
    }
  }

  fun restoreFromCloud(activity: Activity?) {
    val current = _uiState.value
    if (!current.playGamesOptIn || !current.playGamesStatus.isAuthenticated) {
      _uiState.update { it.copy(playGamesToast = "unavailable") }
      return
    }
    playGamesManager.restoreFromCloud(
      activity = activity,
      localSave = toSaveData(current),
      onMerged = { merged ->
        val language = merged.language
        val boardDef = gameData.boardCollection.boardById(merged.selectedBoardId)
        val boardWords = LevelEngine.collectBoardScreenWordIds(boardDef)
        val rebuiltDict =
          LevelEngine.buildDefaultDictionary(
            data = gameData,
            language = language,
            existing = merged.dictionary,
          )
        _uiState.update {
          it.copy(
            selectedBoardId = merged.selectedBoardId,
            clearedLevelKeys = merged.clearedLevelKeys,
            dictionary = rebuiltDict,
            discoveredWordIds = merged.discoveredWordIds + boardWords,
            operationLog = merged.operationLog,
            soundEnabled = merged.soundEnabled,
            bgmEnabled = merged.bgmEnabled,
            hapticsEnabled = merged.hapticsEnabled ?: true,
            language = language,
            themeMode = merged.themeMode,
            colorTheme = merged.colorTheme,
            rtaBestTimeMs = merged.rtaBestTimeMs,
            rtaClearCount = merged.rtaClearCount,
            playGamesOptIn = merged.playGamesOptIn,
            unlockedAchievements = merged.unlockedAchievements,
            playGamesToast = "restored",
          )
        }
        persist(activity)
      },
      onUnavailable = {
        val msgKey = playGamesManager.status.value.statusMessageKey ?: "unavailable"
        _uiState.update { it.copy(playGamesToast = msgKey) }
      },
    )
  }

  fun disconnectPlayGames() {
    playGamesManager.disconnect()
    _uiState.update { it.copy(playGamesOptIn = false, playGamesToast = null) }
    persist()
  }

  fun clearPlayGamesToast() {
    _uiState.update { it.copy(playGamesToast = null) }
  }

  private fun syncPlayGamesAchievements(activity: Activity?) {
    val state = _uiState.value
    if (!state.playGamesOptIn) return
    val save = toSaveData(state)
    val updatedUnlocked =
      playGamesManager.syncAchievements(
        activity = activity,
        data = gameData,
        save = save,
        translationPercent = state.translationStats.percent,
      )
    if (updatedUnlocked != state.unlockedAchievements) {
      _uiState.update { it.copy(unlockedAchievements = updatedUnlocked) }
      saveRepository.save(save.copy(unlockedAchievements = updatedUnlocked))
    }
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

  fun openMenu(tab: MenuTab = MenuTab.PROGRESS) {
    _uiState.update { it.copy(showMenu = true, menuTab = tab) }
  }

  fun closeMenu() {
    _uiState.update { it.copy(showMenu = false) }
  }

  fun setMenuTab(tab: MenuTab) {
    _uiState.update { it.copy(menuTab = tab) }
  }

  fun openStory() {
    val boardDef = gameData.boardCollection.boardById(_uiState.value.selectedBoardId)
    val storyWords = boardDef.storyLines.flatten().flatten()
    _uiState.update {
      it.copy(
        showStory = true,
        discoveredWordIds = it.discoveredWordIds + storyWords,
      )
    }
    persist()
  }

  fun closeStory() {
    _uiState.update { it.copy(showStory = false) }
  }

  fun openGuide() {
    val guideWords = gameData.boardCollection.guideLines.flatten().flatten()
    _uiState.update {
      it.copy(
        showGuide = true,
        discoveredWordIds = it.discoveredWordIds + guideWords,
      )
    }
    persist()
  }

  fun closeGuide() {
    _uiState.update { it.copy(showGuide = false) }
  }

  fun tickActiveLevelTicker() {
    val state = _uiState.value
    val levelState = state.activeLevelState ?: return
    val boardDef = gameData.boardCollection.boardById(levelState.boardId)
    val levelDef = boardDef.levelByNumber(levelState.levelNumber) ?: return
    if (!levelDef.tickerEnabled || levelState.isCleared) return
    val nextLevelState = LevelEngine.advanceTicker(levelDef, levelState)
    if (nextLevelState != levelState) {
      val digitWord = Base5.toDigitWordIds(nextLevelState.tickerDigit)
      _uiState.update {
        it.copy(
          activeLevelState = nextLevelState,
          discoveredWordIds = it.discoveredWordIds + digitWord,
          screenWordIds = (it.screenWordIds + digitWord).distinct(),
        )
      }
    }
  }

  fun setSoundEnabled(enabled: Boolean) {
    _uiState.update { it.copy(soundEnabled = enabled) }
    persist()
  }

  fun setBgmEnabled(enabled: Boolean) {
    bgmPlayer.setEnabled(enabled)
    _uiState.update { it.copy(bgmEnabled = enabled) }
    persist()
  }

  fun setHapticsEnabled(enabled: Boolean) {
    _uiState.update { it.copy(hapticsEnabled = enabled) }
    persist()
  }

  fun setLanguage(language: AppLanguage) {
    _uiState.update { it.copy(language = language) }
    persist()
  }

  fun setThemeMode(themeMode: ThemeMode) {
    _uiState.update { it.copy(themeMode = themeMode) }
    persist()
  }

  fun setColorTheme(colorTheme: ColorTheme) {
    _uiState.update { it.copy(colorTheme = colorTheme) }
    persist()
  }

  fun debugBulkApplyTranslations() {
    _uiState.update { current ->
      val updatedMap = current.dictionary.toMutableMap()
      gameData.words.values.forEach { word ->
        val src = if (word.isDefaultSet) EntrySource.DEFAULT_SET else EntrySource.DEBUG_BULK
        val existing = updatedMap[word.id]
        updatedMap[word.id] =
          DictionaryEntry(
            wordId = word.id,
            translation = word.meaning(current.language),
            colorTag = LevelEngine.defaultTagForPos(word.pos),
            memo = existing?.memo.orEmpty(),
            source = src,
          )
      }
      current.copy(
        dictionary = updatedMap,
        discoveredWordIds = current.discoveredWordIds + gameData.wordOrder,
      )
    }
    persist()
  }

  fun debugResetTranslations() {
    _uiState.update { current ->
      val defaultDict = LevelEngine.buildDefaultDictionary(gameData, current.language)
      current.copy(dictionary = defaultDict)
    }
    persist()
  }

  fun debugFill19ClearsForBoard1() {
    val keys = (1..19).map { LevelEngine.levelKey(1, it) }.toSet()
    _uiState.update { current ->
      current.copy(clearedLevelKeys = current.clearedLevelKeys + keys)
    }
    persist()
  }

  fun debugFill19ClearsForBoard2() {
    val b1Keys = (1..25).map { LevelEngine.levelKey(1, it) }
    val b2Keys = (1..19).map { LevelEngine.levelKey(2, it) }
    _uiState.update { current ->
      current.copy(clearedLevelKeys = current.clearedLevelKeys + b1Keys + b2Keys)
    }
    persist()
  }

  fun debugFill19ClearsForBoard3() {
    val b1Keys = (1..25).map { LevelEngine.levelKey(1, it) }
    val b2Keys = (1..25).map { LevelEngine.levelKey(2, it) }
    val b3Keys = (1..19).map { LevelEngine.levelKey(3, it) }
    _uiState.update { current ->
      current.copy(clearedLevelKeys = current.clearedLevelKeys + b1Keys + b2Keys + b3Keys)
    }
    persist()
  }

  fun debugFill19ClearsForBoard4() {
    val b1Keys = (1..25).map { LevelEngine.levelKey(1, it) }
    val b2Keys = (1..25).map { LevelEngine.levelKey(2, it) }
    val b3Keys = (1..25).map { LevelEngine.levelKey(3, it) }
    val b4Keys = (1..19).map { LevelEngine.levelKey(4, it) }
    _uiState.update { current ->
      current.copy(clearedLevelKeys = current.clearedLevelKeys + b1Keys + b2Keys + b3Keys + b4Keys)
    }
    persist()
  }

  fun debugFill24ClearsForBoard5() {
    val b1To4Keys = (1..4).flatMap { b -> (1..25).map { LevelEngine.levelKey(b, it) } }
    val b5Keys = (1..24).map { LevelEngine.levelKey(5, it) }
    _uiState.update { current ->
      current.copy(clearedLevelKeys = current.clearedLevelKeys + b1To4Keys + b5Keys)
    }
    persist()
  }

  fun resetAll() {
    saveRepository.clear()
    val state = _uiState.value
    val board1 = gameData.boardCollection.boardById(1)
    val boardWords = LevelEngine.collectBoardScreenWordIds(board1)
    val defaultDict = LevelEngine.buildDefaultDictionary(gameData, state.language)

    _uiState.update { current ->
      current.copy(
        route = ScreenRoute.BOARD,
        selectedBoardId = 1,
        activeLevelState = null,
        rtaRun = null,
        clearedLevelKeys = emptySet(),
        dictionary = defaultDict,
        discoveredWordIds = boardWords.toSet(),
        screenWordIds = boardWords,
        operationLog = emptyList(),
        rtaBestTimeMs = null,
        rtaClearCount = 0,
        unlockedAchievements = emptySet(),
        editingWordId = null,
        showDictionary = false,
        showMenu = false,
        showStory = false,
        showGuide = false,
      )
    }
    persist()
  }

  fun findWordContexts(wordId: String): List<List<List<String>>> {
    val state = _uiState.value
    val result = LinkedHashSet<List<List<String>>>()

    val rtaState = state.rtaRun
    if (state.route == ScreenRoute.RTA && rtaState != null) {
      rtaState.currentLevelDef.plates.forEach { plate ->
        if (plate.any { wordId in it }) result.add(plate)
      }
      if (rtaState.activeLevelState.primaryMessage.any { wordId in it }) {
        result.add(rtaState.activeLevelState.primaryMessage)
      }
      if (rtaState.activeLevelState.secondaryMessage.any { wordId in it }) {
        result.add(rtaState.activeLevelState.secondaryMessage)
      }
    }

    val levelState = state.activeLevelState
    if (levelState != null) {
      val levelDef =
        gameData.boardCollection.boardById(levelState.boardId).levelByNumber(levelState.levelNumber)
      levelDef?.plates?.forEach { plate ->
        if (plate.any { wordId in it }) result.add(plate)
      }
      if (levelState.primaryMessage.any { wordId in it }) result.add(levelState.primaryMessage)
      if (levelState.secondaryMessage.any { wordId in it }) result.add(levelState.secondaryMessage)
    }

    val boardDef = gameData.boardCollection.boardById(state.selectedBoardId)
    boardDef.storyLines.forEach { line ->
      if (line.any { wordId in it }) result.add(line)
    }
    gameData.boardCollection.guideLines.forEach { line ->
      if (line.any { wordId in it }) result.add(line)
    }

    state.operationLog.forEach { entry ->
      if (entry.actionLabelSlots.any { wordId in it }) result.add(entry.actionLabelSlots)
      if (entry.resultPrimarySlots.any { wordId in it }) result.add(entry.resultPrimarySlots)
      if (entry.resultSecondarySlots.any { wordId in it }) result.add(entry.resultSecondarySlots)
    }

    return result.take(8)
  }

  private fun toSaveData(state: GameUiState): SaveDataV2 =
    SaveDataV2(
      dictionary = state.dictionary,
      discoveredWordIds = state.discoveredWordIds,
      clearedLevelKeys = state.clearedLevelKeys,
      operationLog = state.operationLog,
      soundEnabled = state.soundEnabled,
      bgmEnabled = state.bgmEnabled,
      hapticsEnabled = state.hapticsEnabled,
      language = state.language,
      themeMode = state.themeMode,
      colorTheme = state.colorTheme,
      selectedBoardId = state.selectedBoardId,
      rtaBestTimeMs = state.rtaBestTimeMs,
      rtaClearCount = state.rtaClearCount,
      playGamesOptIn = state.playGamesOptIn,
      unlockedAchievements = state.unlockedAchievements,
      updatedAtMs = System.currentTimeMillis(),
    )

  private fun persist(activity: Activity? = null) {
    val state = _uiState.value
    val data = toSaveData(state)
    saveRepository.save(data)
    if (state.playGamesOptIn && activity != null) {
      syncPlayGamesAchievements(activity)
      playGamesManager.saveToCloud(activity, toSaveData(_uiState.value))
    }
  }
}
