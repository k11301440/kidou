package com.kidou.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.kidou.game.ui.BoardScreen
import com.kidou.game.ui.DictionarySheet
import com.kidou.game.ui.GuideSheet
import com.kidou.game.ui.LevelScreen
import com.kidou.game.ui.MenuSheet
import com.kidou.game.ui.RtaScreen
import com.kidou.game.ui.StorySheet
import com.kidou.game.ui.TitleScreen
import com.kidou.game.ui.WordEditorSheet
import com.kidou.game.ui.theme.KIDOUTheme
import com.kidou.game.ui.theme.KidouTheme
import com.kidou.game.ui.theme.ThemeMode
import com.kidou.game.viewmodel.DictionaryTab
import com.kidou.game.viewmodel.GameViewModel
import com.kidou.game.viewmodel.ScreenRoute

class MainActivity : ComponentActivity() {
  private val gameViewModel: GameViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0B0D0E")),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0B0D0E")),
    )

    setContent {
      val uiState by gameViewModel.uiState.collectAsState()
      KIDOUTheme(
        themeMode = uiState.themeMode,
        colorTheme = uiState.colorTheme,
        hapticsEnabled = uiState.hapticsEnabled,
      ) {
        val bgArgb = KidouTheme.palette.background.toArgb()
        LaunchedEffect(uiState.themeMode, bgArgb) {
          val barStyle =
            if (uiState.themeMode == ThemeMode.DARK) SystemBarStyle.dark(bgArgb)
            else SystemBarStyle.light(bgArgb, bgArgb)
          enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        }
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = KidouTheme.palette.background,
        ) {
          KidouAppRoot(activity = this, viewModel = gameViewModel)
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    gameViewModel.onForeground(this)
  }

  override fun onPause() {
    super.onPause()
    gameViewModel.onBackground()
  }
}

