package com.kidou.game

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.kidou.game.ui.BoardScreen
import com.kidou.game.ui.DictionarySheet
import com.kidou.game.ui.GuideSheet
import com.kidou.game.ui.LevelScreen
import com.kidou.game.ui.MenuSheet
import com.kidou.game.ui.StorySheet
import com.kidou.game.ui.TitleScreen
import com.kidou.game.ui.WordEditorSheet
import com.kidou.game.ui.theme.KIDOUTheme
import com.kidou.game.ui.theme.KidouColors
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
      KIDOUTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = KidouColors.Background,
        ) {
          KidouAppRoot(activity = this, viewModel = gameViewModel)
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    gameViewModel.onForeground()
  }

  override fun onPause() {
    super.onPause()
    gameViewModel.onBackground()
  }
}

@Composable
private fun KidouAppRoot(
  activity: Activity,
  viewModel: GameViewModel,
) {
  val uiState by viewModel.uiState.collectAsState()
  val data = uiState.gameData ?: return

  BackHandler(enabled = uiState.route == ScreenRoute.LEVEL) {
    viewModel.backToBoard()
  }

  when (uiState.route) {
    ScreenRoute.TITLE -> {
      TitleScreen(
        glyphs = data.glyphs,
        wordOrder = data.wordOrder,
        language = uiState.language,
        bgmEnabled = uiState.bgmEnabled,
        onStart = { viewModel.enterGameFromTitle() },
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
        onTrigger = { viewModel.triggerControl(it) },
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
      onEditWord = { viewModel.openWordEditor(it) },
      onToggleBgm = { viewModel.setBgmEnabled(it) },
      onToggleSound = { viewModel.setSoundEnabled(it) },
      onSelectLanguage = { viewModel.setLanguage(it) },
      onDebugBulkApply = { viewModel.debugBulkApplyTranslations() },
      onDebugResetTranslations = { viewModel.debugResetTranslations() },
      onDebugFill19Clears = { viewModel.debugFill19ClearsForBoard1() },
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