@Composable
private fun KidouAppRoot(
  activity: ComponentActivity,
  viewModel: GameViewModel,
) {
  val uiState by viewModel.uiState.collectAsState()
  val data = uiState.gameData ?: return
  val achievementsLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

  BackHandler(enabled = uiState.route == ScreenRoute.LEVEL) {
    viewModel.backToBoard()
  }
  BackHandler(enabled = uiState.route == ScreenRoute.RTA) {
    viewModel.exitRtaToTitle()
  }

  when (uiState.route) {
    ScreenRoute.TITLE -> {
      TitleScreen(
        glyphs = data.glyphs,
        wordOrder = data.wordOrder,
        subtitle = data.boardCollection.titleSubtitle(uiState.language),
        language = uiState.language,
        bgmEnabled = uiState.bgmEnabled,
        showRtaButton = uiState.isFinalLevelCleared,
        rtaBestTimeMs = uiState.rtaBestTimeMs,
        playGamesLinked = uiState.playGamesOptIn && uiState.playGamesStatus.isAuthenticated,
        playGamesToast = uiState.playGamesToast,
        onStart = { viewModel.enterGameFromTitle() },
        onStartRta = { viewModel.startRtaRun() },
        onPlayGamesClick = {
          viewModel.onPlayGamesEntryAction(activity) { intent ->
            runCatching { achievementsLauncher.launch(intent) }
          }
        },
        onClearPlayGamesToast = { viewModel.clearPlayGamesToast() },
        onToggleLanguage = { viewModel.setLanguage(it) },
        onToggleBgm = { viewModel.setBgmEnabled(it) },
      )
    }

    ScreenRoute.BOARD -> {
      BoardScreen(
        uiState = uiState,
        onSelectBoard = { viewModel.selectBoard(it) },
        onOpenLevel = { bId, lNum -> viewModel.openLevel(bId, lNum) },
        onWordClick = { viewModel.openWordEditor(it) },
        onOpenDictionary = { viewModel.openDictionary(DictionaryTab.SCREEN) },
        onOpenStory = { viewModel.openStory() },
        onOpenGuide = { viewModel.openGuide() },
        onOpenMenu = { viewModel.openMenu() },
      )
    }

    ScreenRoute.LEVEL -> {
      LevelScreen(
        uiState = uiState,
        onBackToBoard = { viewModel.backToBoard() },
        onOpenNextLevel = { bId, lNum -> viewModel.openLevel(bId, lNum) },
        onTrigger = { viewModel.triggerControl(it, activity) },
        onTickTicker = { viewModel.tickActiveLevelTicker() },
        onWordClick = { viewModel.openWordEditor(it) },
        onOpenDictionary = { viewModel.openDictionary(DictionaryTab.SCREEN) },
        onOpenMenu = { viewModel.openMenu() },
      )
    }

    ScreenRoute.RTA -> {
      RtaScreen(
        uiState = uiState,
        onTickTimer = { viewModel.tickRtaTimer() },
        onTickTicker = { viewModel.tickActiveRtaTicker() },
        onTrigger = { viewModel.triggerRtaControl(it, activity) },
        onRetry = { viewModel.startRtaRun() },
        onExitToTitle = { viewModel.exitRtaToTitle() },
        onWordClick = { viewModel.openWordEditor(it) },
        onOpenDictionary = { viewModel.openDictionary(DictionaryTab.SCREEN) },
        onOpenMenu = { viewModel.openMenu() },
      )
    }
  }

  if (uiState.showStory) {
    StorySheet(
      uiState = uiState,
      onEditWord = { viewModel.openWordEditor(it) },
      onDismiss = { viewModel.closeStory() },
    )
  }

  if (uiState.showGuide) {
    GuideSheet(
      uiState = uiState,
      onEditWord = { viewModel.openWordEditor(it) },
      onDismiss = { viewModel.closeGuide() },
    )
  }

  if (uiState.showDictionary) {
    DictionarySheet(
      uiState = uiState,
      onSelectTab = { viewModel.setDictionaryTab(it) },
      onEditWord = { viewModel.openWordEditor(it) },
      onDismiss = { viewModel.closeDictionary() },
    )
  }

  if (uiState.showMenu) {
    MenuSheet(
      uiState = uiState,
      onSelectTab = { viewModel.setMenuTab(it) },
      onToggleBgm = { viewModel.setBgmEnabled(it) },
      onToggleSound = { viewModel.setSoundEnabled(it) },
      onToggleHaptics = { viewModel.setHapticsEnabled(it) },
      onSelectLanguage = { viewModel.setLanguage(it) },
      onSelectThemeMode = { viewModel.setThemeMode(it) },
      onSelectColorTheme = { viewModel.setColorTheme(it) },
      onPlayGamesConnectOrAchievements = {
        viewModel.onPlayGamesEntryAction(activity) { intent ->
          runCatching { achievementsLauncher.launch(intent) }
        }
      },
      onRestoreCloudSave = { viewModel.restoreFromCloud(activity) },
      onDisconnectPlayGames = { viewModel.disconnectPlayGames() },
      onDebugBulkApply = { viewModel.debugBulkApplyTranslations() },
      onDebugResetTranslations = { viewModel.debugResetTranslations() },
      onDebugFill19ClearsBoard1 = { viewModel.debugFill19ClearsForBoard1() },
      onDebugFill19ClearsBoard2 = { viewModel.debugFill19ClearsForBoard2() },
      onDebugFill19ClearsBoard3 = { viewModel.debugFill19ClearsForBoard3() },
      onDebugFill19ClearsBoard4 = { viewModel.debugFill19ClearsForBoard4() },
      onDebugFill24ClearsBoard5 = { viewModel.debugFill24ClearsForBoard5() },
      onResetAll = { viewModel.resetAll() },
      onDismiss = { viewModel.closeMenu() },
    )
  }

  val editingId = uiState.editingWordId
  if (editingId != null) {
    WordEditorSheet(
      wordId = editingId,
      glyphs = data.glyphs,
      words = data.words,
      dictionary = uiState.dictionary,
      language = uiState.language,
      contexts = viewModel.findWordContexts(editingId),
      adLoadState = uiState.adLoadState,
      adErrorToast = uiState.adErrorToast,
      onWatchAdReveal = { viewModel.watchAdAndRevealWord(activity, editingId) },
      onSave = { trans, tag, memo -> viewModel.saveWordEntry(editingId, trans, tag, memo) },
      onSwitchWord = { viewModel.openWordEditor(it) },
      onDismiss = { viewModel.closeWordEditor() },
    )
  }
}
